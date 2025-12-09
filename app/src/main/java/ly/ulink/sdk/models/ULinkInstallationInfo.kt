package ly.ulink.sdk.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Contains information about the current installation, including reinstall detection data.
 * 
 * This data is returned from the bootstrap process and indicates whether the current
 * installation was detected as a reinstall of a previous installation.
 */
@Serializable
data class ULinkInstallationInfo(
    /**
     * The unique identifier for this installation (client-generated UUID)
     */
    val installationId: String,
    
    /**
     * Whether this installation was detected as a reinstall
     */
    val isReinstall: Boolean = false,
    
    /**
     * The ID of the previous installation if this is a reinstall.
     * Null if this is a fresh install or reinstall detection is not available.
     */
    val previousInstallationId: String? = null,
    
    /**
     * Timestamp when the reinstall was detected (ISO 8601 format)
     */
    val reinstallDetectedAt: String? = null,
    
    /**
     * The persistent device ID used for reinstall detection
     */
    val persistentDeviceId: String? = null
) {
    companion object {
        /**
         * Creates a ULinkInstallationInfo from a JSON response
         */
        fun fromJson(json: JsonObject, installationId: String): ULinkInstallationInfo {
            return ULinkInstallationInfo(
                installationId = installationId,
                isReinstall = json["isReinstall"]?.toString()?.removeSurrounding("\"")?.toBoolean() ?: false,
                previousInstallationId = json["previousInstallationId"]?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" },
                reinstallDetectedAt = json["reinstallDetectedAt"]?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" },
                persistentDeviceId = json["persistentDeviceId"]?.toString()?.removeSurrounding("\"")?.takeIf { it != "null" }
            )
        }
        
        /**
         * Creates a fresh installation info (not a reinstall)
         */
        fun freshInstall(installationId: String, persistentDeviceId: String? = null): ULinkInstallationInfo {
            return ULinkInstallationInfo(
                installationId = installationId,
                isReinstall = false,
                persistentDeviceId = persistentDeviceId
            )
        }
    }
}
