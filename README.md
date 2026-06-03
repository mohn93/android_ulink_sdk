# ULink Android SDK

The ULink Android SDK provides a comprehensive solution for creating, managing, and tracking dynamic links in Android applications. This SDK offers the same functionality as the Flutter ULink SDK, allowing you to create dynamic links, handle deep links, track user sessions, and manage installations.

## Features

- **Dynamic Link Creation**: Create dynamic links with custom parameters and social media tags
- **Deep Link Handling**: Automatically handle incoming deep links and extract parameters
- **Session Tracking**: Automatic session management with lifecycle handling
- **Installation Tracking**: Monitor app installations and detect reinstalls
- **Unified Links**: Support for unified links across multiple platforms
- **Social Media Integration**: Add Open Graph tags for better social media sharing
- **Real-time Streams**: Listen to deep link events in real-time
- **Deferred Deep Links**: Handle deep links from app install referrers
- **Debug Logging**: Stream SDK logs for debugging

## AI-Assisted Setup

Using Claude Code, Cursor, Codex, or another AI coding agent? Install the ULink onboarding skill in one command:

```bash
npx skills add https://ulink.ly
```

Then ask your assistant to **"setup ulink"** — it'll detect your Android project, configure your ULink dashboard, edit `build.gradle` and `AndroidManifest.xml`, and verify App Links. Works with 50+ AI agents via the [open agent-skills CLI](https://github.com/vercel-labs/skills). [Learn more →](https://docs.ulink.ly/getting-started/ai-setup)

## Installation

### Using Maven Central (Recommended)

The ULink Android SDK is published to Maven Central and can be added to your project with a single dependency declaration.

Add the dependency to your app's build file:

#### Kotlin DSL (build.gradle.kts)

```kotlin
dependencies {
    implementation("ly.ulink:ulink-sdk:1.0.6")
}
```

#### Groovy DSL (build.gradle)

```groovy
dependencies {
    implementation 'ly.ulink:ulink-sdk:1.0.6'
}
```

Ensure Maven Central is in your repositories (usually configured in `settings.gradle.kts` or `settings.gradle`):

#### Kotlin DSL (settings.gradle.kts)

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

#### Groovy DSL (settings.gradle)

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

### Local Installation (Development Only)

For local development or testing unreleased versions:

1. Clone this repository
2. Build the library: `./gradlew build`
3. Publish to local Maven: `./gradlew publishToMavenLocal`
4. Add the dependency to your project:

```kotlin
dependencies {
    implementation("ly.ulink:ulink-sdk:1.0.6")
}
```

5. Add Maven Local to your repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}
```

## Using from Java

The ULink Android SDK is fully compatible with Java projects. The SDK provides two async patterns:

- **CompletableFuture** (Recommended): Modern Java 8+ approach with chainable operations
- **Callbacks**: Traditional callback-style for backward compatibility

For comprehensive Java integration examples, see [JAVA_INTEGRATION.md](JAVA_INTEGRATION.md).

Quick Java example:

```java
// Using CompletableFuture (Recommended)
ULink.initializeAsync(context, config)
    .thenAccept(ulink -> {
        Log.d("ULink", "SDK initialized");
    })
    .exceptionally(error -> {
        Log.e("ULink", "Init failed", error);
        return null;
    });

// Using Callbacks
ULink.initialize(context, config,
    ulink -> Log.d("ULink", "SDK initialized"),
    error -> Log.e("ULink", "Init failed", error)
);
```

## Quick Start

### 1. Initialize the SDK

**Kotlin:**

```kotlin
import ly.ulink.sdk.ULink
import ly.ulink.sdk.models.ULinkConfig

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = ULinkConfig(
            apiKey = "your-api-key",
            debug = BuildConfig.DEBUG,
            enableDeepLinkIntegration = true // Automatic deep link handling
        )

        ULink.initialize(this, config)
    }
}
```

**Java (CompletableFuture):**

```java
import ly.ulink.sdk.ULink;
import ly.ulink.sdk.models.ULinkConfig;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ULinkConfig config = new ULinkConfig(
            "your-api-key" // apiKey
        );

        ULink.initializeAsync(this, config)
            .thenAccept(ulink -> {
                Log.d("ULink", "SDK initialized");
            })
            .exceptionally(error -> {
                Log.e("ULink", "Initialization failed", error);
                return null;
            });
    }
}
```

### 2. Handle Deep Links in Your Activity

#### Option A: Automatic Deep Link Integration (Recommended)

With `enableDeepLinkIntegration = true` in your config, the SDK automatically handles deep links.

**Kotlin:**

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var ulink: ULink

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ulink = ULink.getInstance()

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

**Java:**

```java
public class MainActivity extends AppCompatActivity {
    private ULink ulink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ulink = ULink.getInstance();

