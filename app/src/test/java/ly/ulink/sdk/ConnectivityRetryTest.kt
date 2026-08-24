package ly.ulink.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
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
 * Connectivity-regained bootstrap retry.
 *
 * A cold start that failed while offline previously stayed degraded until the
 * next app foreground. handleNetworkAvailable() (invoked by a default-network
 * callback registered in setup()) retries bootstrap the moment connectivity
 * returns instead. Guarded so a healthy SDK is never re-bootstrapped and
 * overlapping callbacks don't stack concurrent bootstraps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ConnectivityRetryTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockHttpClient: HttpClient
    private lateinit var config: ULinkConfig

    private var storedInstallationId: String? = null
    private val requestedUrls = Collections.synchronizedList(mutableListOf<String>())
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
            storedInstallationId = secondArg(); mockEditor
        }
        every { mockEditor.apply() } just Runs
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }
        every { mockSharedPreferences.getString("installation_token", null) } returns null
        every { mockSharedPreferences.getBoolean("ulink_deferred_checked", any()) } returns false

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
                HttpResponse(statusCode = -1, body = "Read timed out", isSuccess = false)
            } else {
                HttpResponse(
                    statusCode = 200,
                    body = """{"installationId":"test-123","sessionId":"session-123","success":true}""",
                    isSuccess = true,
                    headers = mapOf("x-installation-token" to "fresh-token"),
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
        ULink::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
    }

    private fun bootstrapCallCount() = requestedUrls.count { it.endsWith("/sdk/bootstrap") }

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
    fun `regaining connectivity retries bootstrap after a failed cold start`() = runTest {
        bootstrapFails = true

        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        assertTrue("cold-start bootstrap should have been attempted", pumpUntil { bootstrapCallCount() >= 1 })

        // Network recovers; the connectivity callback fires.
        bootstrapFails = false
        requestedUrls.clear()

        ulink.handleNetworkAvailable()

        assertTrue(
            "bootstrap must be retried the moment connectivity returns, not only on foreground",
            pumpUntil { bootstrapCallCount() >= 1 },
        )
    }

    @Test
    fun `regaining connectivity does not re-bootstrap when the cold start already succeeded`() = runTest {
        bootstrapFails = false

        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        assertTrue(pumpUntil { bootstrapCallCount() >= 1 })
        requestedUrls.clear()

        ulink.handleNetworkAvailable()
        // Give a stray retry a chance to land before asserting it did not.
        pumpUntil(timeoutMs = 1_000) { bootstrapCallCount() > 0 }

        assertEquals("a healthy SDK must not re-bootstrap on connectivity changes", 0, bootstrapCallCount())
    }
}
