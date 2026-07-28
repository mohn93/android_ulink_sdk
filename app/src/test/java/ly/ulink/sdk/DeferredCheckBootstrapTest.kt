package ly.ulink.sdk

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
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

/**
 * Regression test for the deferred check losing its one shot to a cold-start race.
 *
 * checkDeferredLink() guarded with ensureBootstrapCompleted(), which throws when
 * bootstrap has not finished. The documented pattern when autoCheckDeferredLink
 * is disabled is for the host to call it at startup — exactly when bootstrap is
 * still in flight. The deferred match is a once-per-install check, so losing it
 * to that race loses the install's attribution outright.
 *
 * Mirrors the fix already applied to handleDeepLink: wait for bootstrap instead
 * of failing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DeferredCheckBootstrapTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig
    private lateinit var mockHttpClient: HttpClient
    private val startedJobs = mutableListOf<Job>()

    /** Every postJson URL the SDK requested, in order. */
    private val postedUrls = mutableListOf<String>()

    private val bootstrapResponse = HttpResponse(
        statusCode = 200,
        body = """{"installationId":"test-123","sessionId":"s-1"}""",
        isSuccess = true,
        headers = mapOf("x-installation-token" to "tok"),
    )

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

        // Install Referrer is unavailable under Robolectric; without this the
        // suspendCoroutine in getInstallReferrerClickId never resumes.
        mockkStatic(InstallReferrerClient::class)
        val referrerBuilder = mockk<InstallReferrerClient.Builder>(relaxed = true)
        val referrerClient = mockk<InstallReferrerClient>(relaxed = true)
        every { InstallReferrerClient.newBuilder(any()) } returns referrerBuilder
        every { referrerBuilder.build() } returns referrerClient
        every { referrerClient.startConnection(any()) } answers {
            firstArg<InstallReferrerStateListener>().onInstallReferrerSetupFinished(
                InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED
            )
        }
    }

    @After
    fun tearDown() {
        startedJobs.forEach { it.cancel() }
        idleUntil(timeoutMs = 2_000) { startedJobs.all { it.isCompleted } }
        startedJobs.clear()
        postedUrls.clear()
        unmockkStatic(InstallReferrerClient::class)
        clearAllMocks()
        runCatching {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        }
    }

    /**
     * A host calling checkDeferredLink() while bootstrap is still in flight must
     * have its check run once bootstrap lands, not be rejected outright.
     */
    @Test(timeout = 30_000L)
    fun `a deferred check requested during bootstrap runs once bootstrap finishes`() {
        val bootstrapGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers {
            val url = firstArg<String>()
            postedUrls.add(url)
            if (url.endsWith("/sdk/deferred/match")) {
                HttpResponse(200, """{"data":{"deepLink":null}}""", true)
            } else {
                bootstrapGate.await()
            }
        }

        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        idleUntil { instanceOrNull() != null }
        val ulink = requireNotNull(instanceOrNull())
        assertTrue("bootstrap must still be in flight", initJob.isActive)

        // The host asks for the deferred check while bootstrap is still running.
        ulink.checkDeferredLink()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "the deferred match must not be attempted before bootstrap completes",
            postedUrls.any { it.endsWith("/sdk/deferred/match") },
        )

        bootstrapGate.complete(bootstrapResponse)

        assertTrue(
            "the deferred check must run once bootstrap completes, got $postedUrls",
            idleUntil { postedUrls.any { it.endsWith("/sdk/deferred/match") } },
        )
    }
}
