package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.internal.processing.JsonTraceWriter
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.StringWriter

/** ROAD 2233 – Scenario 11: high IOPS serializes as OTLP doubleValue in JSON payload. */
@RunWith(RobolectricTestRunner::class)
internal class DiskIoMetricsHighIopsJsonTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-high-iops-json", null)
    }

    @Test
    fun serializesHighIopsAsDoubleValueInJsonPayload() {
        writeIoFile(syscr = 0L, syscw = 0L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(
                    NANOS_PER_SECOND,
                    NANOS_PER_SECOND,
                    3L * NANOS_PER_SECOND,
                )

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 200_000L, syscw = 0L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)
            span.end(3L * NANOS_PER_SECOND)

            val json =
                StringWriter().apply {
                    JsonTraceWriter(this).use { writer -> span.toJson(writer) }
                }.toString()

            assertTrue(json.contains("\"key\":\"${DiskIoMetricsSource.ATTR_IOPS_READ}\""))
            assertTrue(json.contains("\"doubleValue\":100000.0"))
            assertFalse(json.contains("\"doubleValue\":null"))
        }
    }

    private fun writeIoFile(
        syscr: Long,
        syscw: Long,
    ) {
        ioFile.writeText(
            """
            rchar: 0
            wchar: 0
            syscr: $syscr
            syscw: $syscw
            read_bytes: 0
            write_bytes: 0
            cancelled_write_bytes: 0
            """.trimIndent(),
        )
    }

    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
    }
}
