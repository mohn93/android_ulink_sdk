package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Installation data for tracking app installations
 */
@Serializable
data class ULinkInstallation(
    /**
     * Unique installation ID
     */
    val installationId: String,
    
    /**
     * Device ID
     */
    val deviceId: String? = null,
    
    /**
     * Device model
     */
    val deviceModel: String? = null,
    
    /**
     * Operating system name
     */
    val osName: String? = null,
    
    /**
     * Operating system version
     */
    val osVersion: String? = null,
    
    /**
     * App version
     */
    val appVersion: String? = null,
    
    /**
     * Device language
     */
    val language: String? = null,
    
    /**
     * Device timezone
     */
    val timezone: String? = null,
    
    /**
     * Additional metadata
     */
    val metadata: JsonElement? = null
) {
    /**
     * Converts the installation to JSON
     */
    fun toJson(): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        data["installationId"] = installationId
        deviceId?.let { data["deviceId"] = it }
        deviceModel?.let { data["deviceModel"] = it }
        osName?.let { data["osName"] = it }
        osVersion?.let { data["osVersion"] = it }
        appVersion?.let { data["appVersion"] = it }
        language?.let { data["language"] = it }
        timezone?.let { data["timezone"] = it }
        metadata?.let { data["metadata"] = it }
        
        return data
    }
    
    companion object {
        /**
         * Creates ULinkInstallation from JSON
         */
        fun fromJson(jsonString: String): ULinkInstallation? {
            return try {
                val json = Json.parseToJsonElement(jsonString).jsonObject
                fromJsonObject(json)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Creates ULinkInstallation from JsonObject
         */
        fun fromJsonObject(json: JsonObject): ULinkInstallation {
            val installationId = json["installationId"]?.jsonPrimitive?.content ?: ""
            val deviceId = json["deviceId"]?.jsonPrimitive?.content
            val deviceModel = json["deviceModel"]?.jsonPrimitive?.content
            val osName = json["osName"]?.jsonPrimitive?.content
            val osVersion = json["osVersion"]?.jsonPrimitive?.content
            val appVersion = json["appVersion"]?.jsonPrimitive?.content
            val language = json["language"]?.jsonPrimitive?.content
            val timezone = json["timezone"]?.jsonPrimitive?.content
            
            val metadata = json["metadata"]
            
            return ULinkInstallation(
                installationId = installationId,
                deviceId = deviceId,
                deviceModel = deviceModel,
                osName = osName,
                osVersion = osVersion,
                appVersion = appVersion,
                language = language,
                timezone = timezone,
                metadata = metadata
            )
        }
    }
}