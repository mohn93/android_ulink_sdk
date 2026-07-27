package ly.ulink.sdk.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.URL
import java.net.UnknownHostException

/**
 * HTTP client for making API requests
 */
class HttpClient(
    private val debug: Boolean = false,
    // Declared before connectionFactory so the factory stays the trailing
    // parameter and existing `HttpClient(debug) { url -> ... }` call sites
    // keep working unchanged.
    private val retryBackoffMs: Long = INITIAL_BACKOFF_MS,
    private val connectionFactory: ((String) -> HttpURLConnection)? = null
) {

    companion object {
        private const val TAG = "ULink-HttpClient"
        private const val TIMEOUT_CONNECT = 10000 // 10 seconds
        private const val TIMEOUT_READ = 30000 // 30 seconds

        /** Total attempts (1 initial + 2 retries) for transient pre-send failures. */
        private const val MAX_ATTEMPTS = 3

        /** Base delay for exponential backoff; doubles per retry. */
        private const val INITIAL_BACKOFF_MS = 300L
    }

    /**
     * Whether a failure proves the request never reached the server, and is
     * therefore safe to retry.
     *
     * Deliberately narrow: a read timeout or a server response means the request
     * may already have been processed, and retrying it could duplicate a side
     * effect (a second session, a second installation). DNS and connect failures
     * happen before anything is sent, so replaying them is always safe — and
     * that is exactly the transient case that used to fail permanently.
     */
    private fun isRetryable(e: Exception): Boolean =
        e is UnknownHostException || e is ConnectException || e is NoRouteToHostException
    
    /**
     * Makes a GET request
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse = withContext(Dispatchers.IO) {
        makeRequest("GET", url, null, headers)
    }
    
    /**
     * Makes a POST request
     */
    suspend fun post(
        url: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse = withContext(Dispatchers.IO) {
        makeRequest("POST", url, body, headers)
    }
    
    /**
     * Makes a POST request with JSON body
     */
    suspend fun postJson(
        url: String,
        jsonBody: Map<String, Any>,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse = withContext(Dispatchers.IO) {
        val jsonString = Json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                jsonBody.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Number -> put(key, value.toString())
                        is Boolean -> put(key, value)
                        is JsonElement -> put(key, value)
                        is Map<*, *> -> {
                            put(key, buildJsonObject {
                                @Suppress("UNCHECKED_CAST")
                                (value as Map<String, Any>).forEach { (k, v) ->
                                    when (v) {
                                        is String -> put(k, v)
                                        is Number -> put(k, v.toString())
                                        is Boolean -> put(k, v)
                                        else -> put(k, v.toString())
                                    }
                                }
                            })
                        }
                        else -> put(key, value.toString())
                    }
                }
            }
        )
        
        val requestHeaders = headers.toMutableMap()
        requestHeaders["Content-Type"] = "application/json"
        
        makeRequest("POST", url, jsonString, requestHeaders)
    }
    
    /**
     * Makes an HTTP request, retrying transient pre-send failures (see
     * [isRetryable]) with exponential backoff.
     */
    private suspend fun makeRequest(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>
    ): HttpResponse {
        var backoff = retryBackoffMs
        var attempt = 0

        while (true) {
            attempt++
            try {
                return executeOnce(method, url, body, headers)
            } catch (e: Exception) {
                val canRetry = isRetryable(e) && attempt < MAX_ATTEMPTS
                if (debug) {
                    Log.e(TAG, "Request failed (attempt $attempt/$MAX_ATTEMPTS, retrying=$canRetry)", e)
                }
                if (!canRetry) {
                    return HttpResponse(
                        statusCode = -1,
                        body = e.message ?: "Unknown error",
                        isSuccess = false
                    )
                }
                if (backoff > 0) delay(backoff)
                backoff *= 2
            }
        }
    }

    /**
     * Performs a single HTTP attempt. Throws on network failure so [makeRequest]
     * can decide whether replaying is safe.
     */
    private fun executeOnce(
        method: String,
        url: String,
        body: String?,
        headers: Map<String, String>
    ): HttpResponse {
        var connection: HttpURLConnection? = null

        try {
            if (debug) {
                Log.d(TAG, "Making $method request to: $url")
                if (body != null) {
                    Log.d(TAG, "Request body: $body")
                }
            }
            
            connection = connectionFactory?.invoke(url) ?: (URL(url).openConnection() as HttpURLConnection)
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_CONNECT
            connection.readTimeout = TIMEOUT_READ
            connection.doInput = true
            
            // Set headers
            headers.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            // Set body for POST requests
            if (method == "POST" && body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body)
                    writer.flush()
                }
            }
            
            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                    reader.readText()
                }
            } else {
                BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use { reader ->
                    reader.readText()
                }
            }
            
            // Capture response headers
            val responseHeaders = mutableMapOf<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && values.isNotEmpty()) {
                    responseHeaders[key.lowercase()] = values.first()
                }
            }
            
            if (debug) {
                Log.d(TAG, "Response code: $responseCode")
                Log.d(TAG, "Response body: $responseBody")
                Log.d(TAG, "Response headers: $responseHeaders")
            }
            
            return HttpResponse(
                statusCode = responseCode,
                body = responseBody,
                isSuccess = responseCode in 200..299,
                headers = responseHeaders
            )
            
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * HTTP response data class
 */
data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val isSuccess: Boolean,
    val headers: Map<String, String> = emptyMap()
) {
    /**
     * Parses the response body as JSON
     */
    fun parseJson(): JsonObject? {
        return try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (e: Exception) {
            null
        }
    }
}