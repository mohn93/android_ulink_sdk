package ly.ulink.sdk.listeners

import ly.ulink.sdk.models.ULinkResolvedData

/**
 * Listener for receiving unified link resolution events.
 * Java-friendly alternative to Flow subscription.
 *
 * This listener is triggered when a unified link is resolved. Unified links
 * are platform-specific redirects designed for "open in browser" scenarios
 * where the app can choose whether to handle the link in-app or open Safari/Chrome.
 *
 * Example usage in Java:
 * ```java
 * ulink.setOnUnifiedLinkListener(data -> {
 *     Log.d("ULink", "Unified link: " + data.getSlug());
 *     // Optionally open in browser or handle in-app
 * });
 * ```
 *
 * Example usage in Kotlin:
 * ```kotlin
 * ulink.setOnUnifiedLinkListener { data ->
 *     Log.d("ULink", "Unified link: ${data.slug}")
 *     // Optionally open in browser or handle in-app
 * }
 * ```
 */
fun interface OnUnifiedLinkListener {
    /**
     * Called when a unified link is resolved.
     *
     * @param data The resolved link data containing platform-specific URLs and metadata
     */
    fun onUnifiedLinkReceived(data: ULinkResolvedData)
}
