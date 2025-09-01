package ly.ulink.sdk.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.TelephonyManager
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P], manifest = Config.NONE)
class DeviceInfoUtilsTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var mockConnectivityManager: ConnectivityManager
    private lateinit var mockTelephonyManager: TelephonyManager
    private lateinit var mockNetwork: Network
    private lateinit var mockNetworkCapabilities: NetworkCapabilities

    @Before
    fun setup() {
        mockContext = mockk(relaxed = true)
        mockPackageManager = mockk(relaxed = true)
        mockConnectivityManager = mockk(relaxed = true)
        mockTelephonyManager = mockk(relaxed = true)
        mockNetwork = mockk(relaxed = true)
        mockNetworkCapabilities = mockk(relaxed = true)

        every { mockContext.packageManager } returns mockPackageManager
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns mockConnectivityManager
        every { mockContext.getSystemService(Context.TELEPHONY_SERVICE) } returns mockTelephonyManager
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test getDeviceInfo returns complete device information`() {
        // Mock package info
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            versionCode = 1
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"

        // Mock network info
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        // Mock telephony info
        every { mockTelephonyManager.networkOperatorName } returns "Test Carrier"
        every { mockTelephonyManager.networkCountryIso } returns "us"

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        assertNotNull(deviceInfo)
        assertEquals("Android", deviceInfo["platform"])
        assertEquals(Build.VERSION.RELEASE, deviceInfo["osVersion"])
        assertEquals(Build.MODEL, deviceInfo["deviceModel"])
        assertEquals(Build.MANUFACTURER, deviceInfo["deviceManufacturer"])
        assertEquals("com.test.app", deviceInfo["packageName"])
        assertEquals("1.0.0", deviceInfo["appVersion"])
        assertEquals("WiFi", deviceInfo["connectionType"])
        assertEquals("Test Carrier", deviceInfo["carrier"])
        assertEquals("us", deviceInfo["country"])
    }

    @Test
    fun `test getDeviceInfo with cellular connection`() {
        // Mock package info
        val packageInfo = PackageInfo().apply {
            versionName = "2.0.0"
            versionCode = 2
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"

        // Mock cellular network
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns true

        every { mockTelephonyManager.networkOperatorName } returns "Cellular Provider"
        every { mockTelephonyManager.networkCountryIso } returns "ca"

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        assertEquals("Cellular", deviceInfo["connectionType"])
        assertEquals("Cellular Provider", deviceInfo["carrier"])
        assertEquals("ca", deviceInfo["country"])
    }

    @Test
    fun `test getDeviceInfo with no network connection`() {
        // Mock package info
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            versionCode = 1
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"

        // Mock no network
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns null
        every { mockTelephonyManager.networkOperatorName } returns ""
        every { mockTelephonyManager.networkCountryIso } returns ""

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        assertEquals("Unknown", deviceInfo["connectionType"])
        assertEquals("Unknown", deviceInfo["carrier"])
        assertEquals("Unknown", deviceInfo["country"])
    }

    @Test
    fun `test getDeviceInfo handles PackageManager exception`() {
        // Mock PackageManager to throw exception
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        every { mockContext.packageName } returns "com.test.app"

        // Mock network info
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns true
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false

        every { mockTelephonyManager.networkOperatorName } returns "Test Carrier"
        every { mockTelephonyManager.networkCountryIso } returns "us"

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        // Should still return device info with unknown app version
        assertNotNull(deviceInfo)
        assertEquals("Unknown", deviceInfo["appVersion"])
        assertEquals("com.test.app", deviceInfo["packageName"])
    }

    @Test
    fun `test getDeviceInfo handles SecurityException for network access`() {
        // Mock package info
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            versionCode = 1
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"

        // Mock SecurityException for network access
        every { mockConnectivityManager.activeNetwork } throws SecurityException("Permission denied")
        every { mockTelephonyManager.networkOperatorName } returns "Test Carrier"
        every { mockTelephonyManager.networkCountryIso } returns "us"

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        // Should handle exception gracefully
        assertEquals("Unknown", deviceInfo["connectionType"])
        assertEquals("Unknown", deviceInfo["carrier"])
        assertEquals("Unknown", deviceInfo["country"])
    }

    @Test
    fun `test getDeviceInfo with ethernet connection`() {
        // Mock package info
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            versionCode = 1
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"

        // Mock ethernet network
        every { mockConnectivityManager.activeNetwork } returns mockNetwork
        every { mockConnectivityManager.getNetworkCapabilities(mockNetwork) } returns mockNetworkCapabilities
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns false
        every { mockNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns true

        every { mockTelephonyManager.networkOperatorName } returns ""
        every { mockTelephonyManager.networkCountryIso } returns "us"

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        assertEquals("Ethernet", deviceInfo["connectionType"])
        assertEquals("Unknown", deviceInfo["carrier"])
        assertEquals("us", deviceInfo["country"])
    }

    @Test
    fun `test getDeviceInfo includes all required fields`() {
        // Mock minimal setup
        val packageInfo = PackageInfo().apply {
            versionName = "1.0.0"
            versionCode = 1
        }
        every { mockPackageManager.getPackageInfo(any<String>(), any<Int>()) } returns packageInfo
        every { mockContext.packageName } returns "com.test.app"
        every { mockConnectivityManager.activeNetwork } returns null
        every { mockTelephonyManager.networkOperatorName } returns ""
        every { mockTelephonyManager.networkCountryIso } returns ""

        val deviceInfo = DeviceInfoUtils.getDeviceInfo(mockContext)

        // Verify all required fields are present
        val requiredFields = listOf(
            "platform", "osVersion", "deviceModel", "deviceManufacturer",
            "packageName", "appVersion", "connectionType", "carrier", "country"
        )

        requiredFields.forEach { field ->
            assertTrue("Missing field: $field", deviceInfo.containsKey(field))
            assertNotNull("Null value for field: $field", deviceInfo[field])
        }
    }
}