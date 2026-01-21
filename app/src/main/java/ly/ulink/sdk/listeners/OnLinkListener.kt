package ly.ulink.sdk.listeners

import ly.ulink.sdk.models.ULinkResolvedData

/**
 * Listener for receiving deep link resolution events.
 * Java-friendly alternative to Flow subscription.
 *
 * This listener is triggered when a dynamic deep link is resolved, providing
 * the parsed link data including parameters, metadata, and routing information.
 *
 * Example usage in Java:
 * ```java
 * ulink.setOnLinkListener(data -> {
 *     Log.d("ULink", "Link received: " + data.getSlug());
 *     // Handle navigation based on data
 * });
 * ```
 *
 * Example usage in Kotlin:
 * ```kotlin
 * ulink.setOnLinkListener { data ->
 *     Log.d("ULink", "Link received: ${data.slug}")
 *     // Handle navigation based on data
 * }
 * ```
 */
fun interface OnLinkListener {
    /**
     * Called when a deep link is resolved.
     *
     * @param data The resolved link data containing slug, parameters, and metadata
     */
    fun onLinkReceived(data: ULinkResolvedData)
}
