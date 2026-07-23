package ly.ulink.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.network.HttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * Regression tests for the automatic-deep-link double-emit.
 *
 * Root causes under test:
 *  1. onActivityCreated AND onActivityResumed both processed the same unconsumed
 *     ACTION_VIEW intent, so one link tap produced two identical emissions; and
 *     because onActivityResumed re-reads activity.intent on every resume, the same
 *     link re-emitted on every foreground / rotation.
 *  2. setup() re-registered ActivityLifecycleCallbacks on every bootstrap retry
 *     without unregistering, so a failed-then-retried init multiplied emissions.
 *
 * handleDeepLink is the single funnel that emits to _dynamicLinkStream, and
 * handleActivityIntent calls it synchronously, so we spy the instance and assert
 * the call count deterministically without touching the network/coroutine timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DeepLinkDedupTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig
    private lateinit var mockHttpClient: HttpClient

    private fun bootstrapSucceeds() {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"tok","sessionId":"s-1"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "tok")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("s-1"))
            }
        }
    }

    private fun viewActivity(url: String): Activity {
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val activity = mockk<Activity>(relaxed = true)
        every { activity.intent } returns viewIntent
        return activity
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
        mockHttpClient = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        try {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    /**
     * A single link tap on an installed app must reach handleDeepLink exactly once,
     * even though both onActivityCreated and onActivityResumed fire for the launch
     * intent.
     */
    @Test
    fun `single link tap is processed once across create and resume`() = runTest {
        bootstrapSucceeds()
        val ulink = spyk(ULink.initialize(context, config, mockHttpClient))
        every { ulink.handleDeepLink(any(), any(), any()) } just Runs

        val activity = viewActivity("https://links.shared.ly/abc")

        ulink.onActivityCreated(activity, null)
        ulink.onActivityResumed(activity)

        verify(exactly = 1) { ulink.handleDeepLink(any(), any(), any()) }
    }

    /**
     * Returning to the foreground (repeated onActivityResumed on the same intent)
     * must NOT re-emit the link. This is the "emits on every resume" storm.
     */
    @Test
    fun `same intent is not reprocessed on repeated resume`() = runTest {
        bootstrapSucceeds()
        val ulink = spyk(ULink.initialize(context, config, mockHttpClient))
        every { ulink.handleDeepLink(any(), any(), any()) } just Runs

        val activity = viewActivity("https://links.shared.ly/abc")

        ulink.onActivityResumed(activity)
        ulink.onActivityResumed(activity)
        ulink.onActivityResumed(activity)

        verify(exactly = 1) { ulink.handleDeepLink(any(), any(), any()) }
    }

    /**
     * A genuinely new link (a fresh intent, e.g. warm-start re-tap) must still be
     * processed. Dedup keys on the intent, not the URL value, so distinct intents
     * each emit once. Guards against over-suppression.
     */
    @Test
    fun `a new intent is processed even after a previous one was handled`() = runTest {
        bootstrapSucceeds()
        val ulink = spyk(ULink.initialize(context, config, mockHttpClient))
        every { ulink.handleDeepLink(any(), any(), any()) } just Runs

        val first = viewActivity("https://links.shared.ly/abc")
        val second = viewActivity("https://links.shared.ly/xyz")

        ulink.onActivityResumed(first)
        ulink.onActivityResumed(second)

        verify(exactly = 1) { ulink.handleDeepLink(match { it.toString().endsWith("/abc") }, any(), any()) }
        verify(exactly = 1) { ulink.handleDeepLink(match { it.toString().endsWith("/xyz") }, any(), any()) }
    }

    /**
     * Non-ACTION_VIEW intents (normal launcher/MAIN launches) must be ignored and
     * left untouched — no processing, and no handled-marker written onto them.
     */
    @Test
    fun `non view intents are ignored`() = runTest {
        bootstrapSucceeds()
        val ulink = spyk(ULink.initialize(context, config, mockHttpClient))
        every { ulink.handleDeepLink(any(), any(), any()) } just Runs

        val mainIntent = Intent(Intent.ACTION_MAIN)
        val activity = mockk<Activity>(relaxed = true)
        every { activity.intent } returns mainIntent

        ulink.onActivityCreated(activity, null)
        ulink.onActivityResumed(activity)

        verify(exactly = 0) { ulink.handleDeepLink(any(), any(), any()) }
    }

    /**
     * If the first bootstrap fails and initialize() is called again (degraded-mode
     * retry), the SDK must NOT register its ActivityLifecycleCallbacks twice —
     * duplicate registration multiplies every deep-link emission.
     */
    @Test
    fun `activity lifecycle callbacks are registered at most once across bootstrap retries`() = runTest {
        val app = spyk(ApplicationProvider.getApplicationContext<Application>())
        every { app.applicationContext } returns app
        coEvery { mockHttpClient.postJson(any(), any(), any()) } throws IOException("no network")

        // First init: bootstrap fails, instance left in degraded (bootstrapSucceeded=false) state.
        ULink.initialize(app, config, mockHttpClient)
        // Second init on the same degraded instance re-runs setup().
        ULink.initialize(app, config, mockHttpClient)

        verify(exactly = 1) { app.registerActivityLifecycleCallbacks(any()) }
    }
}
