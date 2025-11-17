# ULink Android SDK

The ULink Android SDK provides a comprehensive solution for creating, managing, and tracking dynamic links in Android applications. This SDK offers the same functionality as the Flutter ULink SDK, allowing you to create dynamic links, handle deep links, track user sessions, and manage installations.

## Features

- **Dynamic Link Creation**: Create dynamic links with custom parameters and social media tags
- **Deep Link Handling**: Automatically handle incoming deep links and extract parameters
- **Session Tracking**: Track user sessions with automatic device information collection
- **Installation Tracking**: Monitor app installations and user engagement
- **Unified Links**: Support for unified links across multiple platforms
- **Social Media Integration**: Add Open Graph tags for better social media sharing
- **Real-time Streams**: Listen to deep link events in real-time

## Installation

### Using JitPack

Add the JitPack repository to your project's `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add the dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.github.yourusername:android-ulink-sdk:1.0.0")
}
```

### Local Installation

1. Clone this repository
2. Build the library: `./gradlew build`
3. Publish to local Maven: `./gradlew publishToMavenLocal`
4. Add the dependency to your project:

```kotlin
dependencies {
    implementation("ly.ulink.sdk:android-sdk:1.0.0")
}
```

## Quick Start

### 1. Initialize the SDK

```kotlin
import ly.ulink.sdk.ULink
import ly.ulink.sdk.ULinkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val config = ULinkConfig(
            apiKey = "your-api-key",
            baseUrl = "https://api.ulink.ly", // Optional, defaults to this URL
            debug = BuildConfig.DEBUG
        )
        
        ULink.initialize(this, config)
    }
}
```

### 2. Handle Deep Links in Your Activity

#### Option A: Automatic Deep Link Integration (Recommended)

With `enableDeepLinkIntegration = true` in your config, the SDK automatically handles deep links:

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var ulink: ULink
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize with automatic deep link integration
        val config = ULinkConfig(
            apiKey = "your_api_key",
            baseUrl = "https://api.ulink.ly",
            enableDeepLinkIntegration = true // Enables automatic handling
        )
        ulink = ULink.initialize(this, config)
        
        // Just listen to the streams - no manual intent handling needed!
        observeDeepLinks()
    }
    
    private fun observeDeepLinks() {
        lifecycleScope.launch {
            ulink.dynamicLinkStream.collect { linkData ->
                handleDeepLink(linkData)
            }
        }
        
        lifecycleScope.launch {
            ulink.unifiedLinkStream.collect { linkData ->
                handleDeepLink(linkData)
            }
        }
    }
    
    private fun handleDeepLink(data: ULinkResolvedData) {
        // Handle the deep link data
        println("Received deep link: ${data.slug}")
        println("Parameters: ${data.parameters}")
        println("Fallback URL: ${data.fallbackUrl}")
    }
}
```

#### Option B: Manual Deep Link Handling

If you prefer manual control or need backward compatibility:

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var ulink: ULink
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize with manual handling
        val config = ULinkConfig(
            apiKey = "your_api_key",
            // enableDeepLinkIntegration = false // Disable automatic handling
        )
        ulink = ULink.initialize(this, config)
        
        // Handle initial intent
        handleIntent(intent)
        observeDeepLinks()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        intent?.data?.let { uri ->
            ulink.handleDeepLink(uri)
        }
    }
    
    private fun observeDeepLinks() {
        lifecycleScope.launch {
            ulink.dynamicLinkStream.collect { linkData ->
                handleDeepLink(linkData)
            }
        }
        
        lifecycleScope.launch {
            ulink.unifiedLinkStream.collect { linkData ->
                handleDeepLink(linkData)
            }
        }
    }
    
    private fun handleDeepLink(data: ULinkResolvedData) {
        // Handle the deep link data
        println("Received deep link: ${data.slug}")
        println("Parameters: ${data.parameters}")
        println("Fallback URL: ${data.fallbackUrl}")
    }
}
```

### 3. Create Dynamic Links

```kotlin
// Create a dynamic link
val parameters = ULinkParameters.createDynamicLink(
    slug = "my-product",
    iosUrl = "https://apps.apple.com/app/myapp",
    androidUrl = "https://play.google.com/store/apps/details?id=com.myapp",
    fallbackUrl = "https://mywebsite.com/product",
    parameters = mapOf(
        "productId" to "12345",
        "category" to "electronics"
    ),
    socialMediaTags = SocialMediaTags(
        ogTitle = "Check out this amazing product!",
        ogDescription = "This product will change your life",
        ogImage = "https://mywebsite.com/product-image.jpg"
    )
)

