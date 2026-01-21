package ly.ulink.sdk

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Build
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
import ly.ulink.sdk.models.ULinkInitializationError
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.utils.DeviceInfoUtils
import java.util.*
import java.util.concurrent.CompletableFuture
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

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
        private const val SDK_VERSION = "1.0.6"
        
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
        /**
         * Initialize the ULink SDK
         * 
         * This method is now suspend and will await bootstrap completion.
         * It throws ULinkInitializationError if essential operations fail.
         * Thread-safe with double-checked locking to prevent duplicate initialization.
         * 
         * @param context The application context
         * @param config The SDK configuration
         * @param httpClient Optional HTTP client for testing
         * @return The initialized ULink instance
         * @throws ULinkInitializationError if initialization fails
         */
        @Volatile
        private var isInitializing = false
        private val initLock = Any()

        @JvmStatic
        suspend fun initialize(
            context: Context,
            config: ULinkConfig,
            httpClient: HttpClient? = null
        ): ULink {
            // Fast path: already initialized successfully
            val existingInstance = INSTANCE
            if (existingInstance != null && existingInstance.bootstrapSucceeded) {
                return existingInstance
            }
            
            // Thread-safe initialization with synchronization
            synchronized(initLock) {
                // Double-check after acquiring lock
                val instance = INSTANCE
                if (instance != null) {
                    if (instance.bootstrapSucceeded) {
                        return instance
                    }
                    
                    // Bootstrap failed or didn't complete - retry only if not currently initializing
                    if (!isInitializing) {
                        isInitializing = true
                        try {
                            instance.bootstrapCompleted = false
                            instance.bootstrapSucceeded = false
                            kotlinx.coroutines.runBlocking {
                                instance.setup()
                            }
                        } finally {
                            isInitializing = false
                        }
                    }
                    return instance
                }
                
                // Create new instance - protected by lock
                isInitializing = true
                try {
                    val newInstance = ULink(context.applicationContext, config, httpClient)
                    INSTANCE = newInstance
                    kotlinx.coroutines.runBlocking {
                        newInstance.setup()
                    }
                    return newInstance
                } finally {
                    isInitializing = false
                }
            }
        }

        @JvmStatic
        fun createTestInstance(context: Context, config: ULinkConfig, httpClient: HttpClient): ULink {
            return runBlocking {
                initialize(context, config, httpClient)
            }
        }

        /**
         * Get the singleton instance
         * @throws IllegalStateException if the SDK is not initialized
         */
        @JvmStatic
        fun getInstance(): ULink {
            return INSTANCE ?: throw IllegalStateException(
                "ULink SDK not initialized. Call ULink.initialize() first."
            )
        }

        /**
         * Java-friendly async initialization method.
         * Returns a CompletableFuture that completes with the initialized ULink instance.
         *
         * This is the recommended way to initialize the SDK from Java code.
         *
         * Example usage in Java:
         * ```java
         * ULinkConfig config = new ULinkConfig(
         *     "your-api-key",
         *     "https://api.ulink.ly",
         *     true,  // debug
         *     true,  // enableDeepLinkIntegration
         *     true,  // autoCheckDeferredLink
         *     false  // persistLastLinkData
         * );
         *
         * ULink.initializeAsync(context, config)
         *     .thenAccept(ulink -> {
         *         Log.d("ULink", "SDK initialized successfully");
         *         // SDK is ready to use
         *     })
         *     .exceptionally(error -> {
         *         Log.e("ULink", "Failed to initialize SDK", error);
         *         return null;
         *     });
         * ```
         *
         * @param context The application context
         * @param config The SDK configuration
         * @param httpClient Optional HTTP client for testing (can be null)
         * @return CompletableFuture that completes with the initialized ULink instance
         */
        @JvmStatic
        @JvmOverloads
        fun initializeAsync(
            context: Context,
            config: ULinkConfig,
            httpClient: HttpClient? = null
        ): java.util.concurrent.CompletableFuture<ULink> {
            val future = java.util.concurrent.CompletableFuture<ULink>()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val instance = initialize(context, config, httpClient)
                    future.complete(instance)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
            return future
        }

        /**
         * Initialize SDK with callbacks (Java-friendly).
         * Alternative to [initializeAsync] for developers who prefer callbacks.
         *
         * **Threading:** Both callbacks are invoked on the main (UI) thread.
         *
         * **Error Handling:** If [onError] is not provided and initialization fails,
         * the exception will be re-thrown. Always provide an error callback in production.
         *
         * Example usage in Java:
         * ```java
         * ULinkConfig config = new ULinkConfig(
         *     "your-api-key",
         *     "https://api.ulink.ly",
         *     true,  // debug
         *     true,  // enableDeepLinkIntegration
         *     true,  // autoCheckDeferredLink
         *     false  // persistLastLinkData
         * );
         *
         * ULink.initialize(context, config,
         *     ulink -> {
         *         Log.d("ULink", "SDK initialized successfully");
         *         // SDK is ready to use
         *     },
         *     error -> {
         *         Log.e("ULink", "Failed to initialize SDK", error);
         *     }
         * );
         * ```
         *
         * @param context Application context
         * @param config SDK configuration
         * @param onSuccess Callback invoked on main thread when initialization succeeds
         * @param onError Optional callback invoked on main thread when initialization fails.
         *                If null and an error occurs, the exception is re-thrown.
         * @see initializeAsync
         * @see initialize
         */
        @JvmStatic
        @JvmOverloads
        fun initialize(
            context: Context,
            config: ULinkConfig,
            onSuccess: (ULink) -> Unit,
            onError: ((Throwable) -> Unit)? = null
        ) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    val instance = initialize(context, config)
                    onSuccess(instance)
                } catch (e: Exception) {
                    onError?.invoke(e) ?: throw e
                }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = injectedHttpClient ?: HttpClient(config.debug)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Installation token
    private var installationToken: String? = null
    
    // Installation info (including reinstall detection)
    private var _installationInfo: ULinkInstallationInfo? = null
    
    // Reinstall detection stream
    private val _reinstallDetectedStream = MutableSharedFlow<ULinkInstallationInfo>(replay = 1)
    
    /**
     * Flow that emits when a reinstall is detected.
     * The emitted ULinkInstallationInfo contains details about the reinstall,
     * including the previous installation ID.
     */
    val onReinstallDetected: SharedFlow<ULinkInstallationInfo> = _reinstallDetectedStream.asSharedFlow()
    
    // Session management
    private var currentSessionId: String? = null
    private var bootstrapCompleted = false
    private var bootstrapSucceeded = false
    private var pendingSessionStart = false
    
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
    
    // Debug log stream
    private val _logStream = MutableSharedFlow<ULinkLogEntry>(replay = 50, extraBufferCapacity = 100)
    
    /**
     * Flow of SDK log entries for debugging
     * Only emits when debug mode is enabled
     */
    val logStream: SharedFlow<ULinkLogEntry> = _logStream.asSharedFlow()

    // ========== JAVA-FRIENDLY LISTENER SUPPORT ==========

    // Listener variables for Java compatibility
    private var onLinkListener: ly.ulink.sdk.listeners.OnLinkListener? = null
    private var onUnifiedLinkListener: ly.ulink.sdk.listeners.OnUnifiedLinkListener? = null
    private var onReinstallListener: ly.ulink.sdk.listeners.OnReinstallListener? = null
    private var onLogListener: ly.ulink.sdk.listeners.OnLogListener? = null

    // Individual listener jobs for proper cleanup
    private var onLinkListenerJob: kotlinx.coroutines.Job? = null
    private var onUnifiedLinkListenerJob: kotlinx.coroutines.Job? = null
    private var onReinstallListenerJob: kotlinx.coroutines.Job? = null
    private var onLogListenerJob: kotlinx.coroutines.Job? = null

    // Legacy list for backward compatibility in dispose()
    private val listenerJobs = mutableListOf<kotlinx.coroutines.Job>()

    /**
     * Set a listener for dynamic link events (Java-friendly alternative to dynamicLinkStream).
     *
     * This method provides a simple callback-based API for Java developers to receive
     * deep link resolution events without needing to work with Kotlin Flows.
     *
     * **Threading:** Listener callbacks are invoked on the main (UI) thread.
     *
     * Example usage in Java:
     * ```java
     * ulink.setOnLinkListener(data -> {
     *     Log.d("ULink", "Link received: " + data.getSlug());
     *     // Handle navigation based on data
     * });
     * ```
     *
     * @param listener The listener to receive link events, or null to remove the listener
     * @see removeOnLinkListener
     * @see dynamicLinkStream
     */
    fun setOnLinkListener(listener: ly.ulink.sdk.listeners.OnLinkListener?) {
        // Cancel previous listener job
        onLinkListenerJob?.cancel()
        onLinkListenerJob = null
        onLinkListener = listener

        if (listener != null) {
            val job = scope.launch {
                dynamicLinkStream.collect { data ->
                    listener.onLinkReceived(data)
                }
            }
            onLinkListenerJob = job
            listenerJobs.add(job)
        }
    }

    /**
     * Remove the currently registered dynamic link listener.
     *
     * Example usage in Java:
     * ```java
     * // In onDestroy or when cleaning up
     * ulink.removeOnLinkListener();
     * ```
     *
     * @see setOnLinkListener
     */
    fun removeOnLinkListener() {
        setOnLinkListener(null)
    }

    /**
     * Set a listener for unified link events (Java-friendly alternative to unifiedLinkStream).
     *
     * Unified links are platform-specific redirects designed for "open in browser" scenarios.
     *
     * **Threading:** Listener callbacks are invoked on the main (UI) thread.
     *
     * Example usage in Java:
     * ```java
     * ulink.setOnUnifiedLinkListener(data -> {
     *     Log.d("ULink", "Unified link: " + data.getSlug());
     *     // Optionally open in browser or handle in-app
     * });
     * ```
     *
     * @param listener The listener to receive unified link events, or null to remove the listener
     * @see removeOnUnifiedLinkListener
     * @see unifiedLinkStream
     */
    fun setOnUnifiedLinkListener(listener: ly.ulink.sdk.listeners.OnUnifiedLinkListener?) {
        // Cancel previous listener job
        onUnifiedLinkListenerJob?.cancel()
        onUnifiedLinkListenerJob = null
        onUnifiedLinkListener = listener

        if (listener != null) {
            val job = scope.launch {
                unifiedLinkStream.collect { data ->
                    listener.onUnifiedLinkReceived(data)
                }
            }
            onUnifiedLinkListenerJob = job
            listenerJobs.add(job)
        }
    }

    /**
     * Remove the currently registered unified link listener.
     *
     * Example usage in Java:
     * ```java
     * // In onDestroy or when cleaning up
     * ulink.removeOnUnifiedLinkListener();
     * ```
     *
     * @see setOnUnifiedLinkListener
     */
    fun removeOnUnifiedLinkListener() {
        setOnUnifiedLinkListener(null)
    }

    /**
     * Set a listener for reinstall detection events.
     *
     * This listener is triggered when the SDK detects that the app was previously installed.
     *
     * **Threading:** Listener callbacks are invoked on the main (UI) thread.
     *
     * Example usage in Java:
     * ```java
     * ulink.setOnReinstallListener(info -> {
     *     Log.d("ULink", "Reinstall detected!");
     *     // Track reinstall event in analytics
     * });
     * ```
     *
     * @param listener The listener to receive reinstall events, or null to remove the listener
     * @see removeOnReinstallListener
     * @see onReinstallDetected
     */
    fun setOnReinstallListener(listener: ly.ulink.sdk.listeners.OnReinstallListener?) {
        // Cancel previous listener job
        onReinstallListenerJob?.cancel()
        onReinstallListenerJob = null
        onReinstallListener = listener

        if (listener != null) {
            val job = scope.launch {
                onReinstallDetected.collect { info ->
                    listener.onReinstallDetected(info)
                }
            }
            onReinstallListenerJob = job
            listenerJobs.add(job)
        }
    }

    /**
     * Remove the currently registered reinstall detection listener.
     *
     * Example usage in Java:
     * ```java
     * // In onDestroy or when cleaning up
     * ulink.removeOnReinstallListener();
     * ```
     *
     * @see setOnReinstallListener
     */
    fun removeOnReinstallListener() {
        setOnReinstallListener(null)
    }

    /**
     * Set a listener for SDK log events (only active when debug mode is enabled).
     *
     * This provides access to internal SDK log messages for debugging purposes.
     *
     * **Threading:** Listener callbacks are invoked on the main (UI) thread.
     *
     * Example usage in Java:
     * ```java
     * ulink.setOnLogListener(entry -> {
     *     Log.d("ULink", entry.getMessage());
     *     // Forward to your logging system
     * });
     * ```
     *
     * @param listener The listener to receive log events, or null to remove the listener
     * @see removeOnLogListener
     * @see logStream
     */
    fun setOnLogListener(listener: ly.ulink.sdk.listeners.OnLogListener?) {
        // Cancel previous listener job
        onLogListenerJob?.cancel()
        onLogListenerJob = null
        onLogListener = listener

        if (listener != null) {
            val job = scope.launch {
                logStream.collect { entry ->
                    listener.onLog(entry)
                }
            }
            onLogListenerJob = job
            listenerJobs.add(job)
        }
    }

    /**
     * Remove the currently registered log listener.
     *
     * Example usage in Java:
     * ```java
     * // In onDestroy or when cleaning up
     * ulink.removeOnLogListener();
     * ```
     *
     * @see setOnLogListener
     */
    fun removeOnLogListener() {
        setOnLogListener(null)
    }

    /**
     * Remove all registered listeners.
     *
     * Call this method to clean up all listener registrations at once,
     * typically in your Activity's onDestroy or when the SDK is no longer needed.
     *
     * Example usage in Java:
     * ```java
     * @Override
     * protected void onDestroy() {
     *     super.onDestroy();
     *     ulink.removeAllListeners();
     * }
     * ```
     *
     * @see removeOnLinkListener
     * @see removeOnUnifiedLinkListener
     * @see removeOnReinstallListener
     * @see removeOnLogListener
     */
    fun removeAllListeners() {
        removeOnLinkListener()
        removeOnUnifiedLinkListener()
        removeOnReinstallListener()
        removeOnLogListener()
    }

    // ========== END OF LISTENER SUPPORT ==========

    // Initial deep link data
    private var initialUri: Uri? = null
    private var lastLinkData: ULinkResolvedData? = null
    
    /**
     * Logs a debug message to both Android Log and the log stream
     */
    private fun logDebug(message: String, tag: String = TAG) {
        if (config.debug) {
            Log.d(tag, message)
            scope.launch {
                _logStream.emit(ULinkLogEntry(
                    level = ULinkLogEntry.LEVEL_DEBUG,
                    tag = tag,
                    message = message
                ))
            }
        }
    }
    
    /**
     * Logs an info message to both Android Log and the log stream
     */
    private fun logInfo(message: String, tag: String = TAG) {
        if (config.debug) {
            Log.i(tag, message)
            scope.launch {
                _logStream.emit(ULinkLogEntry(
                    level = ULinkLogEntry.LEVEL_INFO,
                    tag = tag,
                    message = message
                ))
            }
        }
    }
    
    /**
     * Logs a warning message to both Android Log and the log stream
     */
    private fun logWarning(message: String, tag: String = TAG) {
        Log.w(tag, message)
        if (config.debug) {
            scope.launch {
                _logStream.emit(ULinkLogEntry(
                    level = ULinkLogEntry.LEVEL_WARNING,
                    tag = tag,
                    message = message
                ))
            }
        }
    }
    
    /**
     * Logs an error message to both Android Log and the log stream
     */
    private fun logError(message: String, throwable: Throwable? = null, tag: String = TAG) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
        if (config.debug) {
            val fullMessage = if (throwable != null) "$message: ${throwable.message}" else message
            scope.launch {
                _logStream.emit(ULinkLogEntry(
                    level = ULinkLogEntry.LEVEL_ERROR,
                    tag = tag,
                    message = fullMessage
                ))
            }
        }
    }
    
    /**
     * Sets up the SDK
     * 
     * This method is now suspend and awaits bootstrap completion.
     * It throws ULinkInitializationError if essential operations fail.
     * 
     * @throws ULinkInitializationError if bootstrap fails
     */
    private suspend fun setup() {
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
        
        // Load last link data (non-essential, don't throw)
        loadLastLinkData()
        
        if (config.debug) {
            logInfo("ULink SDK initialized with API key: ${config.apiKey}")
            logDebug("Installation ID: ${getInstallationId()}")
            logDebug("Installation Token: ${if (installationToken != null) "[LOADED]" else "[NOT FOUND]"}")
        }
        
        // Bootstrap installation and session with the server (essential - await and throw on failure)
        try {
            bootstrap()
            bootstrapSucceeded = true
            bootstrapCompleted = true
            
            if (pendingSessionStart) {
                pendingSessionStart = false
                startSessionIfNeeded()
            }
            
            // Check for deferred links after bootstrap completes (if enabled in config)
            // Launch in background coroutine (non-blocking) to match iOS behavior
            if (config.autoCheckDeferredLink) {
                scope.launch {
                    try {
                        checkDeferredLink()
                    } catch (e: Exception) {
                        logError("Error checking deferred link during setup", e)
                    }
                }
            }
        } catch (e: ULinkInitializationError) {
            bootstrapSucceeded = false
            bootstrapCompleted = true
            throw e
        } catch (e: Exception) {
            bootstrapSucceeded = false
            bootstrapCompleted = true
            throw ULinkInitializationError.bootstrapFailed(
                statusCode = 0,
                message = "Bootstrap failed: ${e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Retrieves the clickId from Google Play Install Referrer
     * This enables deterministic deferred deep link matching with 100% accuracy
     * Returns null if Install Referrer is not available or doesn't contain clickId
     */
    private suspend fun getInstallReferrerClickId(): String? {
        return suspendCoroutine { continuation ->
            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            
            referrerClient.startConnection(object : InstallReferrerStateListener {
                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try {
                                val response = referrerClient.installReferrer
                                val referrer = response.installReferrer
                                
                                if (config.debug) {
                                    logDebug("Install Referrer raw: $referrer")
                                }
                                
                                // Parse clickId from referrer
                                // Format: "click_id=abc123" or "utm_source=...&click_id=abc123"
                                val clickId = referrer
                                    .split("&")
                                    .find { it.startsWith("click_id=") }
                                    ?.substringAfter("click_id=")
                                
                                if (config.debug) {
                                    logDebug("Extracted clickId: ${clickId ?: "not found"}")
                                }
                                
                                continuation.resume(clickId)
                            } catch (e: Exception) {
                                if (config.debug) {
                                    logError("Error parsing Install Referrer", e)
                                }
                                continuation.resume(null)
                            } finally {
                                referrerClient.endConnection()
                            }
                        }
                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                            if (config.debug) {
                                logWarning("Install Referrer not supported on this device")
                            }
                            continuation.resume(null)
                            referrerClient.endConnection()
                        }
                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            if (config.debug) {
                                logWarning("Install Referrer service unavailable")
                            }
                            continuation.resume(null)
                            referrerClient.endConnection()
                        }
                        else -> {
                            if (config.debug) {
                                logWarning("Install Referrer response code: $responseCode")
                            }
                            continuation.resume(null)
                            referrerClient.endConnection()
                        }
                    }
                }
                
                override fun onInstallReferrerServiceDisconnected() {
                    if (config.debug) {
                        logDebug("Install Referrer service disconnected")
                    }
                    continuation.resume(null)
                }
            })
        }
    }

    /**
     * Manually checks for deferred deep links
     * This is useful when autoCheckDeferredLink is disabled in config
     */
    fun checkDeferredLink() {
        // Ensure bootstrap completed before checking deferred links
        ensureBootstrapCompleted()
        
        scope.launch(Dispatchers.IO) {
            try {
                logDebug("Starting deferred link check...")
                
                if (sharedPreferences.getBoolean("ulink_deferred_checked", false)) {
                    logDebug("Deferred link already checked, skipping")
                    return@launch
                }

                // Try to get Install Referrer clickId for deterministic matching
                logDebug("Attempting to get Install Referrer clickId...")
                val clickId = getInstallReferrerClickId()
                
                if (clickId != null) {
                    logDebug("Will attempt deterministic match with clickId: $clickId")
                } else {
                    logDebug("No clickId available, will use fingerprint matching")
                }

                val fingerprint = mutableMapOf<String, Any>(
                    "os" to "android",
                    "model" to Build.MODEL,
                    "brand" to Build.BRAND,
                    "device" to Build.DEVICE,
                    "manufacturer" to Build.MANUFACTURER,
                    "product" to Build.PRODUCT,
                    "hardware" to Build.HARDWARE,
                    "id" to Build.ID
                )

                // Add common fields for browser matching
                try {
                    val displayMetrics = context.resources.displayMetrics
                    // Use Math.round to match browser's window.screen.width/height calculation
                    val width = Math.round(displayMetrics.widthPixels / displayMetrics.density)
                    val height = Math.round(displayMetrics.heightPixels / displayMetrics.density)
                    fingerprint["screenResolution"] = "${width}x${height}"
                    
                    fingerprint["timezone"] = TimeZone.getDefault().id
                    fingerprint["language"] = Locale.getDefault().toLanguageTag()
                    
                    logDebug("Fingerprint collected: resolution=${fingerprint["screenResolution"]}, timezone=${fingerprint["timezone"]}, language=${fingerprint["language"]}")
                } catch (e: Exception) {
                    logError("Error collecting common fingerprint fields", e)
                }

                val url = "https://api.ulink.ly/sdk/deferred/match"
                val headers = mapOf(
                    "Content-Type" to "application/json",
                    "X-App-Key" to config.apiKey
                )
                
                // Build body with fingerprint, clickId (from Install Referrer), and installationId (for attribution)
                val bodyMap = mutableMapOf<String, Any>("fingerprint" to fingerprint)
                
                // Add clickId from Install Referrer if available (for deterministic matching)
                clickId?.let { bodyMap["clickId"] = it }
                
                // Add installationId for attribution
                getInstallationId()?.let { bodyMap["installationId"] = it }
                
                val body: Map<String, Any> = bodyMap

                logInfo("Calling deferred match API: $url")
                val response = httpClient.postJson(url, body, headers)
                
                if (response.isSuccess) {
                    logDebug("Deferred match API response received successfully")
                    val json = response.parseJson()
                    val data = json?.get("data") as? JsonObject
                    val deepLink = data?.get("deepLink")?.toString()?.removeSurrounding("\"")
                    val matchType = json?.get("matchType")?.toString()?.removeSurrounding("\"")
                    
                    if (!deepLink.isNullOrEmpty() && deepLink != "null") {
                        logInfo("Matched deferred link: $deepLink (matchType: $matchType)")
                        // Handle the deep link with deferred flag
                        Uri.parse(deepLink)?.let { uri ->
                            handleDeepLink(uri, isDeferred = true, matchType = matchType)
                        }
                    } else {
                        logDebug("No deferred link matched or deepLink is null")
                    }
                } else {
                    logWarning("Deferred match API call failed: ${response.statusCode}")
                }

                sharedPreferences.edit().putBoolean("ulink_deferred_checked", true).apply()
                logDebug("Deferred link check completed")
            } catch (e: Exception) {
                logError("Error checking deferred link", e)
            }
        }
    }
    
    /**
     * Called when app comes to foreground
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (config.debug) {
            logDebug("App started - starting session")
        }
        scope.launch {
            if (!bootstrapCompleted) {
                // Retry bootstrap silently if it hasn't completed yet
                if (config.debug) {
                    logDebug("App started but bootstrap not yet completed - retrying bootstrap")
                }
                bootstrapSilent()
            }
            
            if (!bootstrapSucceeded) {
                pendingSessionStart = true
                if (config.debug) {
                    logDebug("Bootstrap failed - deferring session start")
                }
                return@launch
            }
            
            startSessionIfNeeded()
        }
    }
    
    /**
     * Called when app goes to background
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (config.debug) {
            logDebug("App stopped - ending session")
        }
        scope.launch {
            if (!bootstrapCompleted) {
                return@launch
            }
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
     * Gets the installation info including reinstall detection data.
     * 
     * This information is available after the SDK has completed bootstrapping.
     * If this is a reinstall, the returned object will have isReinstall=true
     * and previousInstallationId will contain the ID of the previous installation.
     * 
     * @return ULinkInstallationInfo or null if bootstrap hasn't completed
     */
    fun getInstallationInfo(): ULinkInstallationInfo? {
        return _installationInfo
    }
    
    /**
     * Checks if the current installation is a reinstall.
     * 
     * @return true if this is a reinstall, false otherwise or if bootstrap hasn't completed
     */
    fun isReinstall(): Boolean {
        return _installationInfo?.isReinstall ?: false
    }
    
    /**
     * Gets the persistent device ID that survives app reinstalls.
     * This is used for reinstall detection.
     * 
     * @return The persistent device ID or null if unavailable
     */
    fun getPersistentDeviceId(): String? {
        return DeviceInfoUtils.getPersistentDeviceId(context)
    }
    
    /**
     * Generates a new installation ID
     */
    private fun generateInstallationId() {
        val installationId = UUID.randomUUID().toString()
        sharedPreferences.edit().putString(KEY_INSTALLATION_ID, installationId).apply()
        
        if (config.debug) {
            logDebug("Generated new installation ID: $installationId")
        }
    }
    
    /**
     * Loads installation token from SharedPreferences
     */
    private fun loadInstallationToken() {
        try {
            installationToken = sharedPreferences.getString(KEY_INSTALLATION_TOKEN, null)
            if (config.debug && installationToken != null) {
                logDebug("Loaded installation token from storage")
            }
        } catch (e: Exception) {
            if (config.debug) {
                logError("Failed to load installation token", e)
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
                logDebug("Saved installation token to storage")
            }
        } catch (e: Exception) {
            if (config.debug) {
                logError("Failed to save installation token", e)
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
    /**
     * Bootstrap the SDK by tracking installation and starting a session.
     * 
     * This method now throws ULinkInitializationError instead of returning Boolean.
     * 
     * @throws ULinkInitializationError if bootstrap fails
     */
    private suspend fun bootstrap() {
        withContext(Dispatchers.IO) {
            val installationId = getInstallationId() 
                ?: throw ULinkInitializationError.bootstrapFailed(
                    statusCode = 0,
                    message = "Installation ID is required for bootstrap"
                )
            
            if (config.debug) {
                logDebug("Bootstrapping installation and session")
            }
            
            val bootstrapData = buildBootstrapBodyMap()
            val url = "${config.baseUrl}/sdk/bootstrap"
            val headers = mutableMapOf(
                "X-App-Key" to config.apiKey,
                "Content-Type" to "application/json",
                "X-ULink-Client" to "sdk-android",
                "X-ULink-Client-Version" to SDK_VERSION,
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
            
            try {
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
                                logDebug("Received and saved installation token")
                            }
                        }
                        
                        // Handle session ID
                        val sessionId = json["sessionId"]?.toString()?.removeSurrounding("\"")
                        if (!sessionId.isNullOrEmpty()) {
                            currentSessionId = sessionId
                            if (config.debug) {
                                logDebug("Bootstrap ensured session: $sessionId")
                            }
                        }
                        
                        // Parse and store installation info (including reinstall detection)
                        val persistentDeviceId = DeviceInfoUtils.getPersistentDeviceId(context)
                        _installationInfo = ULinkInstallationInfo.fromJson(json, installationId).copy(
                            persistentDeviceId = persistentDeviceId
                        )
                        
                        // Emit reinstall event if detected
                        if (_installationInfo?.isReinstall == true) {
                            if (config.debug) {
                                logInfo("Reinstall detected! Previous installation: ${_installationInfo?.previousInstallationId}")
                            }
                            scope.launch {
                                _reinstallDetectedStream.emit(_installationInfo!!)
                            }
                        }
                        
                        if (config.debug) {
                            logInfo("Bootstrap completed successfully")
                            if (_installationInfo?.isReinstall == true) {
                                logDebug("Installation info: isReinstall=true, previousInstallationId=${_installationInfo?.previousInstallationId}")
                            }
                        }
                    } else {
                        throw ULinkInitializationError.bootstrapFailed(
                            statusCode = response.statusCode,
                            message = "Bootstrap response is not valid JSON"
                        )
                    }
                } else {
                    val errorMessage = "Bootstrap failed: HTTP ${response.statusCode}: ${response.body}"
                    logError(errorMessage)
                    throw ULinkInitializationError.bootstrapFailed(
                        statusCode = response.statusCode,
                        message = errorMessage
                    )
                }
            } catch (e: ULinkInitializationError) {
                throw e
            } catch (e: Exception) {
                val errorMessage = "Bootstrap error: ${e.message ?: "Unknown error"}"
                logError(errorMessage, e)
                throw ULinkInitializationError.bootstrapFailed(
                    statusCode = 0,
                    message = errorMessage,
                    cause = e
                )
            }
        }
    }
    
    /**
     * Bootstrap guard - ensures bootstrap has completed successfully before allowing SDK operations.
     * 
     * Call this at the start of any method that requires the SDK to be fully initialized.
     * 
     * @throws ULinkInitializationError if bootstrap hasn't completed or failed
     */
    private fun ensureBootstrapCompleted() {
        if (!bootstrapCompleted) {
            logError("SDK method called before initialization complete")
            throw ULinkInitializationError.bootstrapFailed(
                statusCode = 0,
                message = "SDK initialization not complete. Ensure initialize() completes successfully before calling SDK methods."
            )
        }
        
        if (!bootstrapSucceeded) {
            logError("SDK method called after initialization failed")
            throw ULinkInitializationError.bootstrapFailed(
                statusCode = 0,
                message = "SDK initialization failed. Check the error from initialize() method and retry initialization."
            )
        }
    }
    
    /**
     * Silent bootstrap for lifecycle retries (non-throwing version)
     * Used when app becomes active and bootstrap needs to be retried
     */
    private suspend fun bootstrapSilent() {
        try {
            bootstrap()
            bootstrapSucceeded = true
            bootstrapCompleted = true
        } catch (e: ULinkInitializationError) {
            logError("Bootstrap error (silent)", e)
            bootstrapSucceeded = false
            bootstrapCompleted = true
        } catch (e: Exception) {
            logError("Bootstrap error (silent)", e)
            bootstrapSucceeded = false
            bootstrapCompleted = true
        }
    }
    
    /**
     * Build bootstrap request body
     */
    private fun buildBootstrapBodyMap(): Map<String, Any> {
        val bootstrapData = mutableMapOf<String, Any>()
        
        // Installation identifiers
        val currentInstallationId = getInstallationId()
        currentInstallationId?.let { bootstrapData["installationId"] = it }
        DeviceInfoUtils.getDeviceId(context)?.let { bootstrapData["deviceId"] = it }
        
        // Persistent device ID for reinstall detection (survives app reinstalls)
        DeviceInfoUtils.getPersistentDeviceId(context)?.let { bootstrapData["persistentDeviceId"] = it }
        
        // Device details
        bootstrapData["deviceModel"] = Build.MODEL
        bootstrapData["deviceManufacturer"] = Build.MANUFACTURER
        
        // OS / App info
        bootstrapData["osName"] = DeviceInfoUtils.getOsName()
        bootstrapData["osVersion"] = DeviceInfoUtils.getOsVersion()
        DeviceInfoUtils.getAppVersion(context)?.let { bootstrapData["appVersion"] = it }
        DeviceInfoUtils.getAppBuild(context)?.let { bootstrapData["appBuild"] = it }
        
        // Locale information
        bootstrapData["language"] = DeviceInfoUtils.getLanguage()
        bootstrapData["timezone"] = DeviceInfoUtils.getTimezone()
        
        // Device state
        bootstrapData["networkType"] = DeviceInfoUtils.getNetworkType(context)
        bootstrapData["deviceOrientation"] = DeviceInfoUtils.getDeviceOrientation(context)
        DeviceInfoUtils.getBatteryLevel(context)?.let { bootstrapData["batteryLevel"] = it }
        DeviceInfoUtils.isCharging(context)?.let { bootstrapData["isCharging"] = it }
        
        // Metadata with client info (consumed by backend)
        bootstrapData["metadata"] = mapOf(
            "client" to mapOf(
                "type" to "sdk-android",
                "version" to SDK_VERSION,
                "platform" to "android"
            )
        )
        
        return bootstrapData
    }

    private suspend fun startSessionIfNeeded() {
        if (sessionState == SessionState.IDLE || sessionState == SessionState.FAILED) {
            startSession()
        }
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
    fun handleDeepLink(uri: Uri, isDeferred: Boolean = false, matchType: String? = null) {
        if (config.debug) {
            logDebug("Handling deep link: $uri (isDeferred: $isDeferred, matchType: $matchType)")
        }
        
        scope.launch {
            try {
                val resolvedData = resolveLink(uri.toString())
                if (resolvedData.success && resolvedData.data != null) {
                    val linkData = ULinkResolvedData.fromJsonObject(resolvedData.data)
                    linkData.let {
                        // Inject isDeferred and matchType if this came from deferred deep linking
                        val enrichedLinkData = if (isDeferred) {
                            it.copy(isDeferred = true, matchType = matchType)
                        } else {
                            it
                        }
                        
                        if (config.debug) {
                            logInfo("Resolved link data:")
                            logDebug("  Type: ${enrichedLinkData.type}")
                            logDebug("  Slug: ${enrichedLinkData.slug}")
                            logDebug("  Is Deferred: ${enrichedLinkData.isDeferred}")
                            logDebug("  Match Type: ${enrichedLinkData.matchType}")
                            logDebug("  Fallback URL: ${enrichedLinkData.fallbackUrl}")
                            logDebug("  Parameters: ${enrichedLinkData.parameters}")
                            logDebug("  Metadata: ${enrichedLinkData.metadata}")
                            logDebug("  Raw data: ${enrichedLinkData.rawData}")
                        }
                        
                        lastLinkData = enrichedLinkData
                        saveLastLinkData(enrichedLinkData)
                        
                        // Emit to appropriate stream based on type
                        when (enrichedLinkData.type) {
                            "dynamic" -> _dynamicLinkStream.emit(enrichedLinkData)
                            "unified" -> _unifiedLinkStream.emit(enrichedLinkData)
                            else -> _dynamicLinkStream.emit(enrichedLinkData) // Default to dynamic
                        }
                    }
                } else {
                    if (config.debug) {
                        logWarning("Failed to resolve link: ${resolvedData.error}")
                    }
                }
            } catch (e: Exception) {
                if (config.debug) {
                    logError("Failed to handle deep link", e)
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
            logDebug("Set initial URI: $uri")
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
                logDebug("Processing URI: ${uri}")
            }
            
            // Always try to resolve the URI with the server to determine if it's a ULink
            if (config.debug) {
                logDebug("Querying server to resolve URI...")
            }
            val resolveResponse = resolveLink(uri.toString())
            
            if (resolveResponse.success && resolveResponse.data != null) {
                val resolvedData = ULinkResolvedData.fromJsonObject(resolveResponse.data)
                if (config.debug) {
                    logInfo("Successfully resolved ULink data: ${resolvedData.rawData}")
                }
                resolvedData
            } else {
                // Differentiate between network errors and non-ULink responses
                if (resolveResponse.error != null) {
                    if (resolveResponse.error.contains("network") ||
                        resolveResponse.error.contains("timeout") ||
                        resolveResponse.error.contains("connection")) {
                        if (config.debug) {
                            logWarning("Network error while resolving URI: ${resolveResponse.error}")
                        }
                    } else {
                        if (config.debug) {
                            logDebug("URI is not a ULink: ${resolveResponse.error}")
                        }
                    }
                } else {
                    if (config.debug) {
                        logDebug("Server responded but URI is not a ULink")
                    }
                }
                null
            }
        } catch (e: Exception) {
            if (config.debug) {
                logError("Exception while processing ULink URI: $e")
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
                    val linkData = ULinkResolvedData.fromJsonObject(resolvedData.data)
                    if (config.debug) {
                        logInfo("Initial deep link resolved:")
                        logDebug("  Type: ${linkData.type}")
                        logDebug("  Slug: ${linkData.slug}")
                        logDebug("  Fallback URL: ${linkData.fallbackUrl}")
                        logDebug("  Parameters: ${linkData.parameters}")
                        logDebug("  Metadata: ${linkData.metadata}")
                        logDebug("  Raw data: ${linkData.rawData}")
                    }
                    linkData
                } else {
                    if (config.debug) {
                        logWarning("Failed to resolve initial deep link: ${resolvedData.error}")
                    }
                    null
                }
            } catch (e: Exception) {
                if (config.debug) {
                    logError("Failed to resolve initial deep link", e)
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
        // Ensure bootstrap completed before allowing link creation
        ensureBootstrapCompleted()
        
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
                    logError("Failed to create link", e)
                }
                ULinkResponse.error(e.message ?: "Unknown error")
            }
        }
    }
    
    /**
     * Resolves a dynamic link
     */
    suspend fun resolveLink(url: String): ULinkResponse {
        // Ensure bootstrap completed before allowing link resolution
        ensureBootstrapCompleted()
        
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
                    logError("Failed to resolve link", e)
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
                            logInfo("Session started: $sessionId")
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
                    logError("Failed to start session", e)
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
                        logInfo("Session ended: $sessionId")
                    }
                    true
                } else {
                    sessionState = SessionState.FAILED
                    if (config.debug) {
                        logError("Failed to end session: ${response.body}")
                    }
                    false
                }
            } catch (e: Exception) {
                sessionState = SessionState.FAILED
                if (config.debug) {
                    logError("Failed to end session", e)
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
                        logError("Error waiting for session initialization", e)
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
                    logDebug("Automatic deep link detected: $uri")
                }
                handleDeepLink(uri)
            }
        }
    }

    // ========== JAVA-FRIENDLY COMPLETABLE FUTURE WRAPPERS ==========

    /**
     * Java-friendly async method for creating links.
     * Returns a CompletableFuture that completes with the link creation response.
     *
     * Example usage in Java:
     * ```java
     * ulink.createLinkAsync(parameters)
     *     .thenAccept(response -> {
     *         if (response.getSuccess()) {
     *             String url = response.getUrl();
     *             // Use the URL
     *         }
     *     })
     *     .exceptionally(error -> {
     *         Log.e("ULink", "Failed to create link", error);
     *         return null;
     *     });
     * ```
     *
     * @param parameters The link parameters
     * @return CompletableFuture that completes with ULinkResponse
     */
    fun createLinkAsync(parameters: ULinkParameters): java.util.concurrent.CompletableFuture<ULinkResponse> {
        val future = java.util.concurrent.CompletableFuture<ULinkResponse>()
        scope.launch {
            try {
                val response = createLink(parameters)
                future.complete(response)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * Java-friendly async method for resolving links.
     * Returns a CompletableFuture that completes with the link resolution response.
     *
     * Example usage in Java:
     * ```java
     * ulink.resolveLinkAsync("https://links.shared.ly/my-link")
     *     .thenAccept(response -> {
     *         if (response.getSuccess()) {
     *             // Handle resolved link data
     *         }
     *     });
     * ```
     *
     * @param url The URL to resolve
     * @return CompletableFuture that completes with ULinkResponse
     */
    fun resolveLinkAsync(url: String): java.util.concurrent.CompletableFuture<ULinkResponse> {
        val future = java.util.concurrent.CompletableFuture<ULinkResponse>()
        scope.launch {
            try {
                val response = resolveLink(url)
                future.complete(response)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * Java-friendly async method for ending the current session.
     * Returns a CompletableFuture that completes with a boolean indicating success.
     *
     * Example usage in Java:
     * ```java
     * ulink.endSessionAsync()
     *     .thenAccept(success -> {
     *         if (success) {
     *             Log.d("ULink", "Session ended successfully");
     *         }
     *     });
     * ```
     *
     * @return CompletableFuture that completes with Boolean
     */
    fun endSessionAsync(): java.util.concurrent.CompletableFuture<Boolean> {
        val future = java.util.concurrent.CompletableFuture<Boolean>()
        scope.launch {
            try {
                val success = endSession()
                future.complete(success)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * Java-friendly async method for getting initial deep link.
     * Returns a CompletableFuture that completes with the initial deep link data (if any).
     *
     * Example usage in Java:
     * ```java
     * ulink.getInitialDeepLinkAsync()
     *     .thenAccept(data -> {
     *         if (data != null) {
     *             Log.d("ULink", "Initial deep link: " + data.getSlug());
     *         }
     *     });
     * ```
     *
     * @return CompletableFuture that completes with ULinkResolvedData or null
     */
    fun getInitialDeepLinkAsync(): java.util.concurrent.CompletableFuture<ULinkResolvedData?> {
        val future = java.util.concurrent.CompletableFuture<ULinkResolvedData?>()
        scope.launch {
            try {
                val data = getInitialDeepLink()
                future.complete(data)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    /**
     * Java-friendly async method for processing ULink URI.
     * Returns a CompletableFuture that completes with the resolved link data (if valid).
     *
     * Example usage in Java:
     * ```java
     * Uri uri = intent.getData();
     * if (uri != null) {
     *     ulink.processULinkUriAsync(uri)
     *         .thenAccept(data -> {
     *             if (data != null) {
     *                 // Handle resolved link
     *             }
     *         });
     * }
     * ```
     *
     * @param uri The URI to process
     * @return CompletableFuture that completes with ULinkResolvedData or null
     */
    fun processULinkUriAsync(uri: android.net.Uri): java.util.concurrent.CompletableFuture<ULinkResolvedData?> {
        val future = java.util.concurrent.CompletableFuture<ULinkResolvedData?>()
        scope.launch {
            try {
                val data = processULinkUri(uri)
                future.complete(data)
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }
        return future
    }

    // ========== END OF JAVA-FRIENDLY WRAPPERS ==========

    // ========== CALLBACK-BASED METHODS FOR JAVA ==========

    /**
     * Create link with callbacks (Java-friendly).
     * Alternative to [createLinkAsync] for developers who prefer callbacks.
     *
     * **Threading:** Both callbacks are invoked on the main (UI) thread.
     *
     * **Error Handling:** If [onError] is not provided and an error occurs,
     * the error is logged but not thrown. Provide an error callback for explicit handling.
     *
     * Example usage in Java:
     * ```java
     * ULinkParameters params = ULinkParameters.dynamic(
     *     "links.shared.ly",
     *     "my-slug",
     *     null, null, "https://example.com",
     *     null, null, null
     * );
     *
     * ulink.createLink(params,
     *     response -> {
     *         if (response.getSuccess()) {
     *             String url = response.getUrl();
     *             Log.d("ULink", "Link created: " + url);
     *         }
     *     },
     *     error -> {
     *         Log.e("ULink", "Error creating link", error);
     *     }
     * );
     * ```
     *
     * @param parameters Link creation parameters
     * @param onSuccess Callback invoked on main thread with link creation response
     * @param onError Optional callback invoked on main thread when an error occurs.
     *                If null, errors are logged to the SDK log stream.
     * @see createLinkAsync
     * @see createLink
     */
    @JvmOverloads
    fun createLink(
        parameters: ULinkParameters,
        onSuccess: (ULinkResponse) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val response = createLink(parameters)
                onSuccess(response)
            } catch (e: Exception) {
                onError?.invoke(e) ?: logError("Error creating link: ${e.message}")
            }
        }
    }

    /**
     * Resolve link with callbacks (Java-friendly).
     * Alternative to [resolveLinkAsync] for developers who prefer callbacks.
     *
     * **Threading:** Both callbacks are invoked on the main (UI) thread.
     *
     * **Error Handling:** If [onError] is not provided and an error occurs,
     * the error is logged but not thrown. Provide an error callback for explicit handling.
     *
     * Example usage in Java:
     * ```java
     * ulink.resolveLink("https://links.shared.ly/my-link",
     *     response -> {
     *         if (response.getSuccess()) {
     *             Log.d("ULink", "Link resolved successfully");
     *         }
     *     },
     *     error -> {
     *         Log.e("ULink", "Error resolving link", error);
     *     }
     * );
     * ```
     *
     * @param url URL to resolve
     * @param onSuccess Callback invoked on main thread with resolution response
     * @param onError Optional callback invoked on main thread when an error occurs.
     *                If null, errors are logged to the SDK log stream.
     * @see resolveLinkAsync
     * @see resolveLink
     */
    @JvmOverloads
    fun resolveLink(
        url: String,
        onSuccess: (ULinkResponse) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val response = resolveLink(url)
                onSuccess(response)
            } catch (e: Exception) {
                onError?.invoke(e) ?: logError("Error resolving link: ${e.message}")
            }
        }
    }

    /**
     * End session with callback (Java-friendly).
     * Alternative to [endSessionAsync] for developers who prefer callbacks.
     *
     * **Threading:** Both callbacks are invoked on the main (UI) thread.
     *
     * **Error Handling:** If [onError] is not provided and an error occurs,
     * the error is logged but not thrown. Provide an error callback for explicit handling.
     *
     * Example usage in Java:
     * ```java
     * ulink.endSession(
     *     success -> {
     *         if (success) {
     *             Log.d("ULink", "Session ended successfully");
     *         }
     *     },
     *     error -> {
     *         Log.e("ULink", "Error ending session", error);
     *     }
     * );
     * ```
     *
     * @param onSuccess Callback invoked on main thread when session ends
     * @param onError Optional callback invoked on main thread when an error occurs.
     *                If null, errors are logged to the SDK log stream.
     * @see endSessionAsync
     * @see endSession
     */
    @JvmOverloads
    fun endSession(
        onSuccess: (Boolean) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        scope.launch {
            try {
                val success = endSession()
                onSuccess(success)
            } catch (e: Exception) {
                onError?.invoke(e) ?: logError("Error ending session: ${e.message}")
            }
        }
    }

    // ========== END OF CALLBACK-BASED METHODS ==========

    /**
     * Disposes the SDK and cleans up resources
     */
    fun dispose() {
        // Clean up listener jobs
        listenerJobs.forEach { it.cancel() }
        listenerJobs.clear()

        // End session and cancel scope
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
            logInfo("ULink SDK disposed")
        }
    }
}