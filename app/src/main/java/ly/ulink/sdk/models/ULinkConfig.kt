package ly.ulink.sdk.models

/**
 * Configuration for the ULink SDK.
 *
 * @param apiKey The API key for the ULink service (required)
 * @param baseUrl The base URL for the ULink API (default: "https://api.ulink.ly")
 * @param debug Whether to enable debug logging (default: false)
 * @param enableDeepLinkIntegration Whether to automatically handle deep links (default: true)
 * @param persistLastLinkData Whether to persist the last resolved link (default: true)
 * @param lastLinkTimeToLiveSeconds TTL for persisted links in seconds (default: 24 hours)
 * @param clearLastLinkOnRead Whether to clear persisted link after first read (default: false)
 * @param redactAllParametersInLastLink Whether to redact all params when persisting (default: false)
 * @param redactedParameterKeysInLastLink Specific keys to redact (default: empty list)
 * @param autoCheckDeferredLink Whether to auto-check deferred deep links (default: true)
 */
data class ULinkConfig @JvmOverloads constructor(
    /**
     * The API key for the ULink service
     */
    val apiKey: String,
    
    /**
     * The base URL for the ULink API
     */
    val baseUrl: String = "https://api.ulink.ly",
    
    /**
     * Whether to use debug mode
     */
    val debug: Boolean = false,
    
    /**
     * Whether to enable automatic deep link integration
     * When enabled, the SDK will automatically handle incoming intents and process deep links
     */
    val enableDeepLinkIntegration: Boolean = true,
    
    /** Whether to persist the last resolved link for later retrieval */
    val persistLastLinkData: Boolean = true,
    /** Time-to-live for persisted last link in seconds (0 or negative to disable TTL) */
    val lastLinkTimeToLiveSeconds: Long = 24 * 60 * 60,
    /** If true, clears the persisted last link after it is read the first time */
    val clearLastLinkOnRead: Boolean = false,
    /** If true, do not persist parameters/metadata for the last link */
    val redactAllParametersInLastLink: Boolean = false,
    /** Keys to redact from parameters/metadata when persisting the last link */
    val redactedParameterKeysInLastLink: List<String> = emptyList(),
    
    /**
     * If true, automatically checks for deferred deep links on first app launch
     * If false, developers must manually call checkDeferredLink() when ready
     */
    val autoCheckDeferredLink: Boolean = true
)