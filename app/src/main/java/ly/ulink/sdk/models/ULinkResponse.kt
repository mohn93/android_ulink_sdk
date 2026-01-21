package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Response from the ULink API for dynamic link creation
 */
@Serializable
data class ULinkResponse(
    /**
     * Whether the request was successful
     */
    val success: Boolean,
    
    /**
     * The generated URL (if successful)
     */
    val url: String? = null,
    
    /**
     * Error message (if unsuccessful)
     */
    val error: String? = null,
    
    /**
     * Raw response data
     */
    val data: JsonObject? = null
) {
    companion object {
        /**
         * Creates a successful response
         */
        @JvmStatic
        @JvmOverloads
        fun success(url: String, data: JsonObject? = null): ULinkResponse {
            return ULinkResponse(
                success = true,
                url = url,
                data = data
            )
        }

        /**
         * Creates an error response
         */
        @JvmStatic
        @JvmOverloads
        fun error(message: String, data: JsonObject? = null): ULinkResponse {
            return ULinkResponse(
                success = false,
                error = message,
                data = data
            )
        }

        /**
         * Creates a response from JSON
         */
        @JvmStatic
        fun fromJson(jsonString: String): ULinkResponse {
            return try {
                val json = Json.parseToJsonElement(jsonString).jsonObject
                val success = json["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                val url = json["url"]?.jsonPrimitive?.content
                val error = json["error"]?.jsonPrimitive?.content
                
                ULinkResponse(
                    success = success,
                    url = url,
                    error = error,
                    data = json
                )
            } catch (e: Exception) {
                error("Failed to parse response: ${e.message}")
            }
        }
    }
    
    /**
     * Converts the response to JSON
     */
    fun toJson(): String {
        val jsonObject = buildMap<String, Any> {
            put("success", success)
            url?.let { put("url", it) }
            error?.let { put("error", it) }
            data?.let { put("data", it) }
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), 
            kotlinx.serialization.json.buildJsonObject {
                jsonObject.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, JsonPrimitive(value))
                        is Boolean -> put(key, JsonPrimitive(value))
                        is JsonObject -> put(key, value)
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            }
        )
    }
}