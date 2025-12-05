package ly.ulink.sdk.models

/**
 * Configuration for the ULink SDK
 */
data class ULinkConfig(
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