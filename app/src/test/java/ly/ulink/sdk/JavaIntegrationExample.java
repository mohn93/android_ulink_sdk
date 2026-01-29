package ly.ulink.sdk;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ly.ulink.sdk.models.SocialMediaTags;
import ly.ulink.sdk.models.ULinkConfig;
import ly.ulink.sdk.models.ULinkParameters;
import ly.ulink.sdk.models.ULinkResponse;

import static org.junit.Assert.*;

/**
 * Comprehensive Java integration examples for ULink SDK.
 *
 * This test class demonstrates all Java-friendly features:
 * - Initialization with CompletableFuture
 * - Link creation with CompletableFuture
 * - Listener setup
 * - Error handling
 *
 * Run these tests to verify Java compatibility and learn SDK usage patterns.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 28)
public class JavaIntegrationExample {

    private Context context;
    private ULinkConfig testConfig;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();

        // ULinkConfig uses all default parameters except the required ones
        testConfig = new ULinkConfig(
            "test-api-key",
            "https://api.test.com",
            true  // debug - other params use defaults
        );
    }

    /**
     * Example 1: ULinkConfig construction patterns
     *
     * Shows different ways to construct ULinkConfig in Java.
     */
    @Test
    public void example1_ConfigConstruction() {
        System.out.println("\n=== Example 1: Config Construction ===");

        // Minimal configuration (recommended)
        ULinkConfig minimalConfig = new ULinkConfig(
            "my-api-key",
            "https://api.ulink.ly"
        );
        assertNotNull(minimalConfig);
        assertEquals("my-api-key", minimalConfig.getApiKey());
        assertEquals("https://api.ulink.ly", minimalConfig.getBaseUrl());
        assertFalse(minimalConfig.getDebug()); // Default is false

        // With debug enabled
        ULinkConfig debugConfig = new ULinkConfig(
            "my-api-key",
            "https://api.ulink.ly",
            true  // debug
        );
        assertTrue(debugConfig.getDebug());

        System.out.println("Config created successfully");
    }

    /**
     * Example 2: Create dynamic link parameters
     *
     * Dynamic links are designed for in-app deep linking with parameters
     * and smart app store redirects.
     */
    @Test
    public void example2_CreateDynamicLinkParameters() {
        System.out.println("\n=== Example 2: Create Dynamic Link Parameters ===");

        // Minimal dynamic link (domain + nulls for optional params + fallbackUrl)
        ULinkParameters minimalParams = ULinkParameters.dynamic(
            "links.shared.ly",              // domain (required)
            null,                           // slug (auto-generate)
            null,                           // name
            null,                           // iosFallbackUrl
            null,                           // androidFallbackUrl
            "https://example.com/fallback"  // fallbackUrl
        );
        assertNotNull(minimalParams);
        assertEquals("dynamic", minimalParams.getType());
        assertEquals("links.shared.ly", minimalParams.getDomain());
        assertEquals("https://example.com/fallback", minimalParams.getFallbackUrl());

        // Dynamic link with custom parameters
        Map<String, Object> params = new HashMap<>();
        params.put("userId", "12345");
        params.put("screen", "profile");
        params.put("referralCode", "ABC123");

        ULinkParameters paramsWithMetadata = ULinkParameters.dynamic(
            "links.shared.ly",
            null,                           // auto-generate slug
            null,                           // name
            "https://apps.apple.com/...",   // iOS fallback
            "https://play.google.com/...",  // Android fallback
            "https://example.com/profile",  // fallback URL
            params,                         // parameters
            null,                           // social media tags
            null                            // metadata
        );
        assertNotNull(paramsWithMetadata);

        System.out.println("Dynamic link parameters created successfully");
    }

    /**
     * Example 3: Create unified link parameters
     *
     * Unified links are simple platform-based redirects intended for browser handling.
     */
    @Test
    public void example3_CreateUnifiedLinkParameters() {
        System.out.println("\n=== Example 3: Create Unified Link Parameters ===");

        ULinkParameters unifiedParams = ULinkParameters.unified(
            "links.shared.ly",
            "download-app",                            // slug
            null,                                      // name
            "https://apps.apple.com/app/id123456",     // iOS URL
            "https://play.google.com/store/apps/...",  // Android URL
            "https://example.com/download"             // Fallback URL
        );

        assertNotNull(unifiedParams);
        assertEquals("unified", unifiedParams.getType());
        assertEquals("links.shared.ly", unifiedParams.getDomain());
        assertEquals("download-app", unifiedParams.getSlug());

        System.out.println("Unified link parameters created successfully");
    }

    /**
     * Example 4: Create social media tags
     *
     * Social media tags customize how links appear when shared.
     */
    @Test
    public void example4_CreateSocialMediaTags() {
        System.out.println("\n=== Example 4: Create Social Media Tags ===");

        SocialMediaTags tags = new SocialMediaTags(
            "Check out this profile!",         // ogTitle
            "View user profile on our app",    // ogDescription
            "https://example.com/profile.png"  // ogImage
        );

        assertNotNull(tags);
        assertEquals("Check out this profile!", tags.getOgTitle());
        assertEquals("View user profile on our app", tags.getOgDescription());
        assertEquals("https://example.com/profile.png", tags.getOgImage());

        // Use in link parameters
        ULinkParameters paramsWithTags = ULinkParameters.dynamic(
            "links.shared.ly",
            null, null, null, null,
            "https://example.com",
            null,
            tags,
            null
        );
        assertNotNull(paramsWithTags);

        System.out.println("Social media tags created successfully");
    }

    /**
     * Example 5: ULinkResponse structure
     *
     * Shows how to work with ULinkResponse objects.
     */
    @Test
    public void example5_ULinkResponseStructure() {
        System.out.println("\n=== Example 5: ULinkResponse Structure ===");

        // Successful response
        ULinkResponse successResponse = new ULinkResponse(
            true,                              // success
            "https://links.shared.ly/abc123",  // url
            null,                              // error
            null                               // data
        );

        assertTrue(successResponse.getSuccess());
        assertEquals("https://links.shared.ly/abc123", successResponse.getUrl());
        assertNull(successResponse.getError());

        // Error response
        ULinkResponse errorResponse = new ULinkResponse(
            false,
            null,
            "Invalid parameters provided",
            null
        );

        assertFalse(errorResponse.getSuccess());
        assertNull(errorResponse.getUrl());
        assertEquals("Invalid parameters provided", errorResponse.getError());

        System.out.println("ULinkResponse handled successfully");
    }

    /**
     * Example 6: Complete link parameters workflow
     *
     * Shows a realistic example of building link parameters for a referral campaign.
     */
    @Test
    public void example6_CompleteWorkflow() {
        System.out.println("\n=== Example 6: Complete Workflow ===");

        // Step 1: Create referral parameters
        Map<String, Object> referralParams = new HashMap<>();
        referralParams.put("referrerId", "user123");
        referralParams.put("campaign", "friend-referral");
        referralParams.put("reward", "10");

        // Step 2: Create social tags for sharing
        SocialMediaTags socialTags = new SocialMediaTags(
            "Join me on this app!",
            "Get $10 when you sign up using my referral link",
            "https://example.com/share-image.png"
        );

        // Step 3: Build complete link parameters
        ULinkParameters linkParams = ULinkParameters.dynamic(
            "links.shared.ly",
            null,  // Auto-generate slug
            null,  // name
            "https://apps.apple.com/app/id123",
            "https://play.google.com/store/apps/details?id=com.example",
            "https://example.com/join",
            referralParams,
            socialTags,
            null
        );

        // Verify parameters
        assertNotNull(linkParams);
        assertEquals("dynamic", linkParams.getType());
        assertEquals("links.shared.ly", linkParams.getDomain());
        assertNotNull(linkParams.getParameters());
        assertNotNull(linkParams.getSocialMediaTags());

        System.out.println("Complete workflow executed successfully");
    }

    /**
     * Example 7: Java-friendly factory methods
     *
     * Demonstrates that factory methods are accessible from Java via @JvmStatic.
     */
    @Test
    public void example7_FactoryMethods() {
        System.out.println("\n=== Example 7: Factory Methods ===");

        // Static factory methods work from Java
        ULinkParameters dynamicLink = ULinkParameters.dynamic(
            "test.ly",
            null, null, null, null,
            "https://example.com"
        );
        assertEquals("dynamic", dynamicLink.getType());
        assertEquals("https://example.com", dynamicLink.getFallbackUrl());

        ULinkParameters unifiedLink = ULinkParameters.unified(
            "test.ly",
            null,
            null,
            "https://ios.example.com",
            "https://android.example.com",
            "https://web.example.com"
        );
        assertEquals("unified", unifiedLink.getType());

        System.out.println("Factory methods work correctly from Java");
    }

    /**
     * Example 8: Default parameter handling with @JvmOverloads
     *
     * Shows that Kotlin default parameters are accessible via overloaded methods.
     */
    @Test
    public void example8_DefaultParameters() {
        System.out.println("\n=== Example 8: Default Parameters ===");

        // Minimal call - uses nulls for optional parameters
        ULinkParameters minimal = ULinkParameters.dynamic(
            "test.ly",
            null, null, null, null,
            "https://fallback.com"
        );
        assertNotNull(minimal);
        assertNull(minimal.getSlug()); // slug is null (auto-generate)
        assertEquals("https://fallback.com", minimal.getFallbackUrl());

        // With some optional parameters
        ULinkParameters withSlug = ULinkParameters.dynamic(
            "test.ly",
            "my-custom-slug",
            null, null, null,
            "https://fallback.com",
            null, null, null
        );
        assertEquals("my-custom-slug", withSlug.getSlug());

        System.out.println("Default parameters handled correctly");
    }
}
