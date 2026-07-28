package ly.ulink.sdk

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ly.ulink.sdk.models.ULinkConfig
import ly.ulink.sdk.models.ULinkLogEntry
import ly.ulink.sdk.network.HttpClient
import ly.ulink.sdk.network.HttpResponse
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Regression tests for log entries that never reach the public logStream.
 *
 * Every log helper emitted via `scope.launch { _logStream.emit(...) }`, so
 * delivery depended on the SDK's own coroutine scope being alive. Two
 * consequences: anything logged after dispose() cancels that scope was written
 * to Log.e/Log.i but silently never reached a host collecting logStream (the
 * SDK's own "ULink SDK disposed" line is logged after the cancel, so it could
 * never be observed), and every single log line cost a coroutine dispatch.
 *
 * logStream is public API — a host can collect it from its own scope, which
 * outlives the SDK's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class LogStreamDeliveryTest {

    private lateinit var context: Context
    private lateinit var config: ULinkConfig
    private lateinit var mockHttpClient: HttpClient
    private val startedJobs = mutableListOf<Job>()

    private val bootstrapResponse = HttpResponse(
        statusCode = 200,
        body = """{"installationId":"test-123","sessionId":"s-1"}""",
        isSuccess = true,
        headers = mapOf("x-installation-token" to "tok"),
    )

    private fun instanceOrNull(): ULink? = runCatching { ULink.getInstance() }.getOrNull()

    private fun idleUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return true
            Thread.sleep(10)
        }
        return condition()
    }

    private fun launchTracked(block: suspend () -> Unit): Job =
        CoroutineScope(Dispatchers.Main).launch { block() }.also { startedJobs.add(it) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        config = ULinkConfig(
            apiKey = "test-key",
            baseUrl = "https://api.test.com",
            debug = true,
            enableDeepLinkIntegration = false,
            autoCheckDeferredLink = false,
            persistLastLinkData = false,
        )
        mockHttpClient = mockk(relaxed = true)
        coEvery { mockHttpClient.postJson(any(), any(), any()) } returns bootstrapResponse
    }

    @After
    fun tearDown() {
        startedJobs.forEach { it.cancel() }
        idleUntil(timeoutMs = 2_000) { startedJobs.all { it.isCompleted } }
        startedJobs.clear()
        clearAllMocks()
        runCatching {
            ULink::class.java.getDeclaredField("INSTANCE").apply {
                isAccessible = true
                set(null, null)
            }
        }
    }

    /**
     * A host collecting logStream from its own scope must keep receiving entries
     * the SDK writes, including the ones dispose() itself emits after cancelling
     * the SDK scope. Log delivery must not depend on the SDK's scope being alive.
     */
    @Test(timeout = 30_000L)
    fun `entries logged after the SDK scope is cancelled still reach logStream`() {
        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue("bootstrap should complete", idleUntil { !initJob.isActive })
        val ulink = requireNotNull(instanceOrNull())

        val seen = mutableListOf<ULinkLogEntry>()
        launchTracked { ulink.logStream.collect { seen.add(it) } }
        idleUntil { seen.isNotEmpty() }

        ulink.dispose()

        assertTrue(
            "dispose() logs 'ULink SDK disposed' after cancelling its scope; that entry must still arrive",
            idleUntil { seen.any { it.message.contains("ULink SDK disposed") } },
        )
    }

    /**
     * Logging must be synchronous with the call that wrote it. Routing every
     * line through scope.launch deferred it to a later main-loop turn, so an
     * entry was absent from the stream at the moment the SDK claimed to log it
     * — which is also why ordering depended on dispatch rather than call order.
     */
    @Test(timeout = 30_000L)
    fun `an entry is on the stream as soon as it is logged`() {
        val initJob = launchTracked { ULink.initialize(context, config, mockHttpClient) }
        assertTrue(idleUntil { !initJob.isActive })
        val ulink = requireNotNull(instanceOrNull())

        // handleDeepLink logs "Handling deep link" synchronously before it
        // dispatches any coroutine work.
        ulink.handleDeepLink(android.net.Uri.parse("https://links.shared.ly/sync-check"))

        // Deliberately no looper pumping here: the entry must already be there.
        assertTrue(
            "the entry must be on the stream without waiting for a dispatch",
            ulink.logStream.replayCache.any { it.message.contains("sync-check") },
        )
    }
}
