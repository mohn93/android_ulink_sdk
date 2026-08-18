package ly.ulink.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import io.mockk.*
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.models.ULinkInitializationError
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
 * Tests for offline/degraded-network resilience:
 *
 * 1. Cached-token bypass — a returning install already has a server-issued
 *    installation token. Operations (link resolution) must run on it immediately
 *    instead of blocking on / being rejected by a fresh bootstrap that is slow or
 *    failing on a poor connection (the field symptom on Telkom/Indonesia: the app
 *    hangs on a spinner while bootstrap stalls for the read timeout).
 *
 * 2. Connectivity-regained retry — when the device regains a network, bootstrap
 *    is retried immediately, not only on the next app foreground, so the degraded
 *    window closes as soon as connectivity returns.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OfflineResilienceTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockHttpClient: HttpClient
    private lateinit var config: ULinkConfig

    private var storedInstallationId: String? = null
    private var storedInstallationToken: String? = null

    private val requestedUrls = Collections.synchronizedList(mutableListOf<String>())
    /** Headers of the most recent GET (used to assert the cached token is sent). */
    private val lastGetHeaders = Collections.synchronizedMap(mutableMapOf<String, String>())

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
        every { mockEditor.putString("installation_token", any()) } answers {
            storedInstallationToken = secondArg(); mockEditor
        }
        every { mockEditor.apply() } just Runs
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }
        every { mockSharedPreferences.getString("installation_token", null) } answers { storedInstallationToken }
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
        coEvery { mockHttpClient.get(any(), any()) } answers {
            val url = firstArg<String>()
            requestedUrls.add(url)
            lastGetHeaders.clear()
            lastGetHeaders.putAll(secondArg<Map<String, String>>())
            HttpResponse(
                statusCode = 200,
                body = """{"url":"https://app.example.com/target","success":true}""",
                isSuccess = true,
            )
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
    private fun resolveCallCount() = requestedUrls.count { it.contains("/sdk/resolve") }

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

    // ---- Cached-token bypass ----------------------------------------------

    @Test
    fun `a returning install resolves links on its cached token while bootstrap is failing`() = runTest {
        storedInstallationId = "cached-id"
        storedInstallationToken = "cached-token"
        bootstrapFails = true

        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        // Cold-start bootstrap was attempted and failed (network down).
        assertTrue(pumpUntil { bootstrapCallCount() >= 1 })

        // Despite the failed bootstrap, resolution must proceed on the cached token.
        val response = ulink.resolveLink("https://ulink.ly/abc")

        assertTrue("resolveLink must succeed using the cached installation token", response.success)
        assertEquals(1, resolveCallCount())
        assertEquals(
            "the cached installation token must be sent on the resolve request",
            "cached-token",
            lastGetHeaders["X-Installation-Token"],
        )
    }

    @Test
    fun `without a cached token a failed bootstrap still blocks resolution`() = runTest {
        // Fresh install: no cached token.
        storedInstallationToken = null
        bootstrapFails = true

        val ulink = ULink.initialize(mockContext, config, mockHttpClient)
        assertTrue(pumpUntil { bootstrapCallCount() >= 1 })

        var thrown: Throwable? = null
        try {
            ulink.resolveLink("https://ulink.ly/abc")
        } catch (e: ULinkInitializationError) {
            thrown = e
        }

        assertNotNull("a first-install resolve with no token and a failed bootstrap must still throw", thrown)
        assertEquals("no resolve request should be made without credentials", 0, resolveCallCount())
    }

    // ---- Connectivity-regained retry --------------------------------------

    @Test
    fun `regaining connectivity retries bootstrap after a failed cold start`() = runTest {
        storedInstallationToken = null
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
        storedInstallationToken = null
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
