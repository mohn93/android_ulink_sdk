package ly.ulink.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.*
import ly.ulink.sdk.models.*
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.utils.DeviceInfoUtils
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Main class for the ULink Android SDK
 * 
 * This class provides functionality for:
 * - Creating dynamic and unified links
 * - Handling deep links and app links
 * - Session management with automatic lifecycle handling
 * - Installation tracking
 * - Link resolution and processing
 */
class ULink private constructor(
    private val context: Context,
    private val config: ULinkConfig,
    private val injectedHttpClient: HttpClient? = null
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    companion object {
        private const val TAG = "ULink"
        private const val PREFS_NAME = "ulink_prefs"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_INSTALLATION_TOKEN = "installation_token"
        private const val KEY_LAST_LINK_DATA = "last_link_data"
        private const val KEY_LAST_LINK_SAVED_AT = "last_link_saved_at"
        private const val SDK_VERSION = "1.0.0"
        
        @Volatile
        private var INSTANCE: ULink? = null
        
        /**
         * Initialize the ULink SDK
         * 
         * This method initializes the ULink SDK and performs the following actions:
         * 1. Creates a singleton instance with the provided configuration
         * 2. Retrieves or generates a unique installation ID
         * 3. Tracks the installation with the server
         * 4. Starts a new session
         * 5. Registers lifecycle observer for automatic session management
         * 
         * It should be called when your app starts, typically in your Application class
         * or in your main Activity's onCreate method.
         * 
         * Example:
         * ```kotlin
         * class MyApplication : Application() {
         *     override fun onCreate() {
         *         super.onCreate()
         *         ULink.initialize(
         *             context = this,
         *             config = ULinkConfig(
         *                 apiKey = "your_api_key",
         *                 baseUrl = "https://api.ulink.ly",
         *                 debug = true
         *             )
         *         )
         *     }
         * }
         * ```
         */
        fun initialize(
            context: Context,
            config: ULinkConfig,
            httpClient: HttpClient? = null
        ): ULink {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ULink(context.applicationContext, config, httpClient).also {
                    INSTANCE = it
                    it.setup()
                }
            }
        }

        fun createTestInstance(context: Context, config: ULinkConfig, httpClient: HttpClient): ULink {
            return initialize(context, config, httpClient)
        }
        
        /**
         * Get the singleton instance
         * @throws IllegalStateException if the SDK is not initialized
         */
        fun getInstance(): ULink {
            return INSTANCE ?: throw IllegalStateException(
                "ULink SDK not initialized. Call ULink.initialize() first."
            )
        }
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = injectedHttpClient ?: HttpClient(config.debug)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Installation token
    private var installationToken: String? = null
    
    // Session management
    private var currentSessionId: String? = null
    
    /**
     * Current session state
     */
    private var sessionState: SessionState = SessionState.IDLE
    
    /**
     * Future to track session initialization completion
     */
    private var sessionFuture: CompletableFuture<Void>? = null
    
    // Deep link streams
    private val _dynamicLinkStream = MutableSharedFlow<ULinkResolvedData>()
    val dynamicLinkStream: SharedFlow<ULinkResolvedData> = _dynamicLinkStream.asSharedFlow()
    
    private val _unifiedLinkStream = MutableSharedFlow<ULinkResolvedData>()
    val unifiedLinkStream: SharedFlow<ULinkResolvedData> = _unifiedLinkStream.asSharedFlow()
    
    /**
     * Flow of dynamic link events
     */
    val onLink: SharedFlow<ULinkResolvedData> = _dynamicLinkStream.asSharedFlow()
    
    /**
     * Flow of unified link events (for external redirects)
     */
    val onUnifiedLink: SharedFlow<ULinkResolvedData> = _unifiedLinkStream.asSharedFlow()
    
    // Initial deep link data
    private var initialUri: Uri? = null
    private var lastLinkData: ULinkResolvedData? = null
    
    /**
     * Sets up the SDK
     */
    private fun setup() {
        // Register lifecycle observer
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        
        // Register activity lifecycle callbacks for automatic deep link integration
        if (config.enableDeepLinkIntegration) {
            (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(this)
        }
        
        // Load installation token from storage
        loadInstallationToken()
        
        // Generate installation ID if not exists
        if (getInstallationId() == null) {
            generateInstallationId()
        }
        
        // Bootstrap installation and session with the server
        scope.launch {
            bootstrap()
            // Note: bootstrap() already handles session creation, no need for additional startSession() call
        }
        
        // Load last link data
        loadLastLinkData()
        
        if (config.debug) {
            Log.d(TAG, "ULink SDK initialized with API key: ${config.apiKey}")
            Log.d(TAG, "Installation ID: ${getInstallationId()}")
            Log.d(TAG, "Installation Token: ${if (installationToken != null) "[LOADED]" else "[NOT FOUND]"}")
        }
    }
    
    /**
     * Called when app comes to foreground
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (config.debug) {
            Log.d(TAG, "App started - starting session")
        }
        scope.launch {
            // Only start session if not already active or initializing
            if (sessionState == SessionState.IDLE || sessionState == SessionState.FAILED) {
                startSession()
            }
        }
    }
    
    /**
     * Called when app goes to background
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (config.debug) {
            Log.d(TAG, "App stopped - ending session")
        }
        scope.launch {
            // Only end session if currently active
            if (sessionState == SessionState.ACTIVE) {
                endSession()
            }
        }
    }
    
    /**
     * Gets or generates installation ID
     */
    fun getInstallationId(): String? {
        return sharedPreferences.getString(KEY_INSTALLATION_ID, null)
    }
    
    /**
     * Generates a new installation ID
     */
    private fun generateInstallationId() {
        val installationId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_INSTALLATION_ID, installationId).apply()
        
        if (config.debug) {
            Log.d(TAG, "Generated new installation ID: $installationId")
        }
    }
    
    /**
     * Loads installation token from SharedPreferences
     */
    private fun loadInstallationToken() {
        try {
            installationToken = sharedPreferences.getString(KEY_INSTALLATION_TOKEN, null)
            if (config.debug && installationToken != null) {
                Log.d(TAG, "Loaded installation token from storage")
            }
        } catch (e: Exception) {
            if (config.debug) {
                Log.e(TAG, "Failed to load installation token", e)
            }
        }
    }
    
    /**
     * Saves installation token
     */
    private fun saveInstallationToken(token: String) {
        installationToken = token
        try {
            sharedPreferences.edit().putString(KEY_INSTALLATION_TOKEN, token).apply()
            if (config.debug) {
                Log.d(TAG, "Saved installation token to storage")
            }
        } catch (e: Exception) {
            if (config.debug) {
                Log.e(TAG, "Failed to save installation token", e)
            }
        }
    }
    
    /**
     * Gets the installation token
     */
    private fun getInstallationToken(): String? {
        return installationToken ?: sharedPreferences.getString(KEY_INSTALLATION_TOKEN, null)
    }
    
    /**
     * Bootstrap installation and session via single API call
     */
    private suspend fun bootstrap() {
        return withContext(Dispatchers.IO) {
            try {
                val installationId = getInstallationId() ?: return@withContext
                
                if (config.debug) {
                    Log.d(TAG, "Bootstrapping installation and session")
                }
                
                val bootstrapData = buildBootstrapBodyMap()
                val url = "${config.baseUrl}/sdk/bootstrap"
                val headers = mutableMapOf(
                    "X-App-Key" to config.apiKey,
                    "Content-Type" to "application/json",
                    "X-ULink-Client" to "sdk-android",
                    "X-ULink-Client-Version" to "1.0.0", // TODO: Get from build config
                    "X-ULink-Client-Platform" to "android"
                )
                
                // Add installation ID if available
                if (installationId.isNotEmpty()) {
                    headers["X-Installation-Id"] = installationId
                }
                
                // Add device ID if available
                DeviceInfoUtils.getDeviceId(context)?.let {
                    headers["X-Device-Id"] = it
                }
                
                val response = httpClient.postJson(url, bootstrapData, headers)
                
                if (response.isSuccess) {
                    val json = response.parseJson()
                    if (json != null) {
                        // Handle installation token from header or body
                        val tokenFromHeader = response.headers["x-installation-token"]
                        val tokenFromBody = json["installationToken"]?.toString()?.removeSurrounding("\"")
                        val token = tokenFromHeader ?: tokenFromBody
                        
                        if (!token.isNullOrEmpty()) {
                            saveInstallationToken(token)
                            if (config.debug) {
                                Log.d(TAG, "Received and saved installation token")
                            }
                        }
                        
                        // Handle session ID
                        val sessionId = json["sessionId"]?.toString()?.removeSurrounding("\"")
                        if (!sessionId.isNullOrEmpty()) {
                            currentSessionId = sessionId
                            if (config.debug) {
                                Log.d(TAG, "Bootstrap ensured session: $sessionId")
                            }
                        }
                        
                        if (config.debug) {
                            Log.d(TAG, "Bootstrap completed successfully")
                        }
                    }
                } else {
                    if (config.debug) {
                        Log.e(TAG, "Bootstrap failed: HTTP ${response.statusCode}: ${response.body}")
                    }
                }
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e(TAG, "Bootstrap error", e)
                }
            }
        }
    }
    
    /**
     * Build bootstrap request body
     */
    private fun buildBootstrapBodyMap(): Map<String, Any> {
        val installationId = getInstallationId()
        val deviceInfo = mutableMapOf<String, Any>()
        
        // Get comprehensive device information using the updated DeviceInfoUtils
        val completeDeviceInfo = DeviceInfoUtils.getCompleteDeviceInfo(context)
        // Filter out null values to match Map<String, Any> type
        deviceInfo.putAll(completeDeviceInfo.filterValues { it != null } as Map<String, Any>)
        
        // Override installation ID if available
        installationId?.let { deviceInfo["installationId"] = it }
        
        // Add metadata with client information
        deviceInfo["metadata"] = mapOf(
            "client" to mapOf(
                "type" to "sdk-android",
                "version" to SDK_VERSION,
                "platform" to "android"
            )
        )
        
        return deviceInfo
    }
    
    private fun buildBootstrapBody(): String {
        val deviceInfo = buildBootstrapBodyMap()
        
        return buildJsonObject {
            deviceInfo.forEach { (key, value) ->
                when (value) {
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    is Map<*, *> -> {
                        put(key, buildJsonObject {
                            @Suppress("UNCHECKED_CAST")
                            (value as Map<String, Any>).forEach { (k, v) ->
                                when (v) {
                                    is String -> put(k, v)
                                    is Number -> put(k, v)
                                    is Boolean -> put(k, v)
                                    is Map<*, *> -> {
                                        put(k, buildJsonObject {
                                            @Suppress("UNCHECKED_CAST")
                                            (v as Map<String, Any>).forEach { (k2, v2) ->
                                                when (v2) {
                                                    is String -> put(k2, v2)
                                                    is Number -> put(k2, v2)
                                                    is Boolean -> put(k2, v2)
                                                    is Map<*, *> -> {
                                                        put(k, buildJsonObject {
                                                            @Suppress("UNCHECKED_CAST")
                                                            (v2 as Map<String, Any>).forEach { (k3, v3) ->
                                                                when (v3) {
                                                                    is String -> put(k3, v3)
                                                                    is Number -> put(k3, v3)
                                                                    is Boolean -> put(k2, v3)
                                                                }
                                                            }
                                                        })
                                                    }
                                                }
                                            }
                                        })
                                    }
                                    // Remove else clause to ensure proper JSON object handling
                                }
                            }
                        })
                    }
                    // Remove else clause to ensure proper JSON object handling
                }
            }
        }.toString()
    }
    
    /**
     * Legacy method for backward compatibility - now calls bootstrap
     */
    private suspend fun trackInstallation(): ULinkInstallationResponse {
        bootstrap()
        val installationId = getInstallationId() ?: "unknown"
        return ULinkInstallationResponse.success(installationId, true)
    }
    
    /**
     * Handles incoming deep links
     */
    fun handleDeepLink(uri: Uri) {
        if (config.debug) {
            Log.d(TAG, "Handling deep link: $uri")
        }
        
        scope.launch {
            try {
                val resolvedData = resolveLink(uri.toString())
                if (resolvedData.success && resolvedData.data != null) {
                    val linkData = ULinkResolvedData.fromJsonObject(resolvedData.data!!)
                    linkData?.let {
                        if (config.debug) {
                            Log.d(TAG, "Resolved link data:")
                            Log.d(TAG, "  Type: ${it.type}")
                            Log.d(TAG, "  Slug: ${it.slug}")
                            Log.d(TAG, "  Fallback URL: ${it.fallbackUrl}")
                            Log.d(TAG, "  Parameters: ${it.parameters}")
                            Log.d(TAG, "  Metadata: ${it.metadata}")
                            Log.d(TAG, "  Raw data: ${it.rawData}")
                        }
                        
                        lastLinkData = it
                        saveLastLinkData(it)
                        
                        // Emit to appropriate stream based on type
                        when (it.type) {
                            "dynamic" -> _dynamicLinkStream.emit(it)
                            "unified" -> _unifiedLinkStream.emit(it)
                            else -> _dynamicLinkStream.emit(it) // Default to dynamic
                        }
                    }
                } else {
                    if (config.debug) {
                        Log.d(TAG, "Failed to resolve link: ${resolvedData.error}")
                    }
                }
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e(TAG, "Failed to handle deep link", e)
                }
            }
        }
    }
    
    /**
     * Sets the initial deep link URI
     */
    fun setInitialUri(uri: Uri?) {
        initialUri = uri
        if (config.debug) {
            Log.d(TAG, "Set initial URI: $uri")
        }
    }
    
    /**
     * Gets the initial deep link URI
     */
    fun getInitialUri(): Uri? {
        return initialUri
    }
    
    /**
     * Process a URI and resolve ULink data by querying the server
     * This unified method is used by both internal link handling and external components
     * Returns null if the URI cannot be resolved or is not a ULink
     */
    suspend fun processULinkUri(uri: Uri): ULinkResolvedData? {
        return try {
            if (config.debug) {
                Log.d(TAG, "Processing URI: ${uri}")
            }
            
            // Always try to resolve the URI with the server to determine if it's a ULink
            if (config.debug) {
                Log.d(TAG, "Querying server to resolve URI...")
            }
            val resolveResponse = resolveLink(uri.toString())
            
            if (resolveResponse.success && resolveResponse.data != null) {
                val resolvedData = ULinkResolvedData.fromJsonObject(resolveResponse.data!!)
                if (config.debug) {
                    Log.d(TAG, "Successfully resolved ULink data: ${resolvedData?.rawData}")
                }
                resolvedData
            } else {
                // Differentiate between network errors and non-ULink responses
                if (resolveResponse.error != null) {
                    if (resolveResponse.error!!.contains("network") ||
                        resolveResponse.error!!.contains("timeout") ||
                        resolveResponse.error!!.contains("connection")) {
                        if (config.debug) {
                            Log.d(TAG, "Network error while resolving URI: ${resolveResponse.error}")
                        }
                    } else {
                        if (config.debug) {
                            Log.d(TAG, "URI is not a ULink: ${resolveResponse.error}")
                        }
                    }
                } else {
                    if (config.debug) {
                        Log.d(TAG, "Server responded but URI is not a ULink")
                    }
                }
                null
            }
        } catch (e: Exception) {
            if (config.debug) {
                Log.e(TAG, "Exception while processing ULink URI: $e")
            }
            null
        }
    }
    
    /**
     * Gets the initial deep link as resolved data
     */
    suspend fun getInitialDeepLink(): ULinkResolvedData? {
        return initialUri?.let { uri ->
            try {
                val resolvedData = resolveLink(uri.toString())
                if (resolvedData.success && resolvedData.data != null) {
                    val linkData = ULinkResolvedData.fromJsonObject(resolvedData.data!!)
                    if (config.debug && linkData != null) {
                        Log.d(TAG, "Initial deep link resolved:")
                        Log.d(TAG, "  Type: ${linkData.type}")
                        Log.d(TAG, "  Slug: ${linkData.slug}")
                        Log.d(TAG, "  Fallback URL: ${linkData.fallbackUrl}")
                        Log.d(TAG, "  Parameters: ${linkData.parameters}")
                        Log.d(TAG, "  Metadata: ${linkData.metadata}")
                        Log.d(TAG, "  Raw data: ${linkData.rawData}")
                    }
                    linkData
                } else {
                    if (config.debug) {
                        Log.d(TAG, "Failed to resolve initial deep link: ${resolvedData.error}")
                    }
                    null
                }
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e(TAG, "Failed to resolve initial deep link", e)
                }
                null
            }
        }
    }
    
    /**
     * Gets the last received link data
     */
    fun getLastLinkData(): ULinkResolvedData? {
        val data = lastLinkData
        if (data != null && config.clearLastLinkOnRead) {
            clearPersistedLastLink()
            lastLinkData = null
        }
        return data
    }
    
    /**
     * Creates a dynamic link
     */
    suspend fun createLink(parameters: ULinkParameters): ULinkResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = "${config.baseUrl}/sdk/links"
                val headers = mutableMapOf(
                    "X-App-Key" to config.apiKey,
                    "Content-Type" to "application/json"
                )
                
                // Add additional headers to match Flutter SDK
                getInstallationToken()?.let { token ->
                    headers["X-Installation-Token"] = token
                } ?: getInstallationId()?.let { id ->
                    headers["X-Installation-Id"] = id
                }
                
                DeviceInfoUtils.getDeviceId(context)?.let { deviceId ->
                    headers["X-Device-Id"] = deviceId
                }
                
                headers["X-ULink-Client"] = "sdk-android"
                headers["X-ULink-Client-Version"] = SDK_VERSION
                headers["X-ULink-Client-Platform"] = "android"
                
                val response = httpClient.postJson(url, parameters.toJson(), headers)
                
                if (response.isSuccess) {
                    // Capture updated installation token if provided
                    val tokenHeader = response.headers["x-installation-token"]
                    if (!tokenHeader.isNullOrEmpty()) {
                        saveInstallationToken(tokenHeader)
                    }
                    
                    val json = response.parseJson()
                    // Prefer shortUrl when present, else url
                    val shortUrl = json?.get("shortUrl")?.toString()?.removeSurrounding("\"")
                    val linkUrl = shortUrl ?: json?.get("url")?.toString()?.removeSurrounding("\"")

                    if (linkUrl != null && linkUrl.isNotEmpty()) {
                        ULinkResponse.success(linkUrl, json)
                    } else {
                        ULinkResponse.error("No URL in response")
                    }
                } else {
                    ULinkResponse.error("HTTP ${response.statusCode}: ${response.body}")
                }
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e(TAG, "Failed to create link", e)
                }
                ULinkResponse.error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Resolves a dynamic link
     */
    suspend fun resolveLink(url: String): ULinkResponse {
        return withContext(Dispatchers.IO) {
            try {
                val resolveUrl = "${config.baseUrl}/sdk/resolve?url=${Uri.encode(url)}"
                val headers = mutableMapOf(
                    "X-App-Key" to config.apiKey,
                    "X-Device-Id" to getInstallationId(),
                    "X-ULink-Client" to "sdk-android",
                    "X-ULink-Client-Version" to SDK_VERSION,
                    "X-ULink-Client-Platform" to "android",
                    "Content-Type" to "application/json"
                )
                
                // Add installation token if available, otherwise use installation ID
                val installationToken = getInstallationToken()
                if (installationToken != null) {
                    headers["X-Installation-Token"] = installationToken
                } else {
                    getInstallationId()?.let { installationId ->
                        headers["X-Installation-Id"] = installationId
                    }
                }
                
                // Filter out null values for httpClient compatibility
                val nonNullableHeaders = headers.filterValues { it != null }.mapValues { it.value!! }
                val response = httpClient.get(resolveUrl, nonNullableHeaders)
                
                if (response.isSuccess) {
                    // Capture updated installation token if provided
                    response.headers["x-installation-token"]?.let { token ->
                        if (token.isNotEmpty()) {
                            saveInstallationToken(token)
                        }
                    }
                    
                    val json = response.parseJson()
                    val resolvedUrl = json?.get("url")?.toString()?.removeSurrounding("\"")
                    ULinkResponse.success(resolvedUrl ?: url, json)
                } else {
                    ULinkResponse.error("HTTP ${response.statusCode}: ${response.body}")
                }
            } catch (e: Exception) {
                if (config.debug) {
                    Log.e(TAG, "Failed to resolve link", e)
                }
                ULinkResponse.error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Starts a new session (internal use only - automatically managed by lifecycle)
     */
    private suspend fun startSession(metadata: Map<String, Any>? = null): ULinkSessionResponse {
        return withContext(Dispatchers.IO) {
            try {
                // Set state to initializing
                sessionState = SessionState.INITIALIZING
                sessionFuture = CompletableFuture<Void>()
                
                val installationId = getInstallationId() ?: return@withContext ULinkSessionResponse.error("No installation ID")
                
                // Collect comprehensive device information
                val completeDeviceInfo = DeviceInfoUtils.getCompleteDeviceInfo(context)
                
                // Extract specific fields that go at the top level
                val networkType = completeDeviceInfo["networkType"]
                val deviceOrientation = completeDeviceInfo["deviceOrientation"]
                val batteryLevel = completeDeviceInfo["batteryLevel"]
                val isCharging = completeDeviceInfo["isCharging"]
                
                // Filter out the extracted fields from device info
                val filteredDeviceInfo = completeDeviceInfo.filterKeys { key ->
                    key !in setOf("networkType", "deviceOrientation", "batteryLevel", "isCharging")
                }.filterValues { it != null }
                
                // Create metadata object with deviceInfo nested inside
                val metadataMap = mutableMapOf<String, Any>()
                metadataMap["deviceInfo"] = filteredDeviceInfo
                
                // Merge with provided metadata
                metadata?.let { metadataMap.putAll(it) }
                
                // Convert metadata map to JsonElement
                val metadataJsonElement = buildJsonObject {
                    metadataMap.forEach { (key, value) ->
                        when (value) {
                            is String -> put(key, value)
                            is Number -> put(key, value)
                            is Boolean -> put(key, value)
                            is Map<*, *> -> {
                                put(key, buildJsonObject {
                                    @Suppress("UNCHECKED_CAST")
                                    (value as Map<String, Any>).forEach { (nestedKey, nestedValue) ->
                                        when (nestedValue) {
                                            is String -> put(nestedKey, nestedValue)
                                            is Number -> put(nestedKey, nestedValue)
                                            is Boolean -> put(nestedKey, nestedValue)
                                            is Map<*, *> -> {
                                                put(nestedKey, buildJsonObject {
                                                    @Suppress("UNCHECKED_CAST")
                                                    (nestedValue as Map<String, Any>).forEach { (deepKey, deepValue) ->
                                                        when (deepValue) {
                                                            is String -> put(deepKey, deepValue)
                                                            is Number -> put(deepKey, deepValue)
                                                            is Boolean -> put(deepKey, deepValue)
                                                        }
                                                    }
                                                })
                                            }
                                        }
                                    }
                                })
                            }
                            // Remove the else clause that was converting objects to strings
                            // This ensures Map objects are properly handled as JSON objects
                        }
                    }
                }
                
                val sessionData = ULinkSession(
                    installationId = installationId,
                    networkType = networkType as? String,
                    deviceOrientation = deviceOrientation as? String,
                    batteryLevel = batteryLevel as? Int,
                    isCharging = isCharging as? Boolean,
                    metadata = metadataJsonElement
                )
                
                val url = "${config.baseUrl}/sdk/sessions/start"
                val headers = mutableMapOf(
                    "X-App-Key" to config.apiKey,
                    "Content-Type" to "application/json",
                    "X-ULink-Client" to "sdk-android",
                    "X-ULink-Client-Version" to SDK_VERSION,
                    "X-ULink-Client-Platform" to "android"
                )
                
                // Add installation ID if available
                getInstallationId()?.let { id ->
                    headers["X-Installation-Id"] = id
                }
                
                // Add device ID if available
                DeviceInfoUtils.getDeviceId(context)?.let { deviceId ->
                    headers["X-Device-Id"] = deviceId
                }
                
                val response = httpClient.postJson(url, sessionData.toJson(), headers)
                
                if (response.isSuccess) {
                    val json = response.parseJson()
                    val sessionId = json?.get("sessionId")?.toString()?.removeSurrounding("\"")
                    
                    if (sessionId != null) {
                        currentSessionId = sessionId
                        sessionState = SessionState.ACTIVE
                        if (config.debug) {
                            Log.d(TAG, "Session started: $sessionId")
                        }
                        // Complete the session future
                        sessionFuture?.complete(null)
                        ULinkSessionResponse.success(sessionId)
                    } else {
                        sessionState = SessionState.FAILED
                        sessionFuture?.complete(null)
                        ULinkSessionResponse.error("No session ID in response")
                    }
                } else {
                    sessionState = SessionState.FAILED
                    sessionFuture?.complete(null)
                    ULinkSessionResponse.error("HTTP ${response.statusCode}: ${response.body}")
                }
            } catch (e: Exception) {
                sessionState = SessionState.FAILED
                sessionFuture?.complete(null)
                if (config.debug) {
                    Log.e(TAG, "Failed to start session", e)
                }
                ULinkSessionResponse.error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Ends the current session
     */
    suspend fun endSession(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sessionId = currentSessionId ?: return@withContext false
                
                // Set state to ending
                sessionState = SessionState.ENDING
                
                val url = "${config.baseUrl}/sdk/sessions/${sessionId}/end"
                val headers = mapOf(
                    "X-App-Key" to "${config.apiKey}",
                    "Content-Type" to "application/json"
                )
                
                val response = httpClient.postJson(url, emptyMap(), headers)
                
                if (response.isSuccess) {
                    currentSessionId = null
                    sessionState = SessionState.IDLE
                    if (config.debug) {
                        Log.d(TAG, "Session ended: $sessionId")
                    }
                    true
                } else {
                    sessionState = SessionState.FAILED
                    if (config.debug) {
                        Log.e(TAG, "Failed to end session: ${response.body}")
                    }
                    false
                }
            } catch (e: Exception) {
                sessionState = SessionState.FAILED
                if (config.debug) {
                    Log.e(TAG, "Failed to end session", e)
                }
                false
            }
        }
    }
    
    /**
     * Gets the current session ID
     */
    fun getCurrentSessionId(): String? {
        return currentSessionId
    }
    
    /**
     * Checks if there's an active session
     */
    fun hasActiveSession(): Boolean {
        return currentSessionId != null
    }
    
    /**
     * Gets the current session state
     */
    fun getSessionState(): SessionState {
        return sessionState
    }
    
    /**
     * Waits for session initialization to complete (internal use only)
     */
    private suspend fun waitForSessionInitialization() {
        sessionFuture?.let { future ->
            withContext(Dispatchers.IO) {
                try {
                    future.get()
                } catch (e: Exception) {
                    if (config.debug) {
                        Log.e(TAG, "Error waiting for session initialization", e)
                    }
                    // Handle exception silently if debug is disabled
                }
                Unit // Explicitly return Unit to satisfy withContext
            }
        }
    }
    
    /**
     * Checks if session is currently initializing
     */
    fun isSessionInitializing(): Boolean {
        return sessionState == SessionState.INITIALIZING
    }
    
    /**
     * Saves last link data to preferences
     */
    private fun saveLastLinkData(data: ULinkResolvedData) {
        if (!config.persistLastLinkData) return
        val sanitized = sanitizeLastLinkData(data)
        sharedPreferences.edit()
            .putString(KEY_LAST_LINK_DATA, sanitized)
            .putLong(KEY_LAST_LINK_SAVED_AT, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * Loads last link data from preferences
     */
    private fun loadLastLinkData() {
        val jsonString = sharedPreferences.getString(KEY_LAST_LINK_DATA, null)
        val savedAt = sharedPreferences.getLong(KEY_LAST_LINK_SAVED_AT, 0L)
        if (jsonString == null) return

        val ttl = config.lastLinkTimeToLiveSeconds
        if (ttl > 0 && savedAt > 0) {
            val age = System.currentTimeMillis() - savedAt
            if (age > ttl * 1000) {
                clearPersistedLastLink()
                return
            }
        }

        lastLinkData = ULinkResolvedData.fromJson(jsonString)
    }

    private fun clearPersistedLastLink() {
        sharedPreferences.edit()
            .remove(KEY_LAST_LINK_DATA)
            .remove(KEY_LAST_LINK_SAVED_AT)
            .apply()
    }

    private fun sanitizeLastLinkData(data: ULinkResolvedData): String {
        val dropAll = config.redactAllParametersInLastLink
        val baseJson: JsonObject = data.rawData ?: buildJsonObject {
            data.slug?.let { put("slug", it) }
            data.iosFallbackUrl?.let { put("iosFallbackUrl", it) }
            data.androidFallbackUrl?.let { put("androidFallbackUrl", it) }
            data.fallbackUrl?.let { put("fallbackUrl", it) }
            data.type?.let { put("type", it) }
        }

        fun redactKeys(obj: JsonObject?, keys: List<String>): JsonObject? {
            if (obj == null || keys.isEmpty()) return obj
            return buildJsonObject {
                obj.forEach { (k, v) -> if (!keys.contains(k)) put(k, v) }
            }
        }

        val redactedParams = if (dropAll) null else redactKeys(baseJson["parameters"] as? JsonObject, config.redactedParameterKeysInLastLink)
        val redactedMeta = if (dropAll) null else redactKeys(baseJson["metadata"] as? JsonObject, config.redactedParameterKeysInLastLink)

        val finalJson = buildJsonObject {
            baseJson.forEach { (k, v) ->
                if (k != "parameters" && k != "metadata") put(k, v)
            }
            redactedParams?.let { put("parameters", it) }
            redactedMeta?.let { put("metadata", it) }
        }

        return finalJson.toString()
    }
    
    // ActivityLifecycleCallbacks implementation for automatic deep link integration
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (config.enableDeepLinkIntegration) {
            activity.intent?.let { intent ->
                handleActivityIntent(intent)
            }
        }
    }
    
    override fun onActivityStarted(activity: Activity) {}
    
    override fun onActivityResumed(activity: Activity) {
        if (config.enableDeepLinkIntegration) {
            activity.intent?.let { intent ->
                handleActivityIntent(intent)
            }
        }
    }
    
    override fun onActivityPaused(activity: Activity) {}
    
    override fun onActivityStopped(activity: Activity) {}
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    
    override fun onActivityDestroyed(activity: Activity) {}
    
    /**
     * Handles intent from activity for automatic deep link processing
     */
    private fun handleActivityIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                if (config.debug) {
                    Log.d(TAG, "Automatic deep link detected: $uri")
                }
                handleDeepLink(uri)
            }
        }
    }
    
    /**
     * Disposes the SDK and cleans up resources
     */
    fun dispose() {
        scope.launch {
            endSession()
        }
        scope.cancel()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        
        // Unregister activity lifecycle callbacks
        if (config.enableDeepLinkIntegration) {
            (context.applicationContext as? Application)?.unregisterActivityLifecycleCallbacks(this)
        }
        
        if (config.debug) {
            Log.d(TAG, "ULink SDK disposed")
        }
    }
}