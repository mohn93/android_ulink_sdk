package ly.ulink.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import io.mockk.*
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import ly.ulink.sdk.utils.DeviceInfoUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.Collections

/**
 * Regression tests for the unreachable bootstrap retry.
 *
 * setup() marks a FAILED bootstrap as `bootstrapCompleted = true` (only
 * `bootstrapSucceeded` stays false) and logs "will retry on next foreground",
 * but onStart() only retried when `!bootstrapCompleted`. The retry could
 * therefore never fire: a single transient network failure at cold start left
 * the SDK permanently degraded for the life of the process — no sessions, no
 * deferred links — despite the log promising recovery.
 *
 * Reported in the field as repeated `Bootstrap failed: HTTP -1: Unable to
 * resolve host "api.ulink.ly"` with the SDK never recovering (2026-07).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BootstrapRetryTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockHttpClient: HttpClient
    private lateinit var config: ULinkConfig
    private var storedInstallationId: String? = null

    /** URLs passed to postJson, in order. */
    private val requestedUrls = Collections.synchronizedList(mutableListOf<String>())

    /** Flipped to false to simulate the network recovering. */
    private var bootstrapFails = true

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString("installation_id", any()) } answers {
            storedInstallationId = secondArg()
            mockEditor
        }
        every { mockEditor.apply() } just Runs
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }

        config = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com",
            debug = false,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = false,
        )

        mockHttpClient = mockk(relaxed = true)
        coEvery { mockHttpClient.postJson(any(), any(), any()) } answers {
            val url = firstArg<String>()
            requestedUrls.add(url)
            if (url.endsWith("/sdk/bootstrap") && bootstrapFails) {
                // What HttpClient returns for an UnknownHostException.
                HttpResponse(
                    statusCode = -1,
                    body = """Unable to resolve host "api.test.com": No address associated with hostname""",
                    isSuccess = false,
                )
            } else {
                HttpResponse(
                    statusCode = 200,
                    body = """{"installationId":"test-123","sessionId":"session-123","success":true}""",
                    isSuccess = true,
                    headers = mapOf("x-installation-token" to "test-token"),
                )
            }
        }

        mockkObject(DeviceInfoUtils)
        every { DeviceInfoUtils.getDeviceModel() } returns "Pixel"
        every { DeviceInfoUtils.getOsName() } returns "Android"
        every { DeviceInfoUtils.getOsVersion() } returns "14"
        every { DeviceInfoUtils.getLanguage() } returns "en"
        every { DeviceInfoUtils.getTimezone() } returns "UTC"
        every { DeviceInfoUtils.getAppVersion(any()) } returns "1.0.0"
        every { DeviceInfoUtils.getAppBuild(any()) } returns "1"
        every { DeviceInfoUtils.getDeviceId(any()) } returns "device-123"
        every { DeviceInfoUtils.getPersistentDeviceId(any()) } returns "persistent-123"
        every { DeviceInfoUtils.getNetworkType(any()) } returns "WiFi"
        every { DeviceInfoUtils.getDeviceOrientation(any()) } returns "Portrait"
        every { DeviceInfoUtils.getBatteryLevel(any()) } returns 90
        every { DeviceInfoUtils.isCharging(any()) } returns true
    }

    @After
    fun tearDown() {
        clearAllMocks()
        val ulinkClass = ULink::class.java
        ulinkClass.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        runCatching {
            ulinkClass.getDeclaredField("isInitializing").apply {
                isAccessible = true
                setBoolean(null, false)
            }
        }
    }

    private fun bootstrapCallCount() = requestedUrls.count { it.endsWith("/sdk/bootstrap") }

    /**
     * bootstrap() hops to Dispatchers.IO and resumes on Main, so the work is not
     * observable after a single idle(). Alternate draining the main looper with
     * short real sleeps (letting the IO pool progress) until [condition] holds.
     */
    private fun pumpUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }

    @Test
    fun `foregrounding retries bootstrap after a failed cold start`() = runTest {
        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        assertEquals("cold-start bootstrap should have been attempted once", 1, bootstrapCallCount())

        // Network recovers while the app is backgrounded.
        bootstrapFails = false
        requestedUrls.clear()

        ulink.onStart(mockk<LifecycleOwner>(relaxed = true))

        assertTrue(
            "bootstrap must be retried when the app is foregrounded after a failed cold start",
            pumpUntil { bootstrapCallCount() >= 1 },
        )
        assertTrue(
            "a session must start once the retried bootstrap succeeds",
            pumpUntil { requestedUrls.any { url -> url.endsWith("/sdk/sessions/start") } },
        )
    }

    @Test
    fun `foregrounding does not re-bootstrap when the cold start already succeeded`() = runTest {
        bootstrapFails = false

        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        assertEquals(1, bootstrapCallCount())
        requestedUrls.clear()

        ulink.onStart(mockk<LifecycleOwner>(relaxed = true))
        // Let the session-start path run so a stray re-bootstrap would be caught.
        pumpUntil { requestedUrls.any { url -> url.endsWith("/sdk/sessions/start") } }

        assertEquals(
            "a healthy SDK must not re-bootstrap on every foreground",
            0,
            bootstrapCallCount(),
        )
    }

    /**
     * The deferred check is launched inside setup()'s try block, after bootstrap().
     * When bootstrap failed, the check was skipped entirely and never revisited —
     * so a fresh install whose cold start hit a transient network error lost its
     * deferred deep link permanently, which is the whole feature for that user.
     */
    @Test
    fun `deferred link check runs once a bootstrap retry recovers`() = runTest {
        // Install Referrer is unavailable in unit tests; resolve it immediately.
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

        val deferredConfig = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com",
            debug = false,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = true,
        )

        val ulink = ULink.initialize(mockContext, deferredConfig, mockHttpClient)
        assertEquals(
            "a failed cold start cannot have run the deferred check",
            0,
            requestedUrls.count { it.endsWith("/sdk/deferred/match") },
        )

        // Network recovers while the app is backgrounded.
        bootstrapFails = false
        requestedUrls.clear()

        ulink.onStart(mockk<LifecycleOwner>(relaxed = true))

        assertTrue(
            "bootstrap must be retried",
            pumpUntil { bootstrapCallCount() >= 1 },
        )
        assertTrue(
            "the deferred check must run once bootstrap recovers, not be lost with the failed cold start",
            pumpUntil { requestedUrls.any { url -> url.endsWith("/sdk/deferred/match") } },
        )

        unmockkStatic(InstallReferrerClient::class)
    }
}
