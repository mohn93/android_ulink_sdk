package ly.ulink.sdk.models

import kotlinx.serialization.Serializable

/**
 * Represents a log entry from the ULink SDK
 */
@Serializable
data class ULinkLogEntry(
    /**
     * Log level: "debug", "info", "warning", "error"
     */
    val level: String,
    
    /**
     * Log tag/source
     */
    val tag: String,
    
    /**
     * Log message
     */
    val message: String,
    
    /**
     * Timestamp in milliseconds since epoch
     */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val LEVEL_DEBUG = "debug"
        const val LEVEL_INFO = "info"
        const val LEVEL_WARNING = "warning"
        const val LEVEL_ERROR = "error"
    }
}
