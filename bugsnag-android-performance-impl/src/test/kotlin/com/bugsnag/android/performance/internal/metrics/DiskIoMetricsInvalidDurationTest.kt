package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 3:
 * Disk metrics are NOT emitted when span duration is invalid (zero or negative).
 *
 * Exact start/end nanosecond injection is not possible in Maze Runner, so this is
 * covered as a unit test. The span itself remains usable (no crash / metrics skipped).
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsInvalidDurationTest {
    companion object {
        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                // ROAD 2233 Scenario 3 – zero duration
                InvalidDurationCase(
                    name = "zero_duration",
                    startNs = 1_681_383_000_000_000_000L,
                    endNs = 1_681_383_000_000_000_000L,
                ),
                // ROAD 2233 Scenario 3 – negative duration
                InvalidDurationCase(
                    name = "negative_duration",
                    startNs = 1_681_383_000_150_000_000L,
                    endNs = 1_681_383_000_000_000_000L,
                ),
            )
    }

    @Parameter
    lateinit var testCase: InvalidDurationCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-invalid-duration", null)
    }

    @Test
    fun doesNotEmitDiskMetricsWhenDurationIsInvalid() {
        writeIoFile(syscr = 100L, syscw = 50L)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(testCase.startNs, testCase.startNs, testCase.endNs)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 200L, syscw = 100L)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            // Span is still usable / "delivered" from metrics' perspective (no crash).
            span.end()

            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
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

    internal data class InvalidDurationCase(
        val name: String,
        val startNs: Long,
        val endNs: Long,
    ) {
        override fun toString(): String = name
    }
}