        // Set up listeners (Java-friendly alternative to Kotlin Flows)
        ulink.setOnLinkListener(data -> {
            Log.d("ULink", "Dynamic link: " + data.getSlug());
            handleDeepLink(data);
        });

        ulink.setOnUnifiedLinkListener(data -> {
            Log.d("ULink", "Unified link: " + data.getSlug());
            handleDeepLink(data);
        });
    }

    private void handleDeepLink(ULinkResolvedData data) {
        // Handle the deep link data
        Log.d("ULink", "Parameters: " + data.getParameters());
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
            enableDeepLinkIntegration = false // Disable automatic handling
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
    }

    private fun handleDeepLink(data: ULinkResolvedData) {
        println("Received deep link: ${data.slug}")
        println("Parameters: ${data.parameters}")
    }
}
```

### 3. Create Dynamic Links

**Kotlin:**

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

**Java (CompletableFuture):**

```java
// Create dynamic link parameters
Map<String, Object> params = new HashMap<>();
params.put("productId", "12345");
params.put("category", "electronics");

SocialMediaTags socialTags = new SocialMediaTags(
    "Check out this amazing product!",
    "This product will change your life",
    "https://mywebsite.com/product-image.jpg"
);

ULinkParameters parameters = ULinkParameters.dynamic(
    "links.shared.ly",              // domain
    "my-product",                   // slug
    "https://apps.apple.com/...",   // iOS fallback
    "https://play.google.com/...",  // Android fallback
    "https://mywebsite.com/product", // fallback URL
    params,                         // parameters
    socialTags,                     // social media tags
    null                            // metadata
);

ulink.createLinkAsync(parameters)
    .thenAccept(response -> {
        if (response.getSuccess()) {
            String url = response.getUrl();
            shareLink(url);
        } else {
            Log.e("ULink", "Error: " + response.getError());
        }
    })
    .exceptionally(error -> {
        Log.e("ULink", "Failed to create link", error);
        return null;
    });
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

#### Idempotent Link Creation

