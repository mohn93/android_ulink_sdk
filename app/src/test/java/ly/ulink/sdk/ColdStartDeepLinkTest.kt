package ly.ulink.sdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Regression tests for deep links lost to the cold-start bootstrap race.
 *
 * Launching the app BY tapping a link is the most common deep-link path, and it
 * is the one that raced: setup() registers the ActivityLifecycleCallbacks before
 * it awaits the bootstrap network round-trip, so onActivityResumed fires — and
 * handleDeepLink calls resolveLink — while bootstrap is still in flight.
 * resolveLink's ensureBootstrapCompleted() threw ULinkInitializationError, and
 * because handleActivityIntent had already stamped EXTRA_ULINK_HANDLED on the
 * intent, nothing ever retried it. The link was gone for the life of the process.
 *
 * Measured on an emulator (2026-07-28): intent processed 0.9s after process
 * start, bootstrap completed 2.2s later, listener never fired.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ColdStartDeepLinkTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig

    /** Same config with debug on — handleDeepLink only logs its catch-all when debug is set. */
    private lateinit var debugConfig: ULinkConfig
    private lateinit var mockHttpClient: HttpClient

    private val link = "https://links.shared.ly/abc"

    /** Resolve (GET /sdk/resolve) URLs the SDK actually requested, in order. */
    private val resolvedUrls = mutableListOf<String>()

    /** Every coroutine a test starts, so tearDown can release the init mutex. */
    private val startedJobs = mutableListOf<Job>()

    private val bootstrapResponse = HttpResponse(
        statusCode = 200,
        body = """{"installationId":"test-123","sessionId":"s-1"}""",
        isSuccess = true,
        headers = mapOf("x-installation-token" to "tok"),
    )

    private fun viewActivity(url: String): Activity {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val activity = mockk<Activity>(relaxed = true)
        every { activity.intent } returns viewIntent
        return activity
    }

    /** The singleton as soon as it exists — before setup() has finished bootstrapping. */
    private fun instanceOrNull(): ULink? = runCatching { ULink.getInstance() }.getOrNull()

    /** handleDeepLink's catch-all logs this; used to detect that it ran at all. */
    private fun loggedHandlingFailure(): Boolean =
        ShadowLog.getLogs().any { it.msg?.contains("Failed to handle deep link") == true }

    /**
     * Pumps the Robolectric main looper until [condition] holds. resolveLink hops
     * to Dispatchers.IO, so the work completes on a real background thread and
     * cannot be advanced by virtual time alone.
     */
    private fun idleUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        config = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = false,
            enableDeepLinkIntegration = true,
            autoCheckDeferredLink = false,
            persistLastLinkData = false,
        )
        debugConfig = config.copy(debug = true)
        mockHttpClient = mockk(relaxed = true)
        ShadowLog.clear()

        coEvery { mockHttpClient.get(any(), any()) } answers {
            resolvedUrls.add(firstArg())
            HttpResponse(
                statusCode = 200,
                body = """{"slug":"abc","type":"dynamic","parameters":{"k":"v"}}""",
                isSuccess = true,
            )
        }
    }

    @After
    fun tearDown() {
        // ULink.initialize holds a companion-level Mutex across setup(). A test
        // that leaves initialize parked on a bootstrap gate would keep that lock
        // held for the rest of the class and every later initialize() would
        // block on it, so cancel every job this test started and let the lock go.
        startedJobs.forEach { it.cancel() }
        idleUntil(timeoutMs = 2_000) { startedJobs.all { it.isCompleted } }
        startedJobs.clear()

        clearAllMocks()
        resolvedUrls.clear()
        runCatching {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        }
    }

    /** Launches on the main dispatcher and registers the job for tearDown cleanup. */
    private fun launchTracked(block: suspend () -> Unit): Job =
        CoroutineScope(Dispatchers.Main).launch { block() }.also { startedJobs.add(it) }

    /**
     * The launch link must survive bootstrap: arriving mid-bootstrap, it waits and
     * resolves once bootstrap lands, instead of being consumed and thrown away.
     */
    @Test(timeout = 30_000L)
    fun `link arriving before bootstrap completes is resolved once bootstrap finishes`() {
        val bootstrapGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers { bootstrapGate.await() }

        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }

        // Let setup() register its callbacks and park on the bootstrap round-trip.
        idleUntil { instanceOrNull() != null }
        val ulink = requireNotNull(instanceOrNull())
        assertTrue("bootstrap must still be in flight for this test to mean anything", initJob.isActive)

        // The cold-start link lands while bootstrap is still pending.
        ulink.onActivityResumed(viewActivity(link))
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(
            "resolve must not be attempted before bootstrap completes",
            resolvedUrls.isEmpty(),
        )

        // Bootstrap lands.
        bootstrapGate.complete(bootstrapResponse)

        assertTrue(
            "the link that arrived during bootstrap must be resolved once bootstrap completes, not dropped",
            idleUntil { resolvedUrls.any { it.contains(Uri.encode(link)) } },
        )
    }

    /**
     * awaitBootstrap() parks until bootstrap reports a terminal state, so every
     * exit path out of setup() must reach one. setup() registers the activity
     * callbacks BEFORE it touches storage, and loadLastLinkData() is not
     * defensive — a corrupt persisted entry (here a String where a Long is
     * expected) throws out of setup() after the callbacks are live. If that
     * left bootstrapCompleted false, every deep link for the life of the
     * process would park forever: a silent hang, strictly worse to diagnose
     * than the fail-fast it replaced.
     */
    @Test(timeout = 30_000L)
    fun `a setup failure still lets deep links reach a terminal outcome`() {
        context.getSharedPreferences("ulink_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_link_data", """{"slug":"abc"}""")
            .putString("last_link_saved_at", "corrupt-not-a-long")
            .commit()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns bootstrapResponse

        val initJob = launchTracked { runCatching { ULink.initialize(context, debugConfig, mockHttpClient) } }
        assertTrue("initialize should finish (by failing)", idleUntil { !initJob.isActive })
        val ulink = requireNotNull(instanceOrNull())

        ShadowLog.clear()
        ulink.handleDeepLink(Uri.parse(link))

        assertTrue(
            "the link must reach a terminal outcome instead of parking forever",
            idleUntil { loggedHandlingFailure() },
        )
    }

    /**
     * Cancellation must propagate. awaitBootstrap() adds a long-lived suspension
     * point inside handleDeepLink's try, and CancellationException is an
     * Exception — so a blanket catch would swallow the SDK's own shutdown and
     * report it as a deep-link failure.
     */
    @Test(timeout = 30_000L)
    fun `disposing while a link awaits bootstrap does not report a handling failure`() {
        val bootstrapGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers { bootstrapGate.await() }

        val initJob = launchTracked { ULink.initialize(context, debugConfig, mockHttpClient) }
        idleUntil { instanceOrNull() != null }
        val ulink = requireNotNull(instanceOrNull())
        assertTrue("bootstrap must still be in flight", initJob.isActive)

        ulink.handleDeepLink(Uri.parse(link))
        shadowOf(Looper.getMainLooper()).idle()

        ShadowLog.clear()
        ulink.dispose()
        idleUntil(timeoutMs = 1_000) { false } // let any cancellation fallout settle

        assertFalse(
            "cancellation must propagate, not be logged as a deep-link handling failure",
            loggedHandlingFailure(),
        )
    }

    /**
     * A link arriving after bootstrap has already completed must still resolve
     * exactly once — the wait must not swallow or duplicate the normal path.
     */
    @Test(timeout = 30_000L)
    fun `link arriving after bootstrap completes is resolved exactly once`() {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns bootstrapResponse

        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue("bootstrap should complete", idleUntil { !initJob.isActive })

        val ulink = requireNotNull(instanceOrNull())
        ulink.onActivityResumed(viewActivity(link))

        assertTrue(
            "link must resolve",
            idleUntil { resolvedUrls.any { it.contains(Uri.encode(link)) } },
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("exactly one resolve for one link", 1, resolvedUrls.size)
    }
}
