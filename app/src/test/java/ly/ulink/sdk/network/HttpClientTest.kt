package ly.ulink.sdk.network

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.net.HttpURLConnection
import java.net.URL
import io.mockk.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpClientTest {

    private lateinit var httpClient: HttpClient
    private lateinit var mockConnection: HttpURLConnection

    @Before
    fun setup() {
        mockConnection = mockk(relaxed = true)
        httpClient = HttpClient(debug = false) { _ -> mockConnection }
    }

    @Test
    fun `test successful GET request`() = runTest {
        val responseBody = """{"success": true, "data": "test"}"""
        
        // Connection provided via HttpClient connectionFactory
        
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())
        every { mockConnection.contentLength } returns responseBody.length
        
        val headers = mapOf("Authorization" to "Bearer test-token")
        val response = httpClient.get("https://api.test.com/data", headers)
        
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
        assertEquals(responseBody, response.body)
        
        verify { mockConnection.requestMethod = "GET" }
        verify { mockConnection.setRequestProperty("Authorization", "Bearer test-token") }
        // Content-Type not required for GET
    }

    @Test
    fun `test successful POST request with JSON`() = runTest {
        val requestBody = mapOf("key" to "value")
        val responseBody = """{"success": true, "id": "123"}"""
        
        // Connection provided via HttpClient connectionFactory
        
        val outputStream = ByteArrayOutputStream()
        every { mockConnection.responseCode } returns 201
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())
        every { mockConnection.outputStream } returns outputStream
        every { mockConnection.contentLength } returns responseBody.length
        
        val headers = mapOf("Authorization" to "Bearer test-token")
        val response = httpClient.postJson("https://api.test.com/create", requestBody, headers)
        
        assertTrue(response.isSuccess)
        assertEquals(201, response.statusCode)
        assertEquals(responseBody, response.body)
        
        verify { mockConnection.requestMethod = "POST" }
        verify { mockConnection.doOutput = true }
        verify { mockConnection.setRequestProperty("Authorization", "Bearer test-token") }
        // Content-Type not required for GET
        
        // Verify request body was written
        assertEquals("""{"key":"value"}""", outputStream.toString())
    }

    @Test
    fun `test HTTP error response`() = runTest {
        val errorBody = """{"error": "Bad Request"}"""
        
        // Connection provided via HttpClient connectionFactory
        
        every { mockConnection.responseCode } returns 400
        every { mockConnection.errorStream } returns ByteArrayInputStream(errorBody.toByteArray())
        every { mockConnection.inputStream } throws java.io.IOException("HTTP 400")
        
        val response = httpClient.get("https://api.test.com/invalid")
        
        assertFalse(response.isSuccess)
        assertEquals(400, response.statusCode)
        assertEquals(errorBody, response.body)
    }

    @Test
    fun `test network connection failure`() = runTest {
        httpClient = HttpClient(debug = false) { _ -> throw java.net.ConnectException("Connection refused") }
        
        val response = httpClient.get("https://invalid.url.com/test")
        
        assertFalse(response.isSuccess)
        assertEquals(-1, response.statusCode)
        assertTrue(response.body.contains("Connection refused"))
    }

    @Test
    fun `test request timeout`() = runTest {
        every { mockConnection.responseCode } throws java.net.SocketTimeoutException("Read timed out")
        
        val response = httpClient.get("https://slow.api.com/test")
        
        assertFalse(response.isSuccess)
        assertEquals(-1, response.statusCode)
        assertTrue(response.body.contains("Read timed out"))
    }

    @Test
    fun `test POST request with empty body`() = runTest {
        val responseBody = """{"success": true}"""
        
        // Connection provided via HttpClient connectionFactory
        
        val outputStream = ByteArrayOutputStream()
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())
        every { mockConnection.outputStream } returns outputStream
        every { mockConnection.contentLength } returns responseBody.length
        
        val response = httpClient.post("https://api.test.com/ping", "")
        
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
        assertEquals(responseBody, response.body)
        
        verify { mockConnection.requestMethod = "POST" }
        verify { mockConnection.doOutput = true }
    }

    @Test
    fun `test request with custom headers`() = runTest {
        val responseBody = """{"data": "test"}"""
        
        // Connection provided via HttpClient connectionFactory
        
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())
        every { mockConnection.contentLength } returns responseBody.length
        
        val headers = mapOf(
            "Authorization" to "Bearer custom-token",
            "X-Custom-Header" to "custom-value",
            "User-Agent" to "ULink-SDK/1.0"
        )
        
        val response = httpClient.get("https://api.test.com/data", headers)
        
        assertTrue(response.isSuccess)
        
        verify { mockConnection.setRequestProperty("Authorization", "Bearer custom-token") }
        verify { mockConnection.setRequestProperty("X-Custom-Header", "custom-value") }
        verify { mockConnection.setRequestProperty("User-Agent", "ULink-SDK/1.0") }
        // Content-Type not required for GET
    }

    @Test
    fun `test large response body handling`() = runTest {
        val largeResponseBody = "x".repeat(10000) // 10KB response
        
        // Connection provided via HttpClient connectionFactory
        
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(largeResponseBody.toByteArray())
        every { mockConnection.contentLength } returns largeResponseBody.length
        
        val response = httpClient.get("https://api.test.com/large-data")
        
        assertTrue(response.isSuccess)
        assertEquals(200, response.statusCode)
        assertEquals(largeResponseBody, response.body)
        assertEquals(10000, response.body.length)
    }
}