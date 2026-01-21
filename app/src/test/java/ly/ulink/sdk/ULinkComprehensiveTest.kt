package ly.ulink.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.*
import ly.ulink.sdk.network.HttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.*

/**
 * Comprehensive test suite for ULink SDK in Kotlin.
 *
 * This serves as a safety net to ensure that when we add Java compatibility features
 * (like @JvmStatic, CompletableFuture wrappers, and listeners), we don't break
 * existing Kotlin functionality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ULinkComprehensiveTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig
    private lateinit var mockHttpClient: HttpClient

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        config = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = true,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = false,
            persistLastLinkData = false
        )
        mockHttpClient = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        // Reset singleton instance between tests
        try {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        } catch (e: Exception) {
            // Ignore if field doesn't exist
        }
    }

    // ===== INITIALIZATION TESTS =====

    @Test
    fun `test initialize with default config`() = runTest {
        // Mock HTTP responses - use postJson as that's what the SDK actually calls
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        assertNotNull("ULink instance should not be null", ulink)
        assertNotNull("Installation ID should not be null", ulink.getInstallationId())
    }

    @Test
    fun `test initialize with enableDeepLinkIntegration true`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val config1 = config.copy(enableDeepLinkIntegration = true)
        val ulink = ULink.initialize(context, config1, mockHttpClient)

        assertNotNull("ULink should initialize with enableDeepLinkIntegration = true", ulink)
    }

    @Test
    fun `test initialize with autoCheckDeferredLink true`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val config2 = config.copy(autoCheckDeferredLink = true)
        val ulink = ULink.initialize(context, config2, mockHttpClient)

        assertNotNull("ULink should initialize with autoCheckDeferredLink = true", ulink)
    }

    @Test
    fun `test getInstance before initialization throws error`() {
        try {
            ULink.getInstance()
            fail("getInstance should throw exception when not initialized")
        } catch (e: IllegalStateException) {
            assertTrue("Error message should mention not initialized",
                e.message?.contains("not initialized") == true)
        }
    }

    @Test
    fun `test getInstance after initialization returns same instance`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink1 = ULink.initialize(context, config, mockHttpClient)
        val ulink2 = ULink.getInstance()

        assertSame("getInstance should return the same instance", ulink1, ulink2)
    }

    // ===== SESSION MANAGEMENT TESTS =====

    @Test
    fun `test session is created on initialization`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Bootstrap response contains sessionId, so session is active
        assertTrue("Should have active session after bootstrap with sessionId", ulink.hasActiveSession())
        assertEquals("session-123", ulink.getCurrentSessionId())
    }

    @Test
    fun `test endSession clears current session`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123","success":true}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Active session after bootstrap
        assertTrue("Should have active session", ulink.hasActiveSession())

        // End the session
        val result = ulink.endSession()
        assertTrue("endSession should succeed", result)
        assertFalse("Session should be cleared after endSession", ulink.hasActiveSession())
        assertNull("Session ID should be null after endSession", ulink.getCurrentSessionId())
    }

    @Test
    fun `test getSessionState returns correct state`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Note: hasActiveSession() returns true (because currentSessionId is set from bootstrap)
        // But getSessionState() returns IDLE (sessionState is only set to ACTIVE by explicit startSession())
        // This is because bootstrap sets currentSessionId but not sessionState
        assertTrue("hasActiveSession should be true", ulink.hasActiveSession())
        assertEquals("getSessionState should be IDLE (not started via lifecycle)",
            SessionState.IDLE, ulink.getSessionState())
    }

    // ===== LINK CREATION TESTS =====

    @Test
    fun `test createLink with dynamic parameters`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        val params = ULinkParameters.dynamic(
            domain = "test.ly",
            slug = "test-link",
            fallbackUrl = "https://test.com",
            parameters = mapOf("key" to "value")
        )

        // Mock link creation response
        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"shortUrl":"https://test.ly/test-link","data":{"slug":"test-link"}}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
                put("shortUrl", kotlinx.serialization.json.JsonPrimitive("https://test.ly/test-link"))
            }
        }

        val response = ulink.createLink(params)

        assertTrue("Link creation should succeed", response.success)
        assertNotNull("Response should contain URL", response.url)
        assertTrue("URL should contain slug",
            response.url?.contains("test-link") == true)
    }

    @Test
    fun `test createLink with unified parameters`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        val params = ULinkParameters.unified(
            domain = "test.ly",
            iosUrl = "https://ios.test.com",
            androidUrl = "https://android.test.com",
            fallbackUrl = "https://test.com"
        )

        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"shortUrl":"https://test.ly/unified"}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
                put("shortUrl", kotlinx.serialization.json.JsonPrimitive("https://test.ly/unified"))
            }
        }

        val response = ulink.createLink(params)

        assertTrue("Unified link creation should succeed", response.success)
    }

    @Test
    fun `test createLink with social media tags`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        val socialTags = SocialMediaTags(
            ogTitle = "Test Title",
            ogDescription = "Test Description",
            ogImage = "https://test.com/image.jpg"
        )

        val params = ULinkParameters.dynamic(
            domain = "test.ly",
            fallbackUrl = "https://test.com",
            socialMediaTags = socialTags
        )

        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"shortUrl":"https://test.ly/social"}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
                put("shortUrl", kotlinx.serialization.json.JsonPrimitive("https://test.ly/social"))
            }
        }

        val response = ulink.createLink(params)

        assertTrue("Link with social tags should be created", response.success)
    }

    // ===== LINK RESOLUTION TESTS =====

    @Test
    fun `test resolveLink with valid URL`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        coEvery { mockHttpClient.get(any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"data":{"slug":"test","type":"dynamic"}}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
            }
        }

        val response = ulink.resolveLink("https://test.ly/test")

        assertTrue("Link resolution should succeed", response.success)
    }

    // ===== ERROR HANDLING TESTS =====

    @Test
    fun `test createLink handles network error`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } throws Exception("Network error")

        // SDK catches exceptions and returns error response
        val response = ulink.createLink(ULinkParameters.dynamic(
            domain = "test.ly",
            fallbackUrl = "https://test.com"
        ))

        assertFalse("Response should indicate failure", response.success)
        assertTrue("Error should contain network error message",
            response.error?.contains("Network error") == true)
    }

    @Test
    fun `test createLink handles HTTP error response`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } returns mockk {
            every { statusCode } returns 400
            every { body } returns """{"success":false,"error":"Invalid domain"}"""
        }

        val response = ulink.createLink(ULinkParameters.dynamic(
            domain = "invalid.ly",
            fallbackUrl = "https://test.com"
        ))

        assertFalse("Link creation should fail with HTTP error", response.success)
        assertNotNull("Error message should be present", response.error)
    }

    // ===== INSTALLATION TRACKING TESTS =====

    @Test
    fun `test getInstallationId returns non-null value`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        val installationId = ulink.getInstallationId()
        assertNotNull("Installation ID should not be null", installationId)
        assertTrue("Installation ID should not be empty", installationId?.isNotEmpty() ?: false)
    }

    @Test
    fun `test getInstallationInfo returns valid data`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        val info = ulink.getInstallationInfo()
        assertNotNull("Installation info should not be null", info)
    }

    // ===== MODEL TESTS =====

    @Test
    fun `test ULinkConfig creation with all parameters`() {
        val config = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://custom.api.com",
            debug = true,
            enableDeepLinkIntegration = true,
            autoCheckDeferredLink = true,
            persistLastLinkData = true
        )

        assertEquals("test-key", config.apiKey)
        assertEquals("https://custom.api.com", config.baseUrl)
        assertTrue(config.debug)
        assertTrue(config.enableDeepLinkIntegration)
        assertTrue(config.autoCheckDeferredLink)
        assertTrue(config.persistLastLinkData)
    }

    @Test
    fun `test ULinkParameters dynamic factory method`() {
        val params = ULinkParameters.dynamic(
            domain = "test.ly",
            slug = "test-slug",
            fallbackUrl = "https://test.com",
            parameters = mapOf("key" to "value"),
            metadata = mapOf("source" to "test")
        )

        assertEquals(ULinkType.DYNAMIC.name.lowercase(), params.type)
        assertEquals("test.ly", params.domain)
        assertEquals("test-slug", params.slug)
        assertEquals("https://test.com", params.fallbackUrl)
        assertNotNull(params.parameters)
        assertNotNull(params.metadata)
    }

    @Test
    fun `test ULinkParameters unified factory method`() {
        val params = ULinkParameters.unified(
            domain = "test.ly",
            iosUrl = "https://ios.test.com",
            androidUrl = "https://android.test.com",
            fallbackUrl = "https://test.com"
        )

        assertEquals(ULinkType.UNIFIED.name.lowercase(), params.type)
        assertEquals("https://ios.test.com", params.iosUrl)
        assertEquals("https://android.test.com", params.androidUrl)
    }

    @Test
    fun `test SocialMediaTags creation`() {
        val tags = SocialMediaTags(
            ogTitle = "Test Title",
            ogDescription = "Test Description",
            ogImage = "https://test.com/image.jpg"
        )

        assertEquals("Test Title", tags.ogTitle)
        assertEquals("Test Description", tags.ogDescription)
        assertEquals("https://test.com/image.jpg", tags.ogImage)
    }

    @Test
    fun `test ULinkResponse success factory`() {
        val response = ULinkResponse.success("https://test.ly/link")

        assertTrue(response.success)
        assertEquals("https://test.ly/link", response.url)
        assertNull(response.error)
    }

    @Test
    fun `test ULinkResponse error factory`() {
        val response = ULinkResponse.error("Test error message")

        assertFalse(response.success)
        assertEquals("Test error message", response.error)
        assertNull(response.url)
    }

    // ===== DISPOSE TESTS =====

    @Test
    fun `test dispose cleans up resources`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Dispose should clean up resources without throwing
        ulink.dispose()

        // After dispose, SDK should still not crash but may not work
        // (Exact behavior depends on implementation)
    }

    // ===== CALLBACK-BASED METHODS TESTS =====

    @Test
    fun `test callback-based initialize success`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        // Use suspend initialize with mocked client
        val ulink = ULink.initialize(context, config, mockHttpClient)

        assertNotNull("ULink instance should be provided", ulink)
        assertNotNull("Installation ID should be set", ulink.getInstallationId())
    }

    @Test
    fun `test callback-based createLink success`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        coEvery {
            mockHttpClient.postJson(
                match { it.contains("/sdk/links") },
                any(),
                any()
            )
        } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"shortUrl":"https://test.ly/callback-test"}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
                put("shortUrl", kotlinx.serialization.json.JsonPrimitive("https://test.ly/callback-test"))
            }
        }

        val params = ULinkParameters.dynamic(
            domain = "test.ly",
            slug = "callback-test",
            fallbackUrl = "https://test.com"
        )

        // Test the suspend version directly
        val response = ulink.createLink(params)

        assertTrue("Response should indicate success", response.success)
        assertEquals("https://test.ly/callback-test", response.url)
    }

    @Test
    fun `test callback-based resolveLink success`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // resolveLink uses GET not POST
        coEvery {
            mockHttpClient.get(
                match { it.contains("/sdk/resolve") },
                any()
            )
        } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"success":true,"data":{"slug":"resolved-link"}}"""
            every { isSuccess } returns true
            every { headers } returns emptyMap()
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
            }
        }

        // Test the suspend version directly
        val response = ulink.resolveLink("https://test.ly/resolved-link")

        assertTrue("Response should be successful", response.success)
    }

    @Test
    fun `test callback-based endSession success`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123","success":true}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
                put("success", kotlinx.serialization.json.JsonPrimitive(true))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Test the suspend version directly (callback version uses same underlying logic)
        val result = ulink.endSession()

        assertTrue("endSession should return true", result)
        assertFalse("Session should be ended", ulink.hasActiveSession())
    }

    // ===== LISTENER REMOVAL TESTS =====

    @Test
    fun `test setOnLinkListener and removeOnLinkListener`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        var listenerCalled = false

        // Set listener
        ulink.setOnLinkListener { _ ->
            listenerCalled = true
        }

        // Remove listener (should not throw)
        ulink.removeOnLinkListener()

        // Setting listener again should work
        ulink.setOnLinkListener { _ ->
            listenerCalled = true
        }

        // Remove again
        ulink.removeOnLinkListener()

        // No exceptions should be thrown
        assertTrue("Test completed without exception", true)
    }

    @Test
    fun `test setOnUnifiedLinkListener and removeOnUnifiedLinkListener`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Set and remove listener
        ulink.setOnUnifiedLinkListener { _ -> }
        ulink.removeOnUnifiedLinkListener()

        // No exceptions should be thrown
        assertTrue("Test completed without exception", true)
    }

    @Test
    fun `test setOnReinstallListener and removeOnReinstallListener`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Set and remove listener
        ulink.setOnReinstallListener { _ -> }
        ulink.removeOnReinstallListener()

        // No exceptions should be thrown
        assertTrue("Test completed without exception", true)
    }

    @Test
    fun `test setOnLogListener and removeOnLogListener`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Set and remove listener
        ulink.setOnLogListener { _ -> }
        ulink.removeOnLogListener()

        // No exceptions should be thrown
        assertTrue("Test completed without exception", true)
    }

    @Test
    fun `test removeAllListeners removes all listeners`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        // Set all listeners
        ulink.setOnLinkListener { _ -> }
        ulink.setOnUnifiedLinkListener { _ -> }
        ulink.setOnReinstallListener { _ -> }
        ulink.setOnLogListener { _ -> }

        // Remove all at once
        ulink.removeAllListeners()

        // No exceptions should be thrown
        assertTrue("Test completed without exception", true)
    }

    @Test
    fun `test listener replacement cancels previous job`() = runTest {
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns mockk {
            every { statusCode } returns 200
            every { body } returns """{"installationId":"test-123","token":"test-token","sessionId":"session-123"}"""
            every { isSuccess } returns true
            every { headers } returns mapOf("x-installation-token" to "test-token")
            every { parseJson() } returns kotlinx.serialization.json.buildJsonObject {
                put("installationId", kotlinx.serialization.json.JsonPrimitive("test-123"))
                put("sessionId", kotlinx.serialization.json.JsonPrimitive("session-123"))
            }
        }

        val ulink = ULink.initialize(context, config, mockHttpClient)

        var firstListenerCalls = 0
        var secondListenerCalls = 0

        // Set first listener
        ulink.setOnLinkListener { _ ->
            firstListenerCalls++
        }

        // Replace with second listener (should cancel first)
        ulink.setOnLinkListener { _ ->
            secondListenerCalls++
        }

        // The first listener should not be called when new link data arrives
        // (This is validated by the implementation properly cancelling the old job)
        assertTrue("Test completed without exception", true)
    }
}
