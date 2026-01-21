package ly.ulink.sdk

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.mockk.*
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.*
import kotlinx.serialization.json.jsonPrimitive
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import ly.ulink.sdk.utils.DeviceInfoUtils

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ULinkTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockHttpClient: HttpClient
    private lateinit var config: ULinkConfig
    private lateinit var ulink: ULink
    private var storedInstallationId: String? = null

    @Before
    fun setup() {
        // Mock Android dependencies
        mockContext = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        
        every { mockContext.getApplicationContext() } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString("installation_id", any()) } answers {
            storedInstallationId = secondArg()
            mockEditor
        }
        every { mockEditor.apply() } just Runs
        // Simulate SharedPreferences storage for installation ID
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }
        
        // Create test configuration
        config = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com",
            debug = true
        )
        
        // Mock HttpClient
        mockHttpClient = mockk(relaxed = true)

        // Mock all postJson calls for initialization (bootstrap, session, etc.)
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns HttpResponse(
            statusCode = 200,
            body = """{"installationId":"test-123","token":"test-token","sessionId":"session-123","success":true}""",
            isSuccess = true,
            headers = mapOf("x-installation-token" to "test-token")
        )

        // Mock GET requests for link resolution
        coEvery { mockHttpClient.get(any(), any()) } returns HttpResponse(
            statusCode = 200,
            body = """{"success":true,"url":"https://example.com"}""",
            isSuccess = true
        )

        // Mock DeviceInfoUtils to avoid real Android services
        mockkObject(DeviceInfoUtils)
        every { DeviceInfoUtils.getDeviceModel() } returns "Pixel"
        every { DeviceInfoUtils.getOsName() } returns "Android"
        every { DeviceInfoUtils.getOsVersion() } returns "14"
        every { DeviceInfoUtils.getLanguage() } returns "en"
        every { DeviceInfoUtils.getTimezone() } returns "UTC"
        every { DeviceInfoUtils.getAppVersion(any()) } returns "1.0.0"
        every { DeviceInfoUtils.getDeviceId(any()) } returns "device-123"
        every { DeviceInfoUtils.getNetworkType(any()) } returns "WiFi"
        every { DeviceInfoUtils.getDeviceOrientation(any()) } returns "Portrait"
        every { DeviceInfoUtils.getBatteryLevel(any()) } returns 90
        every { DeviceInfoUtils.isCharging(any()) } returns true
        
        // Initialize ULink with mocked dependencies
        ulink = ULink.createTestInstance(mockContext, config, mockHttpClient)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        // Reset singleton instance
        ULink::class.java.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
    }

    @Test
    fun `test ULink initialization`() {
        assertNotNull(ulink)
        assertEquals(ulink, ULink.getInstance())
    }

    @Test
    fun `test createLink with dynamic parameters`() = runTest {
        val mockResponse = HttpResponse(
            isSuccess = true,
            statusCode = 200,
            body = """{"success": true, "url": "https://test.ly/abc123", "slug": "abc123"}"""
        )

        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockResponse

        val parameters = ULinkParameters.dynamic(
            domain = "test.ly",
            fallbackUrl = "https://example.com",
            metadata = mapOf("key" to "value")
        )

        val result = ulink.createLink(parameters)

        assertTrue(result.success)
        assertEquals("https://test.ly/abc123", result.url)
        // slug is now available inside result.data
        assertEquals("abc123", result.data?.get("slug")?.jsonPrimitive?.content)
    }

    @Test
    fun `test createLink with unified parameters`() = runTest {
        val mockResponse = HttpResponse(
            isSuccess = true,
            statusCode = 200,
            body = """{"success": true, "url": "https://test.ly/unified123"}"""
        )

        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockResponse

        val parameters = ULinkParameters.unified(
            domain = "test.ly",
            iosUrl = "https://apps.apple.com/app/test",
            androidUrl = "https://play.google.com/store/apps/details?id=com.test",
            fallbackUrl = "https://example.com"
        )

        val result = ulink.createLink(parameters)

        assertTrue(result.success)
        assertEquals("https://test.ly/unified123", result.url)
    }

    @Test
    fun `test createLink failure`() = runTest {
        val mockResponse = HttpResponse(
            isSuccess = false,
            statusCode = 400,
            body = "Bad Request"
        )

        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockResponse

        val parameters = ULinkParameters.dynamic(
            domain = "test.ly",
            fallbackUrl = "invalid-url"
        )

        val result = ulink.createLink(parameters)

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    @Test
    fun `test resolveLink success`() = runTest {
        val mockResponse = HttpResponse(
            isSuccess = true,
            statusCode = 200,
            body = """{"success": true, "url": "https://example.com", "metadata": {"key": "value"}}"""
        )
        
        coEvery { mockHttpClient.get(any(), any()) } returns mockResponse
        
        val result = ulink.resolveLink("https://test.ly/abc123")
        
        assertTrue(result.success)
        assertEquals("https://example.com", result.url)
    }

    @Test
    fun `test session management methods available`() = runTest {
        // Session management is automatic via ProcessLifecycleOwner
        // After initialization with a valid response containing sessionId,
        // there should be an active session
        assertTrue(ulink.hasActiveSession())
        assertNotNull(ulink.getCurrentSessionId())
    }

    @Test
    fun `test endSession clears active session`() = runTest {
        // Test that endSession properly clears the session
        // After initialization, there's an active session
        storedInstallationId = "existing-id"

        // Session exists after initialization
        assertTrue(ulink.hasActiveSession())
        assertNotNull(ulink.getCurrentSessionId())

        // End the session
        val result = ulink.endSession()

        // Should succeed and clear the session
        assertTrue(result)
        assertFalse(ulink.hasActiveSession())
        assertNull(ulink.getCurrentSessionId())
    }

    @Test
    fun `test handleDeepLink with valid URI`() {
        val uri = Uri.parse("https://test.ly/abc123?param=value")
        
        // This should not throw an exception
        ulink.handleDeepLink(uri)
        
        // Verify the URI was processed
        verify { mockContext.getSharedPreferences(any(), any()) }
    }

    @Test
    fun `test getInstallationId generates and persists ID`() {
        val installationId = ulink.getInstallationId()
        assertNotNull(installationId)
        verify(atLeast = 1) { mockEditor.putString("installation_id", any()) }
        verify(atLeast = 1) { mockEditor.apply() }
    }

    @Test
    fun `test getInstallationId returns existing ID`() {
        every { mockSharedPreferences.getString("installation_id", null) } returns "existing-id"
        
        val installationId = ulink.getInstallationId()
        
        assertEquals("existing-id", installationId)
    }

    @Test
    fun `test setInitialUri and getInitialUri`() {
        val uri = Uri.parse("https://test.ly/initial")
        
        ulink.setInitialUri(uri)
        assertEquals(uri, ulink.getInitialUri())
    }

    @Test
    fun `test session state after initialization`() {
        // After successful initialization with sessionId in response,
        // there should be an active session
        assertTrue(ulink.hasActiveSession())
        assertNotNull(ulink.getCurrentSessionId())
    }
}