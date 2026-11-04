package ly.ulink.sdk.listeners

import ly.ulink.sdk.models.ULinkInstallationInfo

/**
 * Listener for reinstall detection events.
 * Java-friendly alternative to Flow subscription.
 *
 * This listener is triggered when the SDK detects that the app was previously
 * installed (reinstall scenario). This is useful for tracking user retention
 * and understanding reinstallation patterns.
 *
 * Example usage in Java:
 * ```java
 * ulink.setOnReinstallListener(info -> {
 *     Log.d("ULink", "Reinstall detected! Previous installation ID: " + info.getPreviousInstallationId());
 *     // Track reinstall event in analytics
 * });
 * ```
 *
 * Example usage in Kotlin:
 * ```kotlin
 * ulink.setOnReinstallListener { info ->
 *     Log.d("ULink", "Reinstall detected! Previous installation ID: ${info.previousInstallationId}")
 *     // Track reinstall event in analytics
 * }
 * ```
 */
fun interface OnReinstallListener {
    /**
     * Called when a reinstall is detected.
     *
     * @param info The installation information including the previous installation ID and detection time
     */
    fun onReinstallDetected(info: ULinkInstallationInfo)
}
