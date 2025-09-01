package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Response from tracking an installation
 */
@Serializable
data class ULinkInstallationResponse(
    /**
     * Whether the operation was successful
     */
    val success: Boolean,
    
    /**
     * The installation ID
     */
    val installationId: String? = null,
    
    /**
     * Whether this is a new installation
     */
    val isNew: Boolean? = null,
    
    /**
     * Error message if unsuccessful
     */
    val error: String? = null
) {
    companion object {
        /**
         * Creates a successful response
         */
        fun success(installationId: String, isNew: Boolean): ULinkInstallationResponse {
            return ULinkInstallationResponse(
                success = true,
                installationId = installationId,
                isNew = isNew
            )
        }
        
        /**
         * Creates an error response
         */
        fun error(error: String): ULinkInstallationResponse {
            return ULinkInstallationResponse(
                success = false,
                error = error
            )
        }
        
        /**
         * Creates a response from JSON
         */
        fun fromJson(json: Map<String, Any>): ULinkInstallationResponse {
            if (json.containsKey("error")) {
                return error(json["error"] as? String ?: "Unknown error")
            }
            
            return ULinkInstallationResponse(
                success = json["success"] as? Boolean ?: false,
                installationId = json["installationId"] as? String,
                isNew = json["isNew"] as? Boolean
            )
        }
    }
}