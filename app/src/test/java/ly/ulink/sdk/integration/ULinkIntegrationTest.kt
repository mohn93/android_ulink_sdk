package ly.ulink.sdk.integration

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import io.mockk.*
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.ULink
import ly.ulink.sdk.models.*
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ly.ulink.sdk.utils.DeviceInfoUtils

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ULinkIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var config: ULinkConfig
    private lateinit var ulink: ULink
    private lateinit var mockHttpClient: HttpClient
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
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }
        
        // Create test configuration
        config = ULinkConfig(
            apiKey = "test-api-key-integration",
            baseUrl = "https://api.ulink.ly",
            debug = true
        )
        
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

        // Mock DeviceInfoUtils
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
        // Initialize ULink with injected HttpClient
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
    fun `test complete link creation and resolution workflow`() = runTest {
        // Mock HTTP responses for the complete workflow
        val createLinkResponse = HttpResponse(
            isSuccess = true,
            statusCode = 201,
            body = """{"success": true, "url": "https://ulink.ly/abc123", "slug": "abc123"}"""
        )
        
        val resolveLinkResponse = HttpResponse(
            isSuccess = true,
            statusCode = 200,
            body = """{"success": true, "url": "https://example.com/product/123", "metadata": {"userId": "user123", "campaign": "summer2024"}}"""
        )
        
        coEvery { mockHttpClient.postJson(match { it.contains("/links") }, any(), any()) } returns createLinkResponse
        coEvery { mockHttpClient.get(match { it.contains("/resolve") }, any()) } returns resolveLinkResponse
        
        // Step 1: Create a dynamic link
        val createParameters = ULinkParameters.dynamic(
            domain = "ulink.ly",
            fallbackUrl = "https://example.com/product/123",
            metadata = mapOf(
                "userId" to "user123",
                "campaign" to "summer2024",
                "source" to "mobile-app"
            )
        )
        
        val createResult = ulink.createLink(createParameters)
        
        assertTrue("Link creation should succeed", createResult.success)
        assertEquals("https://ulink.ly/abc123", createResult.url)
        assertEquals("abc123", createResult.data?.get("slug")?.jsonPrimitive?.content)
        
        // Step 2: Resolve the created link
        val resolveResult = ulink.resolveLink("https://ulink.ly/abc123")
        
        assertTrue("Link resolution should succeed", resolveResult.success)
        assertEquals("https://example.com/product/123", resolveResult.url)
        assertNotNull("Data should be present", resolveResult.data)
        val metadata = resolveResult.data?.get("metadata")?.jsonObject
        assertEquals("user123", metadata?.get("userId")?.jsonPrimitive?.content)
        assertEquals("summer2024", metadata?.get("campaign")?.jsonPrimitive?.content)
    }

    @Test
    fun `test session management state methods`() = runTest {
        // Session management is automatic via ProcessLifecycleOwner
        // After initialization with sessionId in response, there's an active session

        // Ensure installation ID exists
        every { mockSharedPreferences.getString("installation_id", null) } returns "existing-id"

        // After initialization, session should be active
        assertTrue("Should have active session after initialization", ulink.hasActiveSession())
        assertNotNull("Session ID should exist after initialization", ulink.getCurrentSessionId())

        // End the session
        val endResult = ulink.endSession()
        assertTrue("Session end should succeed", endResult)
        assertFalse("Should not have active session after end", ulink.hasActiveSession())
        assertNull("Session ID should be null after end", ulink.getCurrentSessionId())
    }

    @Test
    fun `test unified link creation with fallback URLs`() = runTest {
        val createLinkResponse = HttpResponse(
            isSuccess = true,
            statusCode = 201,
            body = """{"success": true, "url": "https://ulink.ly/unified456", "slug": "unified456"}"""
        )
        
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns createLinkResponse
        
        val parameters = ULinkParameters.unified(
            domain = "ulink.ly",
            iosUrl = "https://apps.apple.com/app/example-app/id123456789",
            androidUrl = "https://play.google.com/store/apps/details?id=com.example.app",
            fallbackUrl = "https://example.com/download"
        )
        
        val result = ulink.createLink(parameters)
        
        assertTrue("Unified link creation should succeed", result.success)
        assertEquals("https://ulink.ly/unified456", result.url)
        assertEquals("unified456", result.data?.get("slug")?.jsonPrimitive?.content)
    }

    @Test
    fun `test error handling in complete workflow`() = runTest {
        // Mock error responses
        val errorResponse = HttpResponse(
            isSuccess = false,
            statusCode = 400,
            body = "Invalid request parameters"
        )
        
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns errorResponse
        coEvery { mockHttpClient.get(any(), any()) } returns errorResponse
        
        // Test link creation error
        val createParameters = ULinkParameters.dynamic(
            domain = "ulink.ly",
            fallbackUrl = "invalid-url"
        )

        val createResult = ulink.createLink(createParameters)

        assertFalse("Link creation should fail with invalid URL", createResult.success)
        assertNotNull("Error message should be present", createResult.error)

        // Test link resolution error
        val resolveResult = ulink.resolveLink("https://ulink.ly/nonexistent")

        assertFalse("Link resolution should fail for nonexistent link", resolveResult.success)
        assertNotNull("Error message should be present", resolveResult.error)
    }

    @Test
    fun `test deep link handling workflow`() = runTest {
        // Test various deep link scenarios
        val deepLinks = listOf(
            "https://ulink.ly/abc123",
            "https://ulink.ly/abc123?param=value",
            "ulink://open?url=https://example.com",
            "https://custom.domain.com/link/xyz789"
        )
        
        deepLinks.forEach { linkUrl ->
            val uri = Uri.parse(linkUrl)
            
            // This should not throw an exception
            assertDoesNotThrow("Deep link handling should not throw for: $linkUrl") {
                ulink.handleDeepLink(uri)
            }
        }
    }

    @Test
    fun `test installation ID persistence across sessions`() {
        // First call should return a non-null ID (generated during SDK setup if missing)
        val firstId = ulink.getInstallationId()
        assertNotNull("Installation ID should be generated", firstId)
        
        // Second call should return the same ID
        val secondId = ulink.getInstallationId()
        assertEquals("Installation ID should be consistent", firstId, secondId)
    }

    @Test
    fun `test concurrent operations handling`() = runTest {
        // Mock successful responses
        val successResponse = HttpResponse(
            isSuccess = true,
            statusCode = 200,
            body = """{"success": true, "url": "https://ulink.ly/concurrent", "slug": "concurrent"}"""
        )
        
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns successResponse
        coEvery { mockHttpClient.get(any(), any()) } returns successResponse
        
        // Simulate concurrent operations
        val parameters = ULinkParameters.dynamic(
            domain = "ulink.ly",
            fallbackUrl = "https://example.com/concurrent"
        )
        
        // Multiple concurrent link creations should all succeed
        val results = (1..5).map {
            ulink.createLink(parameters)
        }
        
        results.forEach { result ->
            assertTrue("Concurrent operation should succeed", result.success)
        }
    }

    private fun assertDoesNotThrow(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            fail("$message - Exception thrown: ${e.message}")
        }
    }
}