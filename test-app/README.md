# ULink SDK Test App

This is a test application for the ULink Android SDK that demonstrates all the key features and functionality.

## Features

The test app includes the following functionality:

### Link Creation
- **Create Dynamic Link**: Creates a dynamic link with social media tags and parameters
- **Create Unified Link**: Creates a unified link with platform-specific URLs

### Session Management
- **Start Session**: Starts a ULink session with metadata
- **End Session**: Ends the current ULink session

### Utility Functions
- **Get Installation ID**: Retrieves the unique installation identifier
- **Resolve Link**: Resolves a ULink URL to get its data and parameters

### Deep Link Handling
- Automatically handles incoming deep links
- Observes dynamic and unified link streams
- Displays resolved link data in the log output

## Setup

1. **Configure API Key**: Update the API key in `MainActivity.kt`:
   ```kotlin
   private fun initializeULink() {
       val config = ULinkConfig(
           apiKey = "your-api-key-here", // Replace with your actual API key
           baseUrl = "https://api.ulink.ly",
           debug = true
       )
       ulink = ULink(this, config)
   }
   ```

2. **Update Domain**: Update the deep link domain in `AndroidManifest.xml`:
   ```xml
   <data android:host="your-domain.com" android:scheme="https" />
   ```

## Building and Running

1. **Build the app**:
   ```bash
   ./gradlew :test-app:assembleDebug
   ```

2. **Install on device/emulator**:
   ```bash
   ./gradlew :test-app:installDebug
   ```

3. **Run the app**: Launch "ULink Test App" from your device/emulator

## Testing Deep Links

### Using ADB
You can test deep links using ADB commands:

```bash
# Test HTTPS deep link
adb shell am start \
  -W -a android.intent.action.VIEW \
  -d "https://your-domain.com/test-link" \
  ly.ulink.sdk.testapp

# Test custom scheme deep link
adb shell am start \
  -W -a android.intent.action.VIEW \
  -d "ulinktest://test-link" \
  ly.ulink.sdk.testapp
```

### Using Intent Filters
The app is configured to handle:
- HTTPS links: `https://your-domain.com/*`
- Custom scheme: `ulinktest://*`

## UI Components

The test app provides a simple interface with:
- Buttons for each SDK function
- A scrollable log view showing all operations and results
- Real-time display of deep link events

## Log Output

All SDK operations and their results are displayed in the log view, including:
- Link creation responses (URLs, errors)
- Session management results
- Deep link resolution data
- Installation ID
- Error messages and debugging information

## Troubleshooting

1. **Build Errors**: Ensure all dependencies are properly configured in `libs.versions.toml`
2. **Deep Link Issues**: Verify the domain configuration in `AndroidManifest.xml`
3. **API Errors**: Check that the API key is valid and the base URL is correct
4. **Network Issues**: Ensure the device has internet connectivity

## Dependencies

The test app depends on:
- ULink SDK (`:ulink-sdk` module)
- AndroidX libraries (Activity, ConstraintLayout, etc.)
- Kotlin Coroutines
- Kotlin Serialization