lifecycleScope.launch {
    val response = ulink.createLink(parameters)
    if (response.success) {
        val dynamicLink = response.url
        // Share the dynamic link
        shareLink(dynamicLink)
    } else {
        // Handle error
        println("Error creating link: ${response.error}")
    }
}
```

### 4. Create Unified Links

```kotlin
// Create a unified link
val parameters = ULinkParameters.createUnifiedLink(
    slug = "my-content",
    iosUrl = "myapp://content/123",
    androidUrl = "myapp://content/123",
    fallbackUrl = "https://mywebsite.com/content/123",
    parameters = mapOf(
        "contentId" to "123",
        "type" to "article"
    )
)

lifecycleScope.launch {
    val response = ulink.createLink(parameters)
    // Handle response...
}
```

### 5. Session Management

```kotlin
// Start a session with custom metadata
lifecycleScope.launch {
    val sessionResponse = ulink.startSession(
        metadata = mapOf(
            "userId" to "user123",
            "campaign" to "summer2024"
        )
    )
    
    if (sessionResponse.success) {
        println("Session started: ${sessionResponse.sessionId}")
    }
}

// End the current session
lifecycleScope.launch {
    ulink.endSession()
}

// Check if there's an active session
val hasActiveSession = ulink.hasActiveSession()
val currentSessionId = ulink.getCurrentSessionId()
```

## API Reference

### ULink Class

The main class for interacting with the ULink SDK.

#### Methods

- `initialize(context: Context, config: ULinkConfig)`: Initialize the SDK
- `getInstance(): ULink`: Get the singleton instance
- `createLink(parameters: ULinkParameters): ULinkResponse`: Create a dynamic or unified link
- `resolveLink(url: String): ULinkResolvedData?`: Resolve a ULink URL to extract data
- `getInitialDeepLink(): ULinkResolvedData?`: Get the initial deep link that opened the app
- `getInitialUri(): Uri?`: Get the raw initial URI
- `getLastLinkData(): ULinkResolvedData?`: Get the last received link data
- `startSession(metadata: Map<String, Any>? = null): ULinkSessionResponse`: Start a new session
- `endSession()`: End the current session
- `hasActiveSession(): Boolean`: Check if there's an active session
- `getCurrentSessionId(): String?`: Get the current session ID
- `dispose()`: Clean up resources

#### Properties

- `deepLinkStream: Flow<ULinkResolvedData?>`: Stream of deep link events
- `unifiedLinkStream: Flow<ULinkResolvedData?>`: Stream of unified link events

### Data Classes

#### ULinkConfig

```kotlin
data class ULinkConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.ulink.ly",
    val debug: Boolean = false
)
```

#### ULinkParameters

```kotlin
data class ULinkParameters(
    val type: ULinkType,
    val slug: String,
    val iosUrl: String? = null,
    val androidUrl: String? = null,
    val fallbackUrl: String? = null,
    val parameters: Map<String, Any>? = null,
    val socialMediaTags: SocialMediaTags? = null,
    val metadata: Map<String, Any>? = null
)
```

#### ULinkResponse

```kotlin
data class ULinkResponse(
    val success: Boolean,
    val url: String? = null,
    val error: String? = null,
    val data: Map<String, Any>? = null
)
```

#### ULinkResolvedData

```kotlin
data class ULinkResolvedData(
    val slug: String? = null,
    val iosUrl: String? = null,
    val androidUrl: String? = null,
    val fallbackUrl: String? = null,
    val parameters: Map<String, Any>? = null,
    val socialMediaTags: SocialMediaTags? = null,
    val metadata: Map<String, Any>? = null,
    val type: ULinkType? = null,
    val rawData: Map<String, Any>? = null
)
```

## Advanced Usage

### Custom Deep Link Handling

```kotlin
// Handle deep links manually
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.data?.let { uri ->
        lifecycleScope.launch {
            val resolvedData = ulink.resolveLink(uri.toString())
            resolvedData?.let {
                handleDeepLink(it)
            }
        }
    }
}
```

### Error Handling

```kotlin
lifecycleScope.launch {
    try {
        val response = ulink.createLink(parameters)
        if (!response.success) {
            when {
                response.error?.contains("unauthorized") == true -> {
                    // Handle authentication error
                }
                response.error?.contains("network") == true -> {
                    // Handle network error
                }
                else -> {
                    // Handle other errors
                }
            }
        }
    } catch (e: Exception) {
        // Handle exceptions
        Log.e("ULink", "Error creating link", e)
    }
}
```

### Testing

```kotlin
// For testing, you can create a test instance
val testULink = ULink.createTestInstance(config)
```

## Requirements

- Android API level 24 (Android 7.0) or higher
- Kotlin 1.9.24 or higher
- AndroidX libraries

## Dependencies

The SDK uses the following dependencies:

- AndroidX Core KTX
- AndroidX Lifecycle
- Kotlin Coroutines
- Kotlinx Serialization

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions, please contact [support@ulink.ly](mailto:support@ulink.ly) or visit our [documentation](https://docs.ulink.ly).