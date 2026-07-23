package ly.ulink.sdk.testapp

import android.app.Application
import android.util.Log
import ly.ulink.sdk.ULink
import ly.ulink.sdk.models.ULinkConfig

/**
 * Initializes ULink in Application.onCreate so the SDK's ActivityLifecycleCallbacks
 * are registered before the launch activity is created/resumed. This mirrors a real
 * host app and makes cold-start deep-link handling deterministic (the E2E dedup test
 * relies on create+resume both firing while callbacks are already registered).
 */
class TestApp : Application() {
    companion object { private const val TAG = "ULinkTestApp" }

    override fun onCreate() {
        super.onCreate()
        val config = ULinkConfig(
            apiKey = "ulk_f666ab8b0113e922e014be89c47d04cacce70114a5b7f702",
            baseUrl = "https://api.ulink.ly",
            debug = true,
            enableDeepLinkIntegration = true,
        )
        ULink.initialize(this, config, { _ ->
            Log.d(TAG, "ULink initialized from Application")
        }, { e ->
            Log.e(TAG, "ULink init failed", e)
        })
    }
}
