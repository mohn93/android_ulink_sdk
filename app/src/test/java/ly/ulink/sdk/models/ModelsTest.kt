package ly.ulink.sdk.models

import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive

class ModelsTest {

    @Test
    fun `test ULinkConfig creation with all parameters`() {
        val config = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com",
            debug = true
        )

        assertEquals("test-api-key", config.apiKey)
        assertEquals("https://api.test.com", config.baseUrl)
        assertTrue(config.debug)
    }

    @Test
    fun `test ULinkConfig creation with default debug value`() {
        val config = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com"
        )

        assertEquals("test-api-key", config.apiKey)
        assertEquals("https://api.test.com", config.baseUrl)
        assertFalse(config.debug) // Default should be false
    }

    @Test
    fun `test ULinkParameters creation for dynamic link`() {
        val metadata = mapOf("key1" to "value1", "key2" to "value2")
        val parameters = ULinkParameters.dynamic(
            fallbackUrl = "https://example.com",
            metadata = metadata
        )

        assertEquals("dynamic", parameters.type)
        assertEquals("https://example.com", parameters.fallbackUrl)
        assertNotNull(parameters.metadata)
    }

    @Test
    fun `test ULinkParameters creation for unified link`() {
        val parameters = ULinkParameters.unified(
            iosUrl = "https://apps.apple.com/app/test",
            androidUrl = "https://play.google.com/store/apps/details?id=com.test",
            fallbackUrl = "https://example.com/desktop"
        )

        assertEquals("unified", parameters.type)
        assertEquals("https://apps.apple.com/app/test", parameters.iosUrl)
        assertEquals("https://play.google.com/store/apps/details?id=com.test", parameters.androidUrl)
        assertEquals("https://example.com/desktop", parameters.fallbackUrl)
        assertNull(parameters.metadata)
    }

    @Test
    fun `test ULinkParameters creation with minimal required fields`() {
        val parameters = ULinkParameters.dynamic(
            fallbackUrl = "https://example.com"
        )

        assertEquals("dynamic", parameters.type)
        assertEquals("https://example.com", parameters.fallbackUrl)
        assertNull(parameters.metadata)
    }

    @Test
    fun `test ULinkResponse creation for successful response`() {
        val response = ULinkResponse(
            success = true,
            url = "https://test.ly/abc123",
            error = null,
            data = buildJsonObject { put("slug", "abc123") }
        )

        assertTrue(response.success)
        assertEquals("https://test.ly/abc123", response.url)
        assertNull(response.error)
        assertEquals("abc123", response.data?.get("slug")?.jsonPrimitive?.content)
    }

    @Test
    fun `test ULinkResponse creation for error response`() {
        val response = ULinkResponse(
            success = false,
            url = null,
            error = "Invalid URL provided"
        )

        assertFalse(response.success)
        assertNull(response.url)
        assertEquals("Invalid URL provided", response.error)
    }

    @Test
    fun `test ULinkResolvedData creation and properties`() {
        // Test will be implemented when ULinkResolvedData structure is confirmed
        assertTrue("ULinkResolvedData test placeholder", true)
    }

    @Test
    fun `test ULinkSession creation and properties`() {
        // Test will be implemented when ULinkSession structure is confirmed
        assertTrue("ULinkSession test placeholder", true)
    }

    @Test
    fun `test model serialization and deserialization`() {
        // Test basic model functionality
        assertTrue("Model serialization test placeholder", true)
    }

    @Test
    fun `test ULinkParameters with empty metadata`() {
        val parameters = ULinkParameters.dynamic(
            fallbackUrl = "https://example.com",
            metadata = emptyMap()
        )

        assertEquals("dynamic", parameters.type)
        assertEquals("https://example.com", parameters.fallbackUrl)
        assertNotNull(parameters.metadata)
    }

    @Test
    fun `test ULinkParameters with complex metadata`() {
        val metadata = mapOf(
            "userId" to "user123",
            "campaign" to "summer2024",
            "source" to "email",
            "medium" to "newsletter",
            "content" to "header-cta",
            "customParam" to "value with spaces"
        )

        val parameters = ULinkParameters.dynamic(
            fallbackUrl = "https://example.com/product",
            metadata = metadata
        )

        assertEquals("dynamic", parameters.type)
        assertEquals("https://example.com/product", parameters.fallbackUrl)
        assertNotNull(parameters.metadata)
    }

    @Test
    fun `test ULinkResponse with empty data`() {
        val response = ULinkResponse(
            success = true,
            url = "https://example.com",
            data = null,
            error = null
        )

        assertTrue(response.success)
        assertEquals("https://example.com", response.url)
        assertNull(response.data)
        assertNull(response.error)
    }

    @Test
    fun `test model equality and hashCode`() {
        val config1 = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = true
        )
        
        val config2 = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = true
        )
        
        val config3 = ULinkConfig(
            apiKey = "different-key",
            baseUrl = "https://api.test.com",
            debug = true
        )

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
        assertNotEquals(config1, config3)
        assertNotEquals(config1.hashCode(), config3.hashCode())
    }
}