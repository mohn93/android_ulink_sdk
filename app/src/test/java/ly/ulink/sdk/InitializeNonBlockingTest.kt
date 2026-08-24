package ly.ulink.sdk

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import androidx.lifecycle.LifecycleOwner
import io.mockk.*
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.Shadows.shadowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import ly.ulink.sdk.utils.DeviceInfoUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.time.Duration.Companion.seconds

/**
 * Regression tests for the issue where ULink.initialize() blocked the calling
 * (often main) thread because its body wrapped setup() in runBlocking { ... }.
 *
 * These tests verify that initialize() is fully suspendable: while it is awaiting
 * the bootstrap network call, other coroutines on the same dispatcher can still
 * run, and the parent scope can cancel the call. With the original implementation
 * (runBlocking on the calling thread), these scenarios would deadlock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class InitializeNonBlockingTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockHttpClient: HttpClient
    private lateinit var config: ULinkConfig
    private var storedInstallationId: String? = null

    @Before
    fun setUp() {
        mockContext = mockk(relaxed = true)
        mockSharedPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)

        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSharedPreferences(any(), any()) } returns mockSharedPreferences
        every { mockSharedPreferences.edit() } returns mockEditor
        every { mockEditor.putString("installation_id", any()) } answers {
            storedInstallationId = secondArg()
            mockEditor
        }
        every { mockEditor.apply() } just Runs
        every { mockSharedPreferences.getString("installation_id", null) } answers { storedInstallationId }

        // autoCheckDeferredLink = false avoids noise from a background coroutine
        // launched at the end of setup() that we don't care about here.
        config = ULinkConfig(
            apiKey = "test-api-key",
            baseUrl = "https://api.test.com",
            debug = false,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = false,
        )

        mockHttpClient = mockk(relaxed = true)

        mockkObject(DeviceInfoUtils)
        every { DeviceInfoUtils.getDeviceModel() } returns "Pixel"
        every { DeviceInfoUtils.getOsName() } returns "Android"
        every { DeviceInfoUtils.getOsVersion() } returns "14"
        every { DeviceInfoUtils.getLanguage() } returns "en"
        every { DeviceInfoUtils.getTimezone() } returns "UTC"
        every { DeviceInfoUtils.getAppVersion(any()) } returns "1.0.0"
        every { DeviceInfoUtils.getAppBuild(any()) } returns "1"
        every { DeviceInfoUtils.getDeviceId(any()) } returns "device-123"
        every { DeviceInfoUtils.getPersistentDeviceId(any()) } returns "persistent-123"
        every { DeviceInfoUtils.getNetworkType(any()) } returns "WiFi"
        every { DeviceInfoUtils.getDeviceOrientation(any()) } returns "Portrait"
        every { DeviceInfoUtils.getBatteryLevel(any()) } returns 90
        every { DeviceInfoUtils.isCharging(any()) } returns true
    }

    @After
    fun tearDown() {
        clearAllMocks()
        // Reset singleton state so each test starts clean.
        val ulinkClass = ULink::class.java
        ulinkClass.getDeclaredField("INSTANCE").apply {
            isAccessible = true
            set(null, null)
        }
        runCatching {
            ulinkClass.getDeclaredField("isInitializing").apply {
                isAccessible = true
                setBoolean(null, false)
            }
        }
    }

    /**
     * If initialize() uses runBlocking on the caller's thread, a single-threaded
     * test dispatcher will be parked while bootstrap awaits the network response,
     * so a sibling coroutine queued on the same dispatcher will never get to run
     * and runTest will hit its timeout.
     *
     * After the fix (initialize is fully suspend), the test dispatcher yields
     * when bootstrap hits withContext(Dispatchers.IO), the sibling coroutine
     * runs, and the test completes well under the timeout.
     */
    @Test(timeout = 10_000L)
    fun `initialize does not park the calling dispatcher during bootstrap`() = runTest(timeout = 5.seconds) {
        val responseGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers { responseGate.await() }

        var siblingRan = false

        val initJob = launch {
            ULink.initialize(mockContext, config, mockHttpClient)
        }
        launch { siblingRan = true }

        // Pump the test scheduler. With runBlocking in the call chain this never
        // returns (the only thread is parked); after the fix it returns promptly
        // because bootstrap suspends on responseGate via withContext(Dispatchers.IO).
        advanceUntilIdle()

        assertTrue(
            "Sibling coroutine must run while initialize awaits bootstrap — proves runBlocking is gone",
            siblingRan,
        )
        assertTrue(
            "initialize should still be suspended waiting for the network response",
            initJob.isActive,
        )

        // Release the gate so the test can complete cleanly.
        responseGate.complete(
            HttpResponse(
                statusCode = 200,
                body = """{"installationId":"test-123","sessionId":"s-1"}""",
                isSuccess = true,
                headers = mapOf("x-installation-token" to "tok"),
            ),
        )
        initJob.join()
        assertTrue(initJob.isCompleted)
    }

    /**
     * Two concurrent initialize() callers should see the same singleton without
     * either thread getting blocked. With synchronized + runBlocking, the second
     * caller would block on the JVM monitor until the first finished its network
     * round-trip; with Mutex.withLock + suspend, the second caller suspends
     * cooperatively and the dispatcher stays free.
     */
    @Test(timeout = 10_000L)
    fun `concurrent initialize calls serialize cooperatively, not by blocking`() = runTest(timeout = 5.seconds) {
        val responseGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers { responseGate.await() }

        val firstInit = async { ULink.initialize(mockContext, config, mockHttpClient) }
        val secondInit = async { ULink.initialize(mockContext, config, mockHttpClient) }

        var siblingRan = false
        launch { siblingRan = true }

        advanceUntilIdle()

        assertTrue("Sibling must run while both initialize calls are pending", siblingRan)
        assertTrue("First initialize should still be in flight", firstInit.isActive)
        assertTrue("Second initialize should also be in flight (waiting on mutex or singleton)", secondInit.isActive)

        responseGate.complete(
            HttpResponse(
                statusCode = 200,
                body = """{"installationId":"test-123","sessionId":"s-1"}""",
                isSuccess = true,
                headers = mapOf("x-installation-token" to "tok"),
            ),
        )

        val a = firstInit.await()
        val b = secondInit.await()
        assertSame("Both initialize calls must return the same singleton", a, b)
    }

    /**
     * If initialize() is fully suspend, cancelling the parent scope while
     * bootstrap is in flight must propagate cancellation. With runBlocking,
     * cancellation cannot reach the inner coroutine until runBlocking returns.
     */
    @Test(timeout = 10_000L)
    fun `initialize is cancellable while bootstrap is in flight`() = runTest(timeout = 5.seconds) {
        val responseGate = CompletableDeferred<HttpResponse>()
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers { responseGate.await() }

        coroutineScope {
            val initJob = launch { ULink.initialize(mockContext, config, mockHttpClient) }

            // Let initialize reach its bootstrap suspension point.
            advanceUntilIdle()
            assertTrue(initJob.isActive)

            // Cancel; with proper suspend this completes promptly.
            initJob.cancelAndJoin()
            assertTrue(initJob.isCancelled)
        }
    }

    /**
     * The cold-start bootstrap from setup() is a multi-second network call and is
     * usually still IN FLIGHT when the first activity foregrounds and onStart()
     * fires. onStart retries on `!bootstrapSucceeded`, which is true simply
     * because the initial attempt hasn't returned yet — so it used to fire a
     * DUPLICATE bootstrap, creating a second session on every cold start. onStart
     * must instead wait for the in-flight bootstrap and retry only if it failed.
     */
    // Plain Robolectric test (no runTest): the cold-start bootstrap must be held
    // IN FLIGHT on a real Dispatchers.IO thread while we foreground the app, which
    // the runTest virtual scheduler can't model. We drive the real main looper and
    // poll in real time, exactly like the app at runtime.
    @Test(timeout = 20_000L)
    fun `foregrounding while the cold-start bootstrap is in flight does not fire a duplicate`() {
        val responseGate = CompletableDeferred<HttpResponse>()
        val bootstrapCalls = AtomicInteger(0)
        coEvery { mockHttpClient.postJson(any(), any(), any()) } coAnswers {
            val url = firstArg<String>()
            if (url.endsWith("/sdk/bootstrap")) {
                bootstrapCalls.incrementAndGet()
                responseGate.await() // hold the cold-start bootstrap in flight
            } else {
                HttpResponse(statusCode = 200, body = "{}", isSuccess = true)
            }
        }

        // Launch init on the main dispatcher (ProcessLifecycleOwner.get() requires
        // the main thread); bootstrap then hops to Dispatchers.IO and blocks.
        val driverScope = CoroutineScope(Dispatchers.Main)
        driverScope.launch { ULink.initialize(mockContext, config, mockHttpClient) }

        assertTrue("cold-start bootstrap should be in flight", pumpUntil { bootstrapCalls.get() == 1 })

        // Foreground the app WHILE the cold-start bootstrap is still in flight.
        ULink.getInstance().onStart(mockk<LifecycleOwner>(relaxed = true))
        // Give a (wrongful) duplicate a chance to fire before asserting it did not.
        pumpUntil(timeoutMs = 1_500) { bootstrapCalls.get() > 1 }
        assertEquals(
            "onStart must not fire a second bootstrap while the first is in flight",
            1,
            bootstrapCalls.get(),
        )

        // Release the gate; the single bootstrap completes successfully and onStart
        // resumes — it must NOT retry, since bootstrap now succeeded.
        responseGate.complete(
            HttpResponse(
                statusCode = 200,
                body = """{"installationId":"i","sessionId":"s"}""",
                isSuccess = true,
                headers = mapOf("x-installation-token" to "t"),
            ),
        )
        pumpUntil(timeoutMs = 2_000) { bootstrapCalls.get() > 1 }
        assertEquals(
            "still exactly one bootstrap after a successful cold start",
            1,
            bootstrapCalls.get(),
        )

        driverScope.cancel()
    }

    private fun pumpUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(20)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return condition()
    }
}
