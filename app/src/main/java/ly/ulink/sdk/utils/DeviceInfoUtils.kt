package ly.ulink.sdk.utils

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import java.util.*
import java.security.MessageDigest
import android.webkit.WebSettings

/**
 * Utility class for collecting device information
 */
object DeviceInfoUtils {
    
    /**
     * Gets comprehensive device information
     */
    fun getDeviceInfo(context: Context): Map<String, String> {
        return try {
            mapOf(
                "platform" to getOsName(),
                "osVersion" to getOsVersion(),
                "deviceModel" to Build.MODEL,
                "deviceManufacturer" to Build.MANUFACTURER,
                "packageName" to context.packageName,
                "appVersion" to (getAppVersion(context) ?: "Unknown"),
                "connectionType" to getNetworkType(context),
                "carrier" to getCarrierName(context),
                "country" to getCountryCode(context)
            )
        } catch (e: Exception) {
            // Return basic info if detailed collection fails
            mapOf(
                "platform" to "Android",
                "osVersion" to Build.VERSION.RELEASE,
                "deviceModel" to Build.MODEL,
                "deviceManufacturer" to Build.MANUFACTURER,
                "packageName" to context.packageName,
                "appVersion" to "Unknown",
                "connectionType" to "Unknown",
                "carrier" to "Unknown",
                "country" to "Unknown"
            )
        }
    }
    
    /**
     * Gets the device model
     */
    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }
    
    /**
     * Gets the operating system name
     */
    fun getOsName(): String {
        return "Android"
    }
    
    /**
     * Gets the operating system version
     */
    fun getOsVersion(): String {
        return Build.VERSION.RELEASE
    }
    
    /**
     * Gets the app version
     */
    fun getAppVersion(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Gets the app build number (version code)
     */
    fun getAppBuild(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
    
    /**
     * Gets the device language
     */
    fun getLanguage(): String {
        return Locale.getDefault().language
    }
    
    /**
     * Gets the device timezone
     */
    fun getTimezone(): String {
        return TimeZone.getDefault().id
    }
    
    /**
     * Gets the device ID using multiple fallback methods
     * Similar to Flutter's flutter_udid package approach
     */
    fun getDeviceId(context: Context): String? {
        return try {
            // Primary method: Android ID
            val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            if (!androidId.isNullOrEmpty() && androidId != "9774d56d682e549c") {
                return androidId
            }
            
            // Fallback: Generate a unique ID based on device characteristics
            val deviceInfo = "${Build.MANUFACTURER}-${Build.MODEL}-${Build.DEVICE}-${Build.BRAND}"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(deviceInfo.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the network type
     */
    fun getNetworkType(context: Context): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return "Unknown"
                val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
                
                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "None"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo ?: return "Unknown"
                
                when (networkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> "WiFi"
                    ConnectivityManager.TYPE_MOBILE -> "Cellular"
                    ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                    else -> "Unknown"
                }
            }
        } catch (e: SecurityException) {
            "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Gets the carrier name
     */
    fun getCarrierName(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val carrierName = telephonyManager.networkOperatorName
            if (carrierName.isNullOrEmpty()) "Unknown" else carrierName
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Gets the country code
     */
    fun getCountryCode(context: Context): String {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val networkCountry = telephonyManager.networkCountryIso
            if (!networkCountry.isNullOrEmpty()) {
                return networkCountry.uppercase(Locale.ROOT)
            }

            val simCountry = telephonyManager.simCountryIso
            if (!simCountry.isNullOrEmpty()) {
                return simCountry.uppercase(Locale.ROOT)
            }

            val localeCountry = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.resources.configuration.locales.get(0).country
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale.country
            }
            if (!localeCountry.isNullOrEmpty()) {
                return localeCountry.uppercase(Locale.ROOT)
            }

            val defaultCountry = Locale.getDefault().country
            if (!defaultCountry.isNullOrEmpty()) {
                return defaultCountry.uppercase(Locale.ROOT)
            }

            "Unknown"
        } catch (e: Exception) {
            val fallback = Locale.getDefault().country
            if (!fallback.isNullOrEmpty()) fallback.uppercase(Locale.ROOT) else "Unknown"
        }
    }
    
    /**
     * Gets the device orientation
     */
    fun getDeviceOrientation(context: Context): String {
        return when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> "Portrait"
            Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
            else -> "Unknown"
        }
    }
    
    /**
     * Gets the battery level (0-100)
     */
    fun getBatteryLevel(context: Context): Int? {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Checks if the device is charging
     */
    fun isCharging(context: Context): Boolean? {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                batteryManager.isCharging
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the device brand
     */
    fun getBrand(): String {
        return Build.BRAND
    }
    
    /**
     * Gets the device name/codename
     */
    fun getDevice(): String {
        return Build.DEVICE
    }
    
    /**
     * Gets the Android SDK version
     */
    fun getSdkVersion(): String {
        return Build.VERSION.SDK_INT.toString()
    }
    
    /**
     * Checks if this is a physical device (not an emulator)
     */
    fun isPhysicalDevice(): Boolean {
        return !(Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK built for x86") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
                "google_sdk" == Build.PRODUCT)
    }
    
    /**
     * Gets user agent string (for web compatibility)
     */
    fun getUserAgent(context: Context): String? {
        return try {
            WebSettings.getDefaultUserAgent(context)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets platform information
     */
    fun getPlatform(): String {
        return "android"
    }
    
    /**
     * Gets comprehensive device information similar to Flutter SDK
     * Combines all available device information methods
     */
    fun getCompleteDeviceInfo(context: Context): Map<String, Any?> {
        val deviceInfo = mutableMapOf<String, Any?>()
        
        try {
            // Basic device information
            deviceInfo["osName"] = getOsName()
            deviceInfo["osVersion"] = getOsVersion()
            deviceInfo["deviceModel"] = Build.MODEL
            deviceInfo["deviceManufacturer"] = Build.MANUFACTURER
            deviceInfo["brand"] = getBrand()
            deviceInfo["device"] = getDevice()
            deviceInfo["isPhysicalDevice"] = isPhysicalDevice()
            deviceInfo["sdkVersion"] = getSdkVersion()
            
            // App information
            deviceInfo["appVersion"] = getAppVersion(context)
            deviceInfo["appBuild"] = getAppBuild(context)
            
            // Device identifiers
            deviceInfo["deviceId"] = getDeviceId(context)
            
            // Locale and timezone
            deviceInfo["language"] = getLanguage()
            deviceInfo["timezone"] = getTimezone()
            
            // Network and connectivity
            deviceInfo["networkType"] = getNetworkType(context)
            deviceInfo["carrierName"] = getCarrierName(context)
            deviceInfo["countryCode"] = getCountryCode(context)
            
            // Device state
            deviceInfo["deviceOrientation"] = getDeviceOrientation(context)
            deviceInfo["batteryLevel"] = getBatteryLevel(context)
            deviceInfo["isCharging"] = isCharging(context)
            
            // Platform specific
            deviceInfo["platform"] = getPlatform()
            deviceInfo["userAgent"] = getUserAgent(context)
            
        } catch (e: Exception) {
            // Add error information if collection fails
            deviceInfo["error"] = "Failed to collect complete device info: ${e.message}"
        }
        
        return deviceInfo
    }
}