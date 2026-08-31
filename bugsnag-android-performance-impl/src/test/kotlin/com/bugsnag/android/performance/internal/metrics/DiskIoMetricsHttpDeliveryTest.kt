package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.internal.Attributes
import com.bugsnag.android.performance.internal.DeliveryResult
import com.bugsnag.android.performance.internal.HttpDelivery
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.connectivity.ConnectionMetering
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.connectivity.ConnectivityStatus
import com.bugsnag.android.performance.internal.connectivity.NetworkType
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.TestSpanFactory
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowSystemClock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * ROAD 2233 – Scenario 15 (Android):
 * SDK delivers span payload to trace API successfully with disk IOPS attributes.
 *
 * Validates that HttpDelivery POSTs an OTLP payload containing disk IOPS attributes
 * and treats HTTP 200 as a successful delivery.
 */
@RunWith(RobolectricTestRunner::class)
internal class DiskIoMetricsHttpDeliveryTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-http-delivery", null)
    }

    @Test
    fun deliversSpanPayloadWithDiskIopsAttributesToTraceApi() {
        val span = spanWithDiskIops(read = 18.0, write = 16.4, total = 34.4)

        val connectivity =
            mock<Connectivity> {
                on { connectivityStatus } doReturn
                        ConnectivityStatus(
                            true,
                            ConnectionMetering.POTENTIALLY_METERED,
                            NetworkType.CELL,
                            null,
                        )
            }

        val recordingConnection = RecordingHttpConnection()
        val delivery =
            object : HttpDelivery(
                "http://localhost/traces",
                "0123456789abcdef0123456789abcdef",
                connectivity,
                false,
                null,
            ) {
                override fun openConnection(): HttpURLConnection = recordingConnection
            }

        val result = delivery.deliver(listOf(span), Attributes())

        assertTrue(result is DeliveryResult.Success)
        assertTrue(recordingConnection.bodyStream.size() > 0)

        val payloadJson = decompressGzipPayload(recordingConnection.bodyStream.toByteArray())
        assertTraceDataValid(payloadJson.toByteArray(Charsets.UTF_8))

        val diskAttributes = diskAttributesFromPayload(JSONObject(payloadJson))
        assertEquals(3, diskAttributes.size)
        assertEquals(
            18.0,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_READ),
            0.001,
        )
        assertEquals(
            16.4,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_WRITE),
            0.001,
        )
        assertEquals(
            34.4,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_TOTAL),
            0.001,
        )
    }

    private fun spanWithDiskIops(
        read: Double,
        write: Double,
        total: Double,
    ): SpanImpl {
        // 5-second span: deltas chosen so (delta / 5) matches ROAD Scenario 15 example values.
        val readStart = 1000L
        val writeStart = 500L
        val readEnd = readStart + (read * 5).toLong()
        val writeEnd = writeStart + (write * 5).toLong()

        DiskIoMetricsTestSupport.writeIoFile(ioFile, readStart, writeStart)
        ShadowSystemClock.advanceBy(1, TimeUnit.SECONDS)
        val startTimestampNanos = SystemClock.elapsedRealtimeNanos()

        val reader = ProcIoReader(ioFile.absolutePath)
        val source = DiskIoMetricsSource(reader)
        val startSnapshot = source.createStartMetrics()
        DiskIoMetricsTestSupport.assertStartSnapshotValid(
            ioFile = ioFile,
            startSnapshot = startSnapshot,
            expectedReadSyscalls = readStart,
            expectedWriteSyscalls = writeStart,
            expectedTimestampNanos = startTimestampNanos,
        )

        DiskIoMetricsTestSupport.writeIoFile(ioFile, readEnd, writeEnd)
        DiskIoMetricsTestSupport.assertEndIoFileReadable(
            reader = reader,
            ioFile = ioFile,
            expectedReadSyscalls = readEnd,
            expectedWriteSyscalls = writeEnd,
        )

        ShadowSystemClock.advanceBy(5, TimeUnit.SECONDS)
        val endTimestampNanos = SystemClock.elapsedRealtimeNanos()
        DiskIoMetricsTestSupport.assertPositiveDuration(endTimestampNanos, startSnapshot)

        val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
        source.endMetrics(startSnapshot, span)
        span.end(endTimestampNanos)

        DiskIoMetricsTestSupport.assertDiskIopsOnSpan(
            span = span,
            expectedRead = read,
            expectedWrite = write,
            expectedTotal = total,
        )

        return span
    }

    private fun decompressGzipPayload(gzippedBody: ByteArray): String =
        ByteArrayInputStream(gzippedBody).use { input ->
            GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

    private fun diskAttributesFromPayload(payload: JSONObject): List<JSONObject> {
        val attributes =
            payload
                .getJSONArray("resourceSpans")
                .getJSONObject(0)
                .getJSONArray("scopeSpans")
                .getJSONObject(0)
                .getJSONArray("spans")
                .getJSONObject(0)
                .getJSONArray("attributes")

        return List(attributes.length()) { index -> attributes.getJSONObject(index) }
            .filter { it.getString("key").startsWith(DISK_KEY_PREFIX) }
    }

    private fun List<JSONObject>.doubleValueFor(key: String): Double {
        val attribute = first { it.getString("key") == key }
        return attribute.getJSONObject("value").getDouble("doubleValue")
    }

    private class RecordingHttpConnection(
        url: URL = URL("http://localhost/traces"),
        private val statusCode: Int = 200,
        val bodyStream: ByteArrayOutputStream = ByteArrayOutputStream(),
    ) : HttpURLConnection(url) {
        override fun connect() = Unit

        override fun disconnect() = Unit

        override fun usingProxy(): Boolean = false

        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

        override fun getOutputStream(): OutputStream = bodyStream

        override fun getResponseCode(): Int = statusCode
    }

    private companion object {
        private const val DISK_KEY_PREFIX = "bugsnag.device.disk."
    }
}
