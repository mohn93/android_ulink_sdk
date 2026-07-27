package ly.ulink.sdk.network

import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import java.net.HttpURLConnection
import java.net.URL
import io.mockk.*
import java.io.ByteArrayInputStream
import java.net.UnknownHostException
import java.net.ConnectException
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

    // ── Retry on transient pre-send network failures ──────────────────────
    //
    // A single DNS hiccup at cold start used to fail permanently (statusCode -1)
    // because there was no retry anywhere in the SDK. Only failures that prove
    // the request never reached the server are retried, so no request with side
    // effects (session start, bootstrap) can be duplicated.

    @Test
    fun `retries a DNS failure and succeeds on a later attempt`() = runTest {
        var attempts = 0
        val client = HttpClient(debug = false, retryBackoffMs = 0L) { _ ->
            attempts++
            if (attempts == 1) throw UnknownHostException("Unable to resolve host \"api.ulink.ly\"")
            mockConnection
        }
        val responseBody = """{"success": true}"""
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())

        val response = client.get("https://api.test.com/data")

        assertTrue("transient DNS failure must not be fatal", response.isSuccess)
        assertEquals(200, response.statusCode)
        assertEquals("should have retried exactly once", 2, attempts)
    }

    @Test
    fun `retries a connect failure for POST too`() = runTest {
        var attempts = 0
        val client = HttpClient(debug = false, retryBackoffMs = 0L) { _ ->
            attempts++
            if (attempts == 1) throw ConnectException("Failed to connect")
            mockConnection
        }
        val responseBody = """{"success": true}"""
        every { mockConnection.responseCode } returns 201
        every { mockConnection.inputStream } returns ByteArrayInputStream(responseBody.toByteArray())
        every { mockConnection.outputStream } returns ByteArrayOutputStream()

        val response = client.postJson("https://api.test.com/create", mapOf("k" to "v"))

        assertTrue(response.isSuccess)
        assertEquals(2, attempts)
    }

    @Test
    fun `gives up after a bounded number of attempts and reports the error`() = runTest {
        var attempts = 0
        val client = HttpClient(debug = false, retryBackoffMs = 0L) { _ ->
            attempts++
            throw UnknownHostException("Unable to resolve host \"api.ulink.ly\"")
        }

        val response = client.get("https://api.test.com/data")

        assertFalse(response.isSuccess)
        assertEquals(-1, response.statusCode)
        assertTrue(response.body.contains("Unable to resolve host"))
        assertEquals("retries must be bounded", 3, attempts)
    }

    @Test
    fun `does not retry once the server has responded`() = runTest {
        var attempts = 0
        val client = HttpClient(debug = false, retryBackoffMs = 0L) { _ ->
            attempts++
            mockConnection
        }
        val errorBody = """{"error":"boom"}"""
        every { mockConnection.responseCode } returns 500
        every { mockConnection.errorStream } returns ByteArrayInputStream(errorBody.toByteArray())

        val response = client.get("https://api.test.com/data")

        assertEquals(500, response.statusCode)
        assertEquals("a server response means the request was processed — retrying could duplicate it", 1, attempts)
    }
}
