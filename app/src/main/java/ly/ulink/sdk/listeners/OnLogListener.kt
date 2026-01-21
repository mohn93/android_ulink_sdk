package ly.ulink.sdk.listeners

import ly.ulink.sdk.models.ULinkLogEntry

/**
 * Listener for SDK log events (only active when debug mode is enabled).
 * Java-friendly alternative to Flow subscription.
 *
 * This listener provides access to internal SDK log messages for debugging purposes.
 * It is only active when the ULinkConfig has debug = true. Use this to integrate
 * ULink logging with your app's logging system.
 *
 * Example usage in Java:
 * ```java
 * ulink.setOnLogListener(entry -> {
 *     Log.d("ULink", entry.getMessage());
 *     // Forward to your logging system
 * });
 * ```
 *
 * Example usage in Kotlin:
 * ```kotlin
 * ulink.setOnLogListener { entry ->
 *     Log.d("ULink", entry.message)
 *     // Forward to your logging system
 * }
 * ```
 */
fun interface OnLogListener {
    /**
     * Called when a log event occurs (only when debug mode is enabled).
     *
     * @param entry The log entry containing message, level, and timestamp
     */
    fun onLog(entry: ULinkLogEntry)
}
