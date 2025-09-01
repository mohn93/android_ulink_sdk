package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Session data for tracking user sessions
 */
@Serializable
data class ULinkSession(
    /**
     * Unique installation ID
     */
    val installationId: String,
    
    /**
     * Network type (WiFi, Cellular, etc.)
     */
    val networkType: String? = null,
    
    /**
     * Device orientation (Portrait, Landscape)
     */
    val deviceOrientation: String? = null,
    
    /**
     * Battery level (0-100)
     */
    val batteryLevel: Int? = null,
    
    /**
     * Whether device is charging
     */
    val isCharging: Boolean? = null,
    
    /**
     * Additional metadata
     */
    val metadata: JsonElement? = null
) {
    /**
     * Converts the session to JSON
     */
    fun toJson(): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        data["installationId"] = installationId
        networkType?.let { data["networkType"] = it }
        deviceOrientation?.let { data["deviceOrientation"] = it }
        batteryLevel?.let { data["batteryLevel"] = it }
        isCharging?.let { data["isCharging"] = it }
        metadata?.let { data["metadata"] = it }
        
        return data
    }
    
    companion object {
        /**
         * Creates ULinkSession from JSON
         */
        fun fromJson(jsonString: String): ULinkSession? {
            return try {
                val json = Json.parseToJsonElement(jsonString).jsonObject
                fromJsonObject(json)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Creates ULinkSession from JsonObject
         */
        fun fromJsonObject(json: JsonObject): ULinkSession {
            val installationId = json["installationId"]?.jsonPrimitive?.content ?: ""
            val networkType = json["networkType"]?.jsonPrimitive?.content
            val deviceOrientation = json["deviceOrientation"]?.jsonPrimitive?.content
            val batteryLevel = json["batteryLevel"]?.jsonPrimitive?.content?.toIntOrNull()
            val isCharging = json["isCharging"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            
            val metadata = json["metadata"]
            
            return ULinkSession(
                installationId = installationId,
                networkType = networkType,
                deviceOrientation = deviceOrientation,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                metadata = metadata
            )
        }
    }
}

/**
 * Response from session start API
 */
@Serializable
data class ULinkSessionResponse(
    /**
     * Whether the session start was successful
     */
    val success: Boolean,
    
    /**
     * The session ID (if successful)
     */
    val sessionId: String? = null,
    
    /**
     * Error message (if unsuccessful)
     */
    val error: String? = null
) {
    companion object {
        /**
         * Creates a successful session response
         */
        fun success(sessionId: String): ULinkSessionResponse {
            return ULinkSessionResponse(
                success = true,
                sessionId = sessionId
            )
        }
        
        /**
         * Creates an error session response
         */
        fun error(message: String): ULinkSessionResponse {
            return ULinkSessionResponse(
                success = false,
                error = message
            )
        }
        
        /**
         * Creates a response from JSON
         */
        fun fromJson(jsonString: String): ULinkSessionResponse {
            return try {
                val json = Json.parseToJsonElement(jsonString).jsonObject
                val success = json["success"]?.jsonPrimitive?.content?.toBoolean() ?: false
                val sessionId = json["sessionId"]?.jsonPrimitive?.content
                val error = json["error"]?.jsonPrimitive?.content
                
                ULinkSessionResponse(
                    success = success,
                    sessionId = sessionId,
                    error = error
                )
            } catch (e: Exception) {
                error("Failed to parse session response: ${e.message}")
            }
        }
    }
}