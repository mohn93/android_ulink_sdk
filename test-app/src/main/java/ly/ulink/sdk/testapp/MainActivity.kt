package ly.ulink.sdk.testapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ly.ulink.sdk.ULink
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.models.ULinkParameters
import ly.ulink.sdk.models.SocialMediaTags
import ly.ulink.sdk.models.ULinkResolvedData
import ly.ulink.sdk.testapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var ulink: ULink
    
    companion object {
        private const val TAG = "ULinkTestApp"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initializeULink()
        setupUI()
        handleIntent(intent)
        observeDeepLinks()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update the activity's intent for ActivityLifecycleCallbacks
        handleIntent(intent)
    }
    
    private fun initializeULink() {
        val config = ULinkConfig(
            apiKey = "ulk_f666ab8b0113e922e014be89c47d04cacce70114a5b7f702", // Replace with your actual API key
            baseUrl = "https://api.ulink.ly", // Replace with your actual base URL
            debug = true,
            enableDeepLinkIntegration = true // Enable automatic deep link handling
        )
        
        ulink = ULink.initialize(this, config)
        Log.d(TAG, "ULink SDK initialized with automatic deep link integration")
    }
    
    private fun setupUI() {
        binding.apply {
            // Create Dynamic Link
            btnCreateDynamicLink.setOnClickListener {
                createDynamicLink()
            }
            
            // Create Unified Link
            btnCreateUnifiedLink.setOnClickListener {
                createUnifiedLink()
            }
            
            // Start Session
            btnStartSession.setOnClickListener {
                startSession()
            }
            
            // End Session
            btnEndSession.setOnClickListener {
                endSession()
            }
            
            // Get Installation ID
            btnGetInstallationId.setOnClickListener {
                val installationId = ulink.getInstallationId()
                updateLog("Installation ID: $installationId")
            }
            
            // Get Last Link Data
            btnGetLastLinkData.setOnClickListener {
                getLastLinkData()
            }
            
            // Get Initial Deep Link
            btnGetInitialDeeplink.setOnClickListener {
                getInitialDeepLink()
            }
            
            // Get Session State
            btnGetSessionState.setOnClickListener {
                getSessionState()
            }
            
            // Track Installation
            btnTrackInstallation.setOnClickListener {
                trackInstallation()
            }
            
            // Test Bootstrap
            btnTestBootstrap.setOnClickListener {
                testBootstrap()
            }
            
            // Test Error Handling
            btnTestErrorHandling.setOnClickListener {
                testErrorHandling()
            }
            
            // Resolve Link
            btnResolveLink.setOnClickListener {
                val url = etLinkUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    resolveLink(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URL to resolve", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Process ULink URI
            btnProcessUri.setOnClickListener {
                val url = etLinkUrl.text.toString().trim()
                if (url.isNotEmpty()) {
                    processULinkUri(url)
                } else {
                    Toast.makeText(this@MainActivity, "Please enter a URI to process", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createDynamicLink() {
        lifecycleScope.launch {
            try {
                val parameters = mapOf(
                    "product_id" to "12345",
                    "category" to "electronics",
                    "user_id" to "user123"
                )
                
                val metadata = mapOf(
                    "campaign" to "summer_sale",
                    "source" to "mobile_app"
                )
                
                val socialTags = SocialMediaTags(
                    ogTitle = "Amazing Product",
                    ogDescription = "Check out this amazing product!",
                    ogImage = "https://example.com/image.jpg"
                )
                
                val linkParams = ULinkParameters.dynamic(
                    slug = "summer-sale-product",
                    iosFallbackUrl = "https://apps.apple.com/app/your-app",
                    androidFallbackUrl = "https://play.google.com/store/apps/details?id=your.package",
                    fallbackUrl = "https://your-website.com",
                    parameters = parameters,
                    socialMediaTags = socialTags,
                    metadata = metadata
                )
                
                val response = ulink.createLink(linkParams)
                if (response.success) {
                    updateLog("Dynamic link created: ${response.url}")
                } else {
                    updateLog("Failed to create dynamic link: ${response.error}")
                }
            } catch (e: Exception) {
                updateLog("Error creating dynamic link: ${e.message}")
                Log.e(TAG, "Error creating dynamic link", e)
            }
        }
    }
    
    private fun createUnifiedLink() {
        lifecycleScope.launch {
            try {
                val parameters = mapOf(
                    "page" to "home",
                    "ref" to "mobile_app"
                )
                
                val linkParams = ULinkParameters.unified(
                    slug = "app-home",
                    iosUrl = "myapp://home",
                    androidUrl = "myapp://home",
                    fallbackUrl = "https://your-website.com/home",
                    parameters = parameters
                )
                
                val response = ulink.createLink(linkParams)
                if (response.success) {
                    updateLog("Unified link created: ${response.url}")
                } else {
                    updateLog("Failed to create unified link: ${response.error}")
                }
            } catch (e: Exception) {
                updateLog("Error creating unified link: ${e.message}")
                Log.e(TAG, "Error creating unified link", e)
            }
        }
    }
    
    private fun startSession() {
        lifecycleScope.launch {
            try {
                val metadata = mapOf(
                    "screen" to "main_activity",
                    "feature" to "testing"
                )
                
                val response = ulink.startSession(metadata)
                if (response.success) {
                    updateLog("Session started successfully")
                } else {
                    updateLog("Failed to start session: ${response.error}")
                }
            } catch (e: Exception) {
                updateLog("Error starting session: ${e.message}")
                Log.e(TAG, "Error starting session", e)
            }
        }
    }
    
    private fun endSession() {
        lifecycleScope.launch {
            try {
                val success = ulink.endSession()
                if (success) {
                    updateLog("Session ended successfully")
                } else {
                    updateLog("Failed to end session")
                }
            } catch (e: Exception) {
                updateLog("Error ending session: ${e.message}")
                Log.e(TAG, "Error ending session", e)
            }
        }
    }
    
    private fun resolveLink(url: String) {
        lifecycleScope.launch {
            try {
                val response = ulink.resolveLink(url)
                if (response.success && response.data != null) {
                    val resolvedData = ULinkResolvedData.fromJsonObject(response.data!!)
                    updateLog("Link resolved successfully:")
                    updateLog("Type: ${resolvedData.type}")
                    updateLog("Slug: ${resolvedData.slug}")
                    updateLog("Parameters: ${resolvedData.parameters}")
                    updateLog("Metadata: ${resolvedData.metadata}")
                } else {
                    updateLog("Failed to resolve link: ${response.error}")
                }
            } catch (e: Exception) {
                updateLog("Error resolving link: ${e.message}")
                Log.e(TAG, "Error resolving link", e)
            }
        }
    }
    
    // NOTE: This manual deep link handling is now OPTIONAL since enableDeepLinkIntegration=true
    // The SDK will automatically handle deep links through ActivityLifecycleCallbacks
    // This method is kept for demonstration purposes and backward compatibility
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            Log.d(TAG, "Manual deep link handling: $uri")
            updateLog("Deep link received (manual): $uri")
            // This call is now redundant with automatic integration enabled
            // ulink.handleDeepLink(uri)
        }
    }
    
    private fun observeDeepLinks() {
        lifecycleScope.launch {
            ulink.dynamicLinkStream.collect { linkData ->
                updateLog("Dynamic link resolved:")
                updateLog("Type: ${linkData.type}")
                updateLog("Parameters: ${linkData.parameters}")
                updateLog("Metadata: ${linkData.metadata}")
                Log.d(TAG, "Dynamic link data: $linkData")
            }
        }
        
        lifecycleScope.launch {
            ulink.unifiedLinkStream.collect { linkData ->
                updateLog("Unified link resolved:")
                updateLog("Type: ${linkData.type}")
                updateLog("Parameters: ${linkData.parameters}")
                updateLog("Metadata: ${linkData.metadata}")
                Log.d(TAG, "Unified link data: $linkData")
            }
        }
    }
    
    private fun getLastLinkData() {
        try {
            val lastLinkData = ulink.getLastLinkData()
            if (lastLinkData != null) {
                updateLog("Last Link Data:")
                updateLog("Type: ${lastLinkData.type}")
                updateLog("Slug: ${lastLinkData.slug}")
                updateLog("Parameters: ${lastLinkData.parameters}")
                updateLog("Metadata: ${lastLinkData.metadata}")
            } else {
                updateLog("No last link data available")
            }
        } catch (e: Exception) {
            updateLog("Error getting last link data: ${e.message}")
            Log.e(TAG, "Error getting last link data", e)
        }
    }
    
    private fun getInitialDeepLink() {
        lifecycleScope.launch {
            try {
                val initialDeepLink = ulink.getInitialDeepLink()
                if (initialDeepLink != null) {
                    updateLog("Initial Deep Link:")
                    updateLog("Type: ${initialDeepLink.type}")
                    updateLog("Slug: ${initialDeepLink.slug}")
                    updateLog("Parameters: ${initialDeepLink.parameters}")
                    updateLog("Metadata: ${initialDeepLink.metadata}")
                } else {
                    updateLog("No initial deep link available")
                }
            } catch (e: Exception) {
                updateLog("Error getting initial deep link: ${e.message}")
                Log.e(TAG, "Error getting initial deep link", e)
            }
        }
    }
    
    private fun getSessionState() {
        try {
            val sessionState = ulink.getSessionState()
            val currentSessionId = ulink.getCurrentSessionId()
            val hasActiveSession = ulink.hasActiveSession()
            val isInitializing = ulink.isSessionInitializing()
            
            updateLog("Session Information:")
            updateLog("State: $sessionState")
            updateLog("Current Session ID: $currentSessionId")
            updateLog("Has Active Session: $hasActiveSession")
            updateLog("Is Initializing: $isInitializing")
        } catch (e: Exception) {
            updateLog("Error getting session state: ${e.message}")
            Log.e(TAG, "Error getting session state", e)
        }
    }
    
    private fun trackInstallation() {
        lifecycleScope.launch {
            try {
                // Note: trackInstallation is now handled automatically by bootstrap
                // This method demonstrates manual tracking if needed
                updateLog("Installation tracking is handled automatically during SDK initialization")
                updateLog("Bootstrap process includes installation tracking")
            } catch (e: Exception) {
                updateLog("Error tracking installation: ${e.message}")
                Log.e(TAG, "Error tracking installation", e)
            }
        }
    }
    
    private fun testBootstrap() {
        try {
            updateLog("Bootstrap Status Information:")
            
            // Check installation ID (set during bootstrap)
            val installationId = ulink.getInstallationId()
            updateLog("Installation ID: ${installationId ?: "Not set"}")
            
            // Check session state (bootstrap initializes session)
            val sessionState = ulink.getSessionState()
            val hasActiveSession = ulink.hasActiveSession()
            val currentSessionId = ulink.getCurrentSessionId()
            
            updateLog("Session State: $sessionState")
            updateLog("Has Active Session: $hasActiveSession")
            updateLog("Current Session ID: ${currentSessionId ?: "None"}")
            
            // Check if session is still initializing
            val isInitializing = ulink.isSessionInitializing()
            updateLog("Session Initializing: $isInitializing")
            
            if (installationId != null && hasActiveSession) {
                updateLog("✅ Bootstrap completed successfully")
            } else if (isInitializing) {
                updateLog("⏳ Bootstrap in progress...")
            } else {
                updateLog("❌ Bootstrap may have failed")
            }
            
        } catch (e: Exception) {
            updateLog("Error checking bootstrap status: ${e.message}")
            Log.e(TAG, "Error checking bootstrap status", e)
        }
    }
    
    private fun processULinkUri(url: String) {
        lifecycleScope.launch {
            try {
                val uri = Uri.parse(url)
                val resolvedData = ulink.processULinkUri(uri)
                if (resolvedData != null) {
                    updateLog("URI processed successfully:")
                    updateLog("Type: ${resolvedData.type}")
                    updateLog("Slug: ${resolvedData.slug}")
                    updateLog("Parameters: ${resolvedData.parameters}")
                    updateLog("Metadata: ${resolvedData.metadata}")
                } else {
                    updateLog("URI is not a ULink or could not be processed")
                }
            } catch (e: Exception) {
                updateLog("Error processing URI: ${e.message}")
                Log.e(TAG, "Error processing URI", e)
            }
        }
    }
    
    private fun testErrorHandling() {
        updateLog("=== Testing Error Handling ===")
        
        // Test 1: Invalid URL for link creation
         lifecycleScope.launch {
             try {
                 updateLog("Test 1: Creating link with invalid parameters...")
                 val invalidParams = ULinkParameters.dynamic(
                     fallbackUrl = "invalid-url" // Invalid URL
                 )
                 ulink.createLink(invalidParams)
             } catch (e: Exception) {
                 updateLog("✅ Caught expected error for invalid URL: ${e.message}")
             }
         }
        
        // Test 2: Resolving non-existent link
        lifecycleScope.launch {
            try {
                updateLog("Test 2: Resolving non-existent link...")
                ulink.resolveLink("https://nonexistent.ulink.ly/invalid123")
            } catch (e: Exception) {
                updateLog("✅ Caught expected error for non-existent link: ${e.message}")
            }
        }
        
        // Test 3: Processing invalid URI
        lifecycleScope.launch {
            try {
                updateLog("Test 3: Processing invalid URI...")
                ulink.processULinkUri(android.net.Uri.parse("not-a-valid-uri"))
            } catch (e: Exception) {
                updateLog("✅ Caught expected error for invalid URI: ${e.message}")
            }
        }
        
        // Test 4: Session operations when not initialized
        try {
            updateLog("Test 4: Testing session state access...")
            val sessionState = ulink.getSessionState()
            updateLog("Session state retrieved: $sessionState")
            
            val hasSession = ulink.hasActiveSession()
            updateLog("Has active session: $hasSession")
            
            val sessionId = ulink.getCurrentSessionId()
            updateLog("Current session ID: ${sessionId ?: "None"}")
            
        } catch (e: Exception) {
            updateLog("✅ Caught error in session operations: ${e.message}")
        }
        
        // Test 5: Network timeout simulation (if applicable)
        updateLog("Test 5: Testing network resilience...")
        lifecycleScope.launch {
            try {
                // This will test the SDK's network error handling
                 val params = ULinkParameters.dynamic(
                     fallbackUrl = "https://httpstat.us/500" // Returns 500 error
                 )
                ulink.createLink(params)
            } catch (e: Exception) {
                updateLog("✅ Caught network error: ${e.message}")
            }
        }
        
        updateLog("=== Error Handling Tests Completed ===")
    }

    private fun updateLog(message: String) {
        runOnUiThread {
            val currentText = binding.tvLog.text.toString()
            val newText = if (currentText.isEmpty()) {
                message
            } else {
                "$currentText\n$message"
            }
            binding.tvLog.text = newText
            binding.scrollView.post {
                binding.scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
        Log.d(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        ulink.dispose()
    }
}