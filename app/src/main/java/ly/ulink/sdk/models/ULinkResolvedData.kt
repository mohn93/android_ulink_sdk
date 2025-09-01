package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Resolved data from a ULink deep link
 */
@Serializable
data class ULinkResolvedData(
    /**
     * The slug of the link
     */
    val slug: String? = null,
    
    /**
     * iOS fallback URL
     */
    val iosFallbackUrl: String? = null,
    
    /**
     * Android fallback URL
     */
    val androidFallbackUrl: String? = null,
    
    /**
     * General fallback URL
     */
    val fallbackUrl: String? = null,
    
    /**
     * Additional parameters from the link
     */
    val parameters: JsonElement? = null,
    
    /**
     * Social media tags
     */
    val socialMediaTags: SocialMediaTags? = null,
    
    /**
     * Metadata from the link
     */
    val metadata: JsonElement? = null,
    
    /**
     * Type of the link (dynamic or unified)
     */
    val type: String? = null,
    
    /**
     * Raw data from the API response
     */
    val rawData: JsonObject? = null
) {
    companion object {
        /**
         * Creates ULinkResolvedData from JSON
         */
        fun fromJson(jsonString: String): ULinkResolvedData? {
            return try {
                val json = Json.parseToJsonElement(jsonString).jsonObject
                fromJsonObject(json)
            } catch (e: Exception) {
                null
            }
        }
        
        /**
         * Creates ULinkResolvedData from JsonObject
         */
        fun fromJsonObject(json: JsonObject): ULinkResolvedData {
            val slug = json["slug"]?.jsonPrimitive?.content
            val iosFallbackUrl = json["iosFallbackUrl"]?.jsonPrimitive?.content
            val androidFallbackUrl = json["androidFallbackUrl"]?.jsonPrimitive?.content
            val fallbackUrl = json["fallbackUrl"]?.jsonPrimitive?.content
            val type = json["type"]?.jsonPrimitive?.content
            
            // Extract parameters
            val parameters = json["parameters"]
            
            // Extract metadata
            val metadata = json["metadata"]
            
            // Extract social media tags from metadata
            val socialMediaTags = metadata?.jsonObject?.let { metadataJson ->
                SocialMediaTags(
                    ogTitle = metadataJson["ogTitle"]?.jsonPrimitive?.content,
                    ogDescription = metadataJson["ogDescription"]?.jsonPrimitive?.content,
                    ogImage = metadataJson["ogImage"]?.jsonPrimitive?.content
                )
            }
            
            return ULinkResolvedData(
                slug = slug,
                iosFallbackUrl = iosFallbackUrl,
                androidFallbackUrl = androidFallbackUrl,
                fallbackUrl = fallbackUrl,
                parameters = parameters,
                socialMediaTags = socialMediaTags,
                metadata = metadata,
                type = type,
                rawData = json
            )
        }
    }
    
    /**
     * Converts the resolved data to JSON
     */
    fun toJson(): String {
        val jsonObject = buildMap<String, Any> {
            slug?.let { put("slug", it) }
            iosFallbackUrl?.let { put("iosFallbackUrl", it) }
            androidFallbackUrl?.let { put("androidFallbackUrl", it) }
            fallbackUrl?.let { put("fallbackUrl", it) }
            parameters?.let { put("parameters", it) }
            metadata?.let { put("metadata", it) }
            type?.let { put("type", it) }
            rawData?.let { put("rawData", it) }
        }
        
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), 
            kotlinx.serialization.json.buildJsonObject {
                jsonObject.forEach { (key, value) ->
                    when (value) {
                        is String -> put(key, value)
                        is Map<*, *> -> {
                            put(key, kotlinx.serialization.json.buildJsonObject {
                                @Suppress("UNCHECKED_CAST")
                                (value as Map<String, Any>).forEach { (k, v) ->
                                    when (v) {
                                        is String -> put(k, JsonPrimitive(v))
                                        is Number -> put(k, JsonPrimitive(v.toString()))
                                        is Boolean -> put(k, JsonPrimitive(v))
                                        else -> put(k, JsonPrimitive(v.toString()))
                                    }
                                }
                            })
                        }
                        is JsonObject -> put(key, value)
                        else -> put(key, JsonPrimitive(value.toString()))
                    }
                }
            }
        )
    }
}