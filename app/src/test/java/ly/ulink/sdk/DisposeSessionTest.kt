package ly.ulink.sdk

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
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

/**
 * Regression test for dispose() never ending the session.
 *
 * dispose() did `scope.launch { endSession() }` and then cancelled that very
 * scope on the next line. The launch was still queued, so cancelling the scope
 * killed it before the body ever ran: no end-session request was ever sent and
 * sessions were left dangling server-side for every host that disposes the SDK.
 *
 * Verified by probe before the fix: zero HTTP calls after dispose().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DisposeSessionTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig
    private lateinit var mockHttpClient: HttpClient
    private val startedJobs = mutableListOf<Job>()

    /** Every postJson URL the SDK requested, in order. */
    private val postedUrls = mutableListOf<String>()

    /** Every GET (link resolution) URL the SDK requested, in order. */
    private val resolvedUrls = mutableListOf<String>()

    private fun instanceOrNull(): ULink? = runCatching { ULink.getInstance() }.getOrNull()

    private fun idleUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private fun launchTracked(block: suspend () -> Unit): Job =
        CoroutineScope(Dispatchers.Main).launch { block() }.also { startedJobs.add(it) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        config = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = true,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = false,
            persistLastLinkData = false,
        )
        mockHttpClient = mockk(relaxed = true)
        coEvery { mockHttpClient.postJson(any(), any(), any()) } answers {
            postedUrls.add(firstArg())
            HttpResponse(
                statusCode = 200,
                body = """{"installationId":"test-123","sessionId":"s-1"}""",
                isSuccess = true,
                headers = mapOf("x-installation-token" to "tok"),
            )
        }
        coEvery { mockHttpClient.get(any(), any()) } answers {
            resolvedUrls.add(firstArg())
            HttpResponse(
                statusCode = 200,
                body = """{"slug":"s","type":"dynamic","parameters":{}}""",
                isSuccess = true,
            )
        }
    }

    @After
    fun tearDown() {
        startedJobs.forEach { it.cancel() }
        idleUntil(timeoutMs = 2_000) { startedJobs.all { it.isCompleted } }
        startedJobs.clear()
        postedUrls.clear()
        resolvedUrls.clear()
        clearAllMocks()
        runCatching {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        }
    }

    /**
     * Disposing with a live session must actually send the end-session request.
     * It is best effort — dispose() does not block on the round-trip — but the
     * request has to be dispatched rather than cancelled on the way out.
     */
    @Test(timeout = 30_000L)
    fun `dispose ends the live session`() {
        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue("bootstrap should complete", idleUntil { !initJob.isActive })
        val ulink = requireNotNull(instanceOrNull())

        // Bootstrap establishes the session id that endSession needs.
        postedUrls.clear()

        ulink.dispose()

        assertTrue(
            "dispose() must send the end-session request, got $postedUrls",
            idleUntil { postedUrls.any { it.contains("/sdk/sessions/") && it.endsWith("/end") } },
        )
    }

    /**
     * dispose() cancels the instance's coroutine scope but left INSTANCE set, so
     * initialize()'s fast path handed that same instance back to the next caller
     * — bootstrapSucceeded was still true. Everything the SDK does through its
     * scope (deep links, sessions, deferred checks) then silently no-ops, with
     * no error anywhere: the SDK looks initialized and does nothing.
     */
    @Test(timeout = 30_000L)
    fun `initializing after dispose yields a live instance, not the disposed one`() {
        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue("bootstrap should complete", idleUntil { !initJob.isActive })
        requireNotNull(instanceOrNull()).dispose()
        idleUntil(timeoutMs = 300) { false } // let disposal settle

        val reinitJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue("re-initialize should complete", idleUntil { !reinitJob.isActive })
        val fresh = requireNotNull(instanceOrNull())

        // Behavioural proof the instance is alive: scope-dispatched work must run.
        resolvedUrls.clear()
        fresh.handleDeepLink(android.net.Uri.parse("https://links.shared.ly/after-dispose"))

        assertTrue(
            "a link handled after re-initialize must actually be resolved, got $resolvedUrls",
            idleUntil { resolvedUrls.any { it.contains("after-dispose") } },
        )
    }
}
