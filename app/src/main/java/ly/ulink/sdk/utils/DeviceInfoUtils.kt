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
     * Gets the device ID (Android ID)
     */
    fun getDeviceId(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the network type
     */
    fun getNetworkType(context: Context): String {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return "Unknown"
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"
            
            return when {
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "None"
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return "Unknown"
            
            return when (networkInfo.type) {
                ConnectivityManager.TYPE_WIFI -> "WiFi"
                ConnectivityManager.TYPE_MOBILE -> "Cellular"
                ConnectivityManager.TYPE_ETHERNET -> "Ethernet"
                else -> "Unknown"
            }
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
            val countryCode = telephonyManager.networkCountryIso
            if (countryCode.isNullOrEmpty()) "Unknown" else countryCode
        } catch (e: Exception) {
            "Unknown"
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
}