Pass `externalId` to avoid creating duplicates when the same share button is tapped repeatedly. ULink scopes the key to your project — repeat calls return the existing link instead of creating a new one. See the [docs](https://docs.ulink.ly/create-links/idempotent-link-creation) for full semantics.

```kotlin
val parameters = ULinkParameters.unified(
    domain = "links.shared.ly",
    iosUrl = "myapp://post/456",
    androidUrl = "myapp://post/456",
    fallbackUrl = "https://myapp.com/post/456",
    externalId = "share:user:123:post:456"
)

lifecycleScope.launch {
    val response = ulink.createLink(parameters)
    // First call:        creates link, returns 201
    // Subsequent calls:  returns the same link, 200
}
```

### 5. Installation Tracking & Reinstall Detection

The SDK automatically tracks installations and can detect reinstalls:

**Kotlin:**

```kotlin
// Get installation information
val installationId = ulink.getInstallationId()
val installationInfo = ulink.getInstallationInfo()
val isReinstall = ulink.isReinstall()
val persistentDeviceId = ulink.getPersistentDeviceId()

// Listen for reinstall detection
lifecycleScope.launch {
    ulink.onReinstallDetected.collect { info ->
        println("Reinstall detected!")
        println("Previous installation ID: ${info.previousInstallationId}")
        println("Detected at: ${info.reinstallDetectedAt}")
    }
}
```

**Java:**

```java
// Get installation information
String installationId = ulink.getInstallationId();
ULinkInstallationInfo info = ulink.getInstallationInfo();
boolean isReinstall = ulink.isReinstall();

// Listen for reinstall detection
ulink.setOnReinstallListener(info -> {
    Log.d("ULink", "Reinstall detected! Previous ID: " + info.getPreviousInstallationId());
});
```

### 6. Session Management

Sessions are managed automatically by the SDK. You can end a session manually when the user logs out:

**Kotlin:**

```kotlin
// End the current session
lifecycleScope.launch {
    val success = ulink.endSession()
    if (success) {
        println("Session ended successfully")
    }
}

// Check session state
val hasActiveSession = ulink.hasActiveSession()
val currentSessionId = ulink.getCurrentSessionId()
val sessionState = ulink.getSessionState() // INITIALIZING, ACTIVE, ENDED, ERROR
val isInitializing = ulink.isSessionInitializing()
```

**Java (CompletableFuture):**

```java
// End the current session
ulink.endSessionAsync()
    .thenAccept(success -> {
        if (success) {
            Log.d("ULink", "Session ended successfully");
        }
    })
    .exceptionally(error -> {
        Log.e("ULink", "Failed to end session", error);
        return null;
    });
```

### 7. Deferred Deep Links

Deferred deep links are checked automatically on first launch when `autoCheckDeferredLink = true`. For manual control:

```kotlin
// Manually check for deferred deep links
ulink.checkDeferredLink()

// The result will be emitted to dynamicLinkStream
```

### 8. Query Parameter Passthrough

When a link has **`allowQueryPassthrough`** enabled (set via the ULink dashboard, REST API, or MCP — not a field in `ULinkParameters`), any query params appended to the link URL at click time (e.g. `?orderId=123`) are merged into the link's `parameters` before delivery. Passthrough values override stored params with the same key and always arrive as strings. This works on both direct app open and after a deferred install — no SDK changes needed.

```kotlin
lifecycleScope.launch {
    ulink.dynamicLinkStream.collect { data ->
        val orderId = data.parameters?.jsonObject?.get("orderId")?.jsonPrimitive?.content // e.g. "123"
    }
}
```

> **Note:** Passthrough values always arrive as strings and override any stored param with the same key. Enable `allowQueryPassthrough` on the link via the dashboard or REST API.

### 9. Debug Logging

Enable debug mode and listen to SDK logs:

**Kotlin:**

```kotlin
// Enable debug mode in config
val config = ULinkConfig(
    apiKey = "your-api-key",
    debug = true // Enable debug logging
)

// Listen to log stream
lifecycleScope.launch {
    ulink.logStream.collect { entry ->
        println("[${entry.level}] ${entry.tag}: ${entry.message}")
    }
}
```

**Java:**

```java
ulink.setOnLogListener(entry -> {
    Log.d("ULink", "[" + entry.getLevel() + "] " + entry.getMessage());
});
```

## API Reference

### ULink Class

The main class for interacting with the ULink SDK.

#### Initialization

| Method | Description |
|--------|-------------|
| `initialize(context, config)` | Initialize the SDK (Kotlin) |
| `initializeAsync(context, config)` | Initialize the SDK (Java CompletableFuture) |
| `initialize(context, config, onSuccess, onError)` | Initialize with callbacks (Java) |
| `getInstance()` | Get the singleton instance |

#### Link Operations

| Method | Description |
|--------|-------------|
| `createLink(parameters): ULinkResponse` | Create a dynamic or unified link (suspend) |
| `createLinkAsync(parameters)` | Create link (CompletableFuture) |
| `createLink(parameters, onSuccess, onError)` | Create link with callbacks |
| `resolveLink(url): ULinkResponse` | Resolve a ULink URL (suspend) |
| `resolveLinkAsync(url)` | Resolve link (CompletableFuture) |
| `resolveLink(url, onSuccess, onError)` | Resolve link with callbacks |
| `handleDeepLink(uri, isDeferred, matchType)` | Manually handle a deep link URI |
| `processULinkUri(uri): ULinkResolvedData?` | Process URI without emitting to streams |
| `checkDeferredLink()` | Manually check for deferred deep links |

#### Deep Link Data

| Method | Description |
|--------|-------------|
| `getInitialDeepLink(): ULinkResolvedData?` | Get the initial deep link that opened the app |
| `getInitialUri(): Uri?` | Get the raw initial URI |
| `getLastLinkData(): ULinkResolvedData?` | Get the last received link data |
| `setInitialUri(uri)` | Set the initial URI manually |

#### Installation & Reinstall

| Method | Description |
|--------|-------------|
| `getInstallationId(): String?` | Get the unique installation ID |
| `getInstallationInfo(): ULinkInstallationInfo?` | Get full installation info |
| `isReinstall(): Boolean` | Check if this is a reinstall |
| `getPersistentDeviceId(): String?` | Get the persistent device ID |

#### Session Management

| Method | Description |
|--------|-------------|
| `endSession(): Boolean` | End the current session (suspend) |
| `endSessionAsync()` | End session (CompletableFuture) |
| `endSession(onSuccess, onError)` | End session with callbacks |
| `hasActiveSession(): Boolean` | Check if there's an active session |
| `getCurrentSessionId(): String?` | Get the current session ID |
| `getSessionState(): SessionState` | Get session state (INITIALIZING, ACTIVE, ENDED, ERROR) |
| `isSessionInitializing(): Boolean` | Check if session is initializing |

#### Listeners (Java-friendly)

| Method | Description |
|--------|-------------|
| `setOnLinkListener(listener)` | Set dynamic link listener |
| `setOnUnifiedLinkListener(listener)` | Set unified link listener |
| `setOnReinstallListener(listener)` | Set reinstall detection listener |
| `setOnLogListener(listener)` | Set log listener |
| `removeOnLinkListener()` | Remove dynamic link listener |
| `removeOnUnifiedLinkListener()` | Remove unified link listener |
| `removeOnReinstallListener()` | Remove reinstall listener |
| `removeOnLogListener()` | Remove log listener |
| `removeAllListeners()` | Remove all listeners |

#### Cleanup

| Method | Description |
|--------|-------------|
| `dispose()` | Clean up resources |

#### Streams (Kotlin Flows)

| Property | Description |
|----------|-------------|
| `dynamicLinkStream` / `onLink` | Stream of dynamic link events |
| `unifiedLinkStream` / `onUnifiedLink` | Stream of unified link events |
| `onReinstallDetected` | Stream of reinstall detection events |
| `logStream` | Stream of SDK log entries |

### Data Classes

#### ULinkConfig

```kotlin
data class ULinkConfig(
    val apiKey: String,
    val baseUrl: String = "https://api.ulink.ly", // Internal use only, do not set
    val debug: Boolean = false,
    val enableDeepLinkIntegration: Boolean = true,
    val persistLastLinkData: Boolean = true,
    val lastLinkTimeToLiveSeconds: Long = 86400, // 24 hours
    val clearLastLinkOnRead: Boolean = false,
    val redactAllParametersInLastLink: Boolean = false,
    val redactedParameterKeysInLastLink: List<String> = emptyList(),
    val autoCheckDeferredLink: Boolean = true
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

#### ULinkInstallationInfo

```kotlin
data class ULinkInstallationInfo(
    val installationId: String,
    val isReinstall: Boolean = false,
    val previousInstallationId: String? = null,
    val reinstallDetectedAt: String? = null,
    val persistentDeviceId: String? = null
)
```

#### ULinkLogEntry

```kotlin
data class ULinkLogEntry(
    val level: String, // "debug", "info", "warning", "error"
    val tag: String,
    val message: String,
    val timestamp: Long
)
```

#### SessionState

```kotlin
enum class SessionState {
    INITIALIZING,
    ACTIVE,
    ENDED,
    ERROR
}
```

## Advanced Usage

### Custom Deep Link Handling

```kotlin
// Handle deep links manually
override fun onNewIntent(intent: Intent?) {
    super.onNewIntent(intent)
    intent?.data?.let { uri ->
        lifecycleScope.launch {
            val resolvedData = ulink.processULinkUri(uri)
            resolvedData?.let {
                // Handle without emitting to streams
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

### Link Data Persistence Options

```kotlin
val config = ULinkConfig(
    apiKey = "your-api-key",
    persistLastLinkData = true,           // Persist last link for later retrieval
    lastLinkTimeToLiveSeconds = 3600,     // TTL: 1 hour
    clearLastLinkOnRead = true,           // Clear after first read
    redactAllParametersInLastLink = false, // Keep parameters
    redactedParameterKeysInLastLink = listOf("password", "token") // Redact sensitive keys
)
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
- Google Play Install Referrer

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and questions, please contact [support@ulink.ly](mailto:support@ulink.ly) or visit our [documentation](https://docs.ulink.ly).
