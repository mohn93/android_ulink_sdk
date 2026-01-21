# ULink Android SDK - Java Integration Guide

This guide provides comprehensive examples for using the ULink Android SDK from Java. The SDK is written in Kotlin but provides a fully Java-compatible API with two async patterns: **CompletableFuture** (recommended) and **callbacks** (traditional).

## Table of Contents

- [Installation](#installation)
- [Initialization](#initialization)
  - [Using CompletableFuture (Recommended)](#using-completablefuture-recommended)
  - [Using Callbacks](#using-callbacks)
- [Setting Up Listeners](#setting-up-listeners)
- [Creating Links](#creating-links)
  - [Dynamic Links](#dynamic-links)
  - [Unified Links](#unified-links)
- [Resolving Links](#resolving-links)
- [Session Management](#session-management)
- [Installation Tracking](#installation-tracking)
- [Debug Logging](#debug-logging)
- [Complete Example](#complete-example)
- [Error Handling](#error-handling)

## Installation

Add the dependency to your `app/build.gradle`:

```gradle
dependencies {
    implementation 'ly.ulink:ulink-sdk:1.0.6'
}
```

## Initialization

### Using CompletableFuture (Recommended)

CompletableFuture is the modern, recommended approach for Java async operations:

```java
import ly.ulink.sdk.ULink;
import ly.ulink.sdk.models.ULinkConfig;
import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ULinkConfig config = new ULinkConfig(
            "your-api-key",           // API key
            "https://api.ulink.ly",   // Base URL
            true,                      // Debug mode
            true,                      // Enable deep link integration
            true,                      // Persist last link data
            86400,                     // Last link TTL in seconds (24 hours)
            false,                     // Clear last link on read
            false,                     // Redact all parameters in last link
            Collections.emptyList(),   // Redacted parameter keys
            true                       // Auto check deferred links
        );

        ULink.initializeAsync(this, config)
            .thenAccept(ulink -> {
                Log.d("ULink", "SDK initialized successfully");
                // SDK is ready to use
            })
            .exceptionally(error -> {
                Log.e("ULink", "Failed to initialize SDK", error);
                return null;
            });
    }
}
```

### Using Callbacks

Callbacks provide a traditional approach familiar to many Java developers:

```java
import ly.ulink.sdk.ULink;
import ly.ulink.sdk.models.ULinkConfig;
import android.app.Application;
import android.util.Log;

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ULinkConfig config = new ULinkConfig(
            "your-api-key",
            "https://api.ulink.ly",
            true,                      // debug
            true,                      // enableDeepLinkIntegration
            true,                      // persistLastLinkData
            86400,                     // lastLinkTimeToLiveSeconds
            false,                     // clearLastLinkOnRead
            false,                     // redactAllParametersInLastLink
            Collections.emptyList(),   // redactedParameterKeysInLastLink
            true                       // autoCheckDeferredLink
        );

        ULink.initialize(this, config,
            ulink -> {
                Log.d("ULink", "SDK initialized successfully");
                // SDK is ready to use
            },
            error -> {
                Log.e("ULink", "Failed to initialize SDK", error);
            }
        );
    }
}
```

## Setting Up Listeners

The SDK provides Java-friendly listener interfaces as an alternative to Kotlin Flows:

```java
import ly.ulink.sdk.ULink;
import ly.ulink.sdk.listeners.OnLinkListener;
import ly.ulink.sdk.listeners.OnUnifiedLinkListener;
import ly.ulink.sdk.listeners.OnReinstallListener;
import ly.ulink.sdk.listeners.OnLogListener;

public class MainActivity extends AppCompatActivity {
    private ULink ulink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ulink = ULink.getInstance();

        // Listen for dynamic links (deep links)
        ulink.setOnLinkListener(data -> {
            Log.d("ULink", "Dynamic link received: " + data.getSlug());
            // Handle deep link data
            String slug = data.getSlug();
            Map<String, String> params = data.getParameters();
            // Navigate to appropriate screen
        });

        // Listen for unified links (external redirects)
        ulink.setOnUnifiedLinkListener(data -> {
            Log.d("ULink", "Unified link received: " + data.getSlug());
            // Handle unified link data
        });

        // Listen for reinstall detection
        ulink.setOnReinstallListener(info -> {
            Log.d("ULink", "Reinstall detected. Previous ID: " +
                info.getPreviousInstallationId());
            // Handle reinstall scenario
        });

        // Listen for SDK logs (debug mode only)
        ulink.setOnLogListener(entry -> {
            Log.d("ULink", entry.getMessage());
            // Forward to your logging system
        });
    }
}
```

## Creating Links

### Dynamic Links

Dynamic links are designed for in-app deep linking with parameters and smart app store redirects.

#### Using CompletableFuture

```java
import ly.ulink.sdk.models.ULinkParameters;
import ly.ulink.sdk.models.SocialMediaTags;
import java.util.HashMap;
import java.util.Map;

public void createDynamicLink() {
    // Create parameters
    Map<String, Object> params = new HashMap<>();
    params.put("userId", "12345");
    params.put("screen", "profile");

    // Create social media tags
    SocialMediaTags socialTags = new SocialMediaTags(
        "Check out this profile!",        // ogTitle
        "View user profile on our app",   // ogDescription
        "https://example.com/image.png"   // ogImage
    );

    // Create dynamic link parameters
    ULinkParameters linkParams = ULinkParameters.dynamic(
        "links.shared.ly",              // domain
        "user-profile-12345",           // slug (optional)
        "https://apps.apple.com/...",   // iOS fallback
        "https://play.google.com/...",  // Android fallback
        "https://example.com",          // fallback URL
        params,                         // parameters
        socialTags,                     // social media tags
        null                            // metadata
    );

    // Create link using CompletableFuture
    ulink.createLinkAsync(linkParams)
        .thenAccept(response -> {
            if (response.getSuccess()) {
                String url = response.getUrl();
                Log.d("ULink", "Link created: " + url);
                // Share the link
                shareLink(url);
            } else {
                Log.e("ULink", "Error: " + response.getError());
            }
        })
        .exceptionally(error -> {
            Log.e("ULink", "Failed to create link", error);
            return null;
        });
}
```

#### Using Callbacks

```java
public void createDynamicLinkWithCallback() {
    Map<String, Object> params = new HashMap<>();
    params.put("userId", "12345");
    params.put("screen", "profile");

    ULinkParameters linkParams = ULinkParameters.dynamic(
        "links.shared.ly",
        "user-profile-12345",
        null, null,
        "https://example.com",
        params,
        null, null
    );

    // Create link using callbacks
    ulink.createLink(linkParams,
        response -> {
            if (response.getSuccess()) {
                String url = response.getUrl();
                Log.d("ULink", "Link created: " + url);
                shareLink(url);
            } else {
                Log.e("ULink", "Error: " + response.getError());
            }
        },
        error -> {
            Log.e("ULink", "Failed to create link", error);
        }
    );
}
```

### Unified Links

Unified links are simple platform-based redirects intended for browser handling.

```java
public void createUnifiedLink() {
    ULinkParameters linkParams = ULinkParameters.unified(
        "links.shared.ly",                    // domain
        "download-app",                       // slug (optional)
        "https://apps.apple.com/your-app",    // iOS URL
        "https://play.google.com/your-app",   // Android URL
        "https://example.com",                // fallback URL
        null,                                 // parameters
        null,                                 // social media tags
        null                                  // metadata
    );

    // Using CompletableFuture
    ulink.createLinkAsync(linkParams)
        .thenAccept(response -> {
            if (response.getSuccess()) {
                Log.d("ULink", "Unified link: " + response.getUrl());
            }
        });

    // Or using callbacks
    ulink.createLink(linkParams,
        response -> {
            if (response.getSuccess()) {
                Log.d("ULink", "Unified link: " + response.getUrl());
            }
        },
        null  // Optional error callback
    );
}
```

## Resolving Links

Resolve ULink URLs to get their configuration and parameters:

```java
// Using CompletableFuture
public void resolveLink(String url) {
    ulink.resolveLinkAsync(url)
        .thenAccept(response -> {
            if (response.getSuccess()) {
                Log.d("ULink", "Link resolved successfully");
                // Access link data from response
            } else {
                Log.e("ULink", "Error: " + response.getError());
            }
        })
        .exceptionally(error -> {
            Log.e("ULink", "Failed to resolve link", error);
            return null;
        });
}

// Using callbacks
public void resolveLinkWithCallback(String url) {
    ulink.resolveLink(url,
        response -> {
            if (response.getSuccess()) {
                Log.d("ULink", "Link resolved successfully");
            }
        },
        error -> {
            Log.e("ULink", "Failed to resolve link", error);
        }
    );
}
```

## Session Management

End the current user session when the user logs out:

```java
// Using CompletableFuture
public void logout() {
    ulink.endSessionAsync()
        .thenAccept(success -> {
            if (success) {
                Log.d("ULink", "Session ended successfully");
                // Proceed with logout
            }
        })
        .exceptionally(error -> {
            Log.e("ULink", "Failed to end session", error);
            return null;
        });
}

// Using callbacks
public void logoutWithCallback() {
    ulink.endSession(
        success -> {
            if (success) {
                Log.d("ULink", "Session ended successfully");
                // Proceed with logout
            }
        },
        error -> {
            Log.e("ULink", "Failed to end session", error);
        }
    );
}
```

## Installation Tracking

The SDK automatically tracks installations and can detect reinstalls:

```java
// Get installation information
String installationId = ulink.getInstallationId();
ULinkInstallationInfo info = ulink.getInstallationInfo();
boolean isReinstall = ulink.isReinstall();
String persistentDeviceId = ulink.getPersistentDeviceId();

// Check installation info details
if (info != null) {
    Log.d("ULink", "Installation ID: " + info.getInstallationId());
    Log.d("ULink", "Is Reinstall: " + info.isReinstall());
    if (info.isReinstall()) {
        Log.d("ULink", "Previous ID: " + info.getPreviousInstallationId());
        Log.d("ULink", "Detected at: " + info.getReinstallDetectedAt());
    }
}

// Listen for reinstall detection
ulink.setOnReinstallListener(reinstallInfo -> {
    Log.d("ULink", "Reinstall detected!");
    Log.d("ULink", "Previous installation ID: " + reinstallInfo.getPreviousInstallationId());
    Log.d("ULink", "Persistent device ID: " + reinstallInfo.getPersistentDeviceId());
    // Handle reinstall scenario (e.g., restore user data)
});
```

## Debug Logging

Enable debug mode to receive SDK log events:

```java
// Enable debug mode in config
ULinkConfig config = new ULinkConfig(
    "your-api-key",
    "https://api.ulink.ly",
    true,  // debug = true
    true, true, 86400, false, false,
    Collections.emptyList(), true
);

// Listen for SDK logs
ulink.setOnLogListener(entry -> {
    String level = entry.getLevel();  // "debug", "info", "warning", "error"
    String tag = entry.getTag();
    String message = entry.getMessage();
    long timestamp = entry.getTimestamp();

    // Forward to your logging system
    switch (level) {
        case "error":
            Log.e(tag, message);
            break;
        case "warning":
            Log.w(tag, message);
            break;
        default:
            Log.d(tag, message);
    }
});
```

## Complete Example

Here's a complete example showing initialization, listener setup, and link creation:

```java
import android.app.Application;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import ly.ulink.sdk.ULink;
import ly.ulink.sdk.models.ULinkConfig;
import ly.ulink.sdk.models.ULinkParameters;
import java.util.HashMap;
import java.util.Map;

// Application class
public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        ULinkConfig config = new ULinkConfig(
            "your-api-key",
            "https://api.ulink.ly",
            true,                      // debug
            true,                      // enableDeepLinkIntegration
            true,                      // persistLastLinkData
            86400,                     // lastLinkTimeToLiveSeconds
            false,                     // clearLastLinkOnRead
            false,                     // redactAllParametersInLastLink
            Collections.emptyList(),   // redactedParameterKeysInLastLink
            true                       // autoCheckDeferredLink
        );

        // Initialize with CompletableFuture
        ULink.initializeAsync(this, config)
            .thenAccept(ulink -> {
                Log.d("ULink", "SDK initialized successfully");
            })
            .exceptionally(error -> {
                Log.e("ULink", "Initialization failed", error);
                return null;
            });
    }
}

// Main activity
public class MainActivity extends AppCompatActivity {
    private ULink ulink;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get SDK instance
        ulink = ULink.getInstance();

        // Set up listeners
        setupListeners();

        // Create a dynamic link
        createShareLink();
    }

    private void setupListeners() {
        // Listen for deep links
        ulink.setOnLinkListener(data -> {
            Log.d("ULink", "Deep link: " + data.getSlug());
            Map<String, String> params = data.getParameters();

            // Navigate based on parameters
            if (params != null) {
                String screen = params.get("screen");
                String userId = params.get("userId");
                // Navigate to appropriate screen
            }
        });

        // Listen for reinstalls
        ulink.setOnReinstallListener(info -> {
            Log.d("ULink", "Reinstall detected");
            // Track reinstall event
        });
    }

    private void createShareLink() {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", "12345");
        params.put("referralCode", "ABC123");

        ULinkParameters linkParams = ULinkParameters.dynamic(
            "links.shared.ly",
            null,  // Auto-generate slug
            null, null,
            "https://example.com",
            params,
            null, null
        );

        // Create link with CompletableFuture
        ulink.createLinkAsync(linkParams)
            .thenAccept(response -> {
                if (response.getSuccess()) {
                    String shareUrl = response.getUrl();
                    Log.d("ULink", "Share link: " + shareUrl);
                    // Show share dialog
                    shareLink(shareUrl);
                }
            })
            .exceptionally(error -> {
                Log.e("ULink", "Link creation failed", error);
                return null;
            });
    }

    private void shareLink(String url) {
        // Implement your share logic
    }
}
```

## Error Handling

The SDK provides detailed error information through exceptions:

```java
// With CompletableFuture
ulink.createLinkAsync(params)
    .thenAccept(response -> {
        if (response.getSuccess()) {
            // Success
            String url = response.getUrl();
        } else {
            // API returned error
            String errorMessage = response.getError();
            Log.e("ULink", "API error: " + errorMessage);
        }
    })
    .exceptionally(error -> {
        // Exception during operation
        if (error.getCause() instanceof ULinkInitializationError) {
            ULinkInitializationError initError =
                (ULinkInitializationError) error.getCause();
            Log.e("ULink", "Init error: " + initError.getStatusCode());
        } else {
            Log.e("ULink", "Unexpected error", error);
        }
        return null;
    });

// With callbacks
ulink.createLink(params,
    response -> {
        // Success callback
        if (response.getSuccess()) {
            Log.d("ULink", "Success: " + response.getUrl());
        } else {
            Log.e("ULink", "API error: " + response.getError());
        }
    },
    error -> {
        // Error callback (optional)
        Log.e("ULink", "Operation failed", error);
    }
);
```

## Additional Resources

- **Main README**: [README.md](README.md) - Kotlin examples and full API documentation
- **Test Examples**: [JavaIntegrationExample.java](app/src/test/java/ly/ulink/sdk/JavaIntegrationExample.java) - Comprehensive test suite showing all features
- **Getting Started**: Check the official documentation for Android setup guide

## Summary

**Two Async Patterns Available:**

1. **CompletableFuture** (Recommended)
   - Modern Java 8+ approach
   - Chainable operations
   - Better composition
   - Exception handling with `exceptionally()`

2. **Callbacks** (Traditional)
   - Familiar pattern
   - Optional error callbacks
   - Simpler for basic use cases
   - Compatible with older code styles

Both patterns provide identical functionality. Choose based on your project's style and requirements.
