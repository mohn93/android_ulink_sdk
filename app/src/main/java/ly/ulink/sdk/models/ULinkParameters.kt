package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Enumeration for different types of links
 */
enum class ULinkType {
    /**
     * Dynamic links designed for app deep linking with parameters, fallback URLs, and smart app store redirects
     */
    DYNAMIC,
    
    /**
     * Simple platform-based redirects (iOS URL, Android URL, fallback URL) intended for browser handling
     */
    UNIFIED
}

/**
 * Social media tags for Open Graph metadata
 */
@Serializable
data class SocialMediaTags(
    /**
     * The title to be displayed when shared on social media
     */
    val ogTitle: String? = null,
    
    /**
     * The description to be displayed when shared on social media
     */
    val ogDescription: String? = null,
    
    /**
     * The image URL to be displayed when shared on social media
     */
    val ogImage: String? = null
) {
    /**
     * Converts the social media tags to a JSON object
     */
    fun toJson(): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        ogTitle?.let { data["ogTitle"] = it }
        ogDescription?.let { data["ogDescription"] = it }
        ogImage?.let { data["ogImage"] = it }
        
        return data
    }
}

/**
 * Dynamic link parameters
 */
data class ULinkParameters(
    /**
     * Link type: "unified" or "dynamic"
     */
    val type: String? = null,
    
    /**
     * Optional custom slug for the link
     */
    val slug: String? = null,
    
    /**
     * iOS URL for unified links (direct iOS app store or web URL)
     */
    val iosUrl: String? = null,
    
    /**
     * Android URL for unified links (direct Google Play or web URL)
     */
    val androidUrl: String? = null,
    
    /**
     * iOS fallback URL for dynamic links
     */
    val iosFallbackUrl: String? = null,
    
    /**
     * Android fallback URL for dynamic links
     */
    val androidFallbackUrl: String? = null,
    
    /**
     * Fallback URL for the link
     */
    val fallbackUrl: String? = null,
    
    /**
     * Additional parameters for the link (non-social media parameters)
     */
    val parameters: JsonElement? = null,
    
    /**
     * Social media tags for the link
     */
    val socialMediaTags: SocialMediaTags? = null,
    
    /**
     * Metadata map for social media data
     */
    val metadata: JsonElement? = null,
    
    /**
     * Domain host to use for the link (e.g., "example.com" or "subdomain.shared.ly")
     * Required to ensure consistent link generation and prevent app breakage
     * when projects have multiple domains configured.
     */
    val domain: String
) {
    companion object {
        /**
         * Factory method for creating dynamic links
         * Dynamic links are designed for in-app deep linking with parameters and smart app store redirects
         */
        fun dynamic(
            domain: String,
            slug: String? = null,
            iosFallbackUrl: String? = null,
            androidFallbackUrl: String? = null,
            fallbackUrl: String? = null,
            parameters: Map<String, Any>? = null,
            socialMediaTags: SocialMediaTags? = null,
            metadata: Map<String, Any>? = null
        ): ULinkParameters {
            return ULinkParameters(
                type = ULinkType.DYNAMIC.name.lowercase(),
                slug = slug,
                iosFallbackUrl = iosFallbackUrl,
                androidFallbackUrl = androidFallbackUrl,
                fallbackUrl = fallbackUrl,
                parameters = parameters?.let { params ->
                    buildJsonObject {
                        params.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, value.toString())
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                },
                socialMediaTags = socialMediaTags,
                metadata = metadata?.let { meta ->
                    buildJsonObject {
                        meta.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, value.toString())
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                },
                domain = domain
            )
        }
        
        /**
         * Factory method for creating unified links
         * Unified links are simple platform-based redirects intended for browser handling
         */
        fun unified(
            domain: String,
            slug: String? = null,
            iosUrl: String,
            androidUrl: String,
            fallbackUrl: String,
            parameters: Map<String, Any>? = null,
            socialMediaTags: SocialMediaTags? = null,
            metadata: Map<String, Any>? = null
        ): ULinkParameters {
            return ULinkParameters(
                type = ULinkType.UNIFIED.name.lowercase(),
                slug = slug,
                iosUrl = iosUrl,
                androidUrl = androidUrl,
                fallbackUrl = fallbackUrl,
                parameters = parameters?.let { params ->
                    buildJsonObject {
                        params.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, value.toString())
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                },
                socialMediaTags = socialMediaTags,
                metadata = metadata?.let { meta ->
                    buildJsonObject {
                        meta.forEach { (key, value) ->
                            when (value) {
                                is String -> put(key, value)
                                is Number -> put(key, value.toString())
                                is Boolean -> put(key, value)
                                else -> put(key, value.toString())
                            }
                        }
                    }
                },
                domain = domain
            )
        }
    }
    
    /**
     * Converts the parameters to a JSON map
     */
    fun toJson(): Map<String, Any> {
        val data = mutableMapOf<String, Any>()
        
        type?.let { data["type"] = it }
        slug?.let { data["slug"] = it }
        iosUrl?.let { data["iosUrl"] = it }
        androidUrl?.let { data["androidUrl"] = it }
        iosFallbackUrl?.let { data["iosFallbackUrl"] = it }
        androidFallbackUrl?.let { data["androidFallbackUrl"] = it }
        fallbackUrl?.let { data["fallbackUrl"] = it }
        data["domain"] = domain
        
        // Handle regular parameters (non-social media)
        val regularParameters = mutableMapOf<String, Any>()
        parameters?.jsonObject?.forEach { (key, value) ->
            if (!key.startsWith("og") && !isSocialMediaParameter(key)) {
                regularParameters[key] = value.jsonPrimitive.content
            }
        }
        
        if (regularParameters.isNotEmpty()) {
            data["parameters"] = regularParameters
        }
        
        // Handle metadata (social media data)
        val metadataMap = mutableMapOf<String, Any>()
        
        // Add social media tags from socialMediaTags object
        socialMediaTags?.let {
            metadataMap.putAll(it.toJson())
        }
        
        // Add social media parameters from parameters map
        parameters?.jsonObject?.forEach { (key, value) ->
            if (key.startsWith("og") || isSocialMediaParameter(key)) {
                metadataMap[key] = value.jsonPrimitive.content
            }
        }
        
        // Add explicit metadata
        metadata?.jsonObject?.forEach { (key, value) ->
            metadataMap[key] = value.jsonPrimitive.content
        }
        
        if (metadataMap.isNotEmpty()) {
            data["metadata"] = metadataMap
        }
        
        return data
    }
    
    /**
     * Helper method to identify social media parameters
     */
    private fun isSocialMediaParameter(key: String): Boolean {
        val socialMediaKeys = listOf(
            "ogTitle",
            "ogDescription",
            "ogImage",
            "ogSiteName",
            "ogType",
            "ogUrl",
            "twitterCard",
            "twitterSite",
            "twitterCreator",
            "twitterTitle",
            "twitterDescription",
            "twitterImage"
        )
        return socialMediaKeys.contains(key)
    }
}