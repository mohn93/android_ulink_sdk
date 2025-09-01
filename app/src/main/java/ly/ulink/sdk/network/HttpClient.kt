package ly.ulink.sdk.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP client for making API requests
 */
class HttpClient(
    private val debug: Boolean = false,
    private val connectionFactory: ((String) -> HttpURLConnection)? = null
) {
    
    companion object {
        private const val TAG = "ULink-HttpClient"
        private const val TIMEOUT_CONNECT = 10000 // 10 seconds
        private const val TIMEOUT_READ = 30000 // 30 seconds
    }
    
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
     * Makes an HTTP request
     */
    private fun makeRequest(
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
            
        } catch (e: Exception) {
            if (debug) {
                Log.e(TAG, "Request failed", e)
            }
            return HttpResponse(
                statusCode = -1,
                body = e.message ?: "Unknown error",
                isSuccess = false
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