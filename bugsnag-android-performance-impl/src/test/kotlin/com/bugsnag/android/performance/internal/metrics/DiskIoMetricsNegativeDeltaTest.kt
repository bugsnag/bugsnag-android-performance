package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/**
 * ROAD 2233 – Scenario 4 (Android platform behavior):
 * Negative counter deltas must not produce negative IOPS values.
 *
 * ED §3.1.4 allows platforms to either clamp negative deltas or treat them as invalid.
 * Android treats any negative delta as invalid and omits all disk IOPS attributes
 * (it does NOT clamp one dimension to 0 while still emitting the other).
 *
 * Therefore the ROAD example Expected Read/Write/Total columns that assume per-dimension
 * clamping (e.g. read=0.0, write=15.0) do not apply to Android. These tests assert omit-all.
 *
 * Counter pairs match the ROAD Scenario 4 examples table; duration is positive (2s).
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsNegativeDeltaTest {
    companion object {
        private const val START_NS = 1_000_000_000L
        private const val END_NS = 3_000_000_000L // 2.0 seconds

        @get:JvmStatic
        @get:Parameterized.Parameters(name = "{0}")
        val parameters =
            listOf(
                // ROAD 2233 Scenario 4 – both counters regress
                NegativeDeltaCase(
                    name = "both_counters_regress",
                    readStart = 1260L,
                    writeStart = 430L,
                    readEnd = 1200L,
                    writeEnd = 400L,
                ),
                // ROAD 2233 Scenario 4 – only read regresses
                NegativeDeltaCase(
                    name = "only_read_regresses",
                    readStart = 1260L,
                    writeStart = 400L,
                    readEnd = 1200L,
                    writeEnd = 430L,
                ),
                // ROAD 2233 Scenario 4 – only write regresses
                NegativeDeltaCase(
                    name = "only_write_regresses",
                    readStart = 1200L,
                    writeStart = 430L,
                    readEnd = 1260L,
                    writeEnd = 400L,
                ),
            )
    }

    @Parameterized.Parameter
    lateinit var testCase: NegativeDeltaCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-negative-delta", null)
    }

    @Test
    fun omitsAllDiskMetricsWhenAnyDeltaIsNegative() {
        writeIoFile(syscr = testCase.readStart, syscw = testCase.writeStart)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(START_NS, START_NS, END_NS)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = testCase.readEnd, syscw = testCase.writeEnd)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)
            span.end()

            // Android omits all disk attrs when any delta is negative (no negative values emitted).
            Assert.assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            Assert.assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            Assert.assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
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

    internal data class NegativeDeltaCase(
        val name: String,
        val readStart: Long,
        val writeStart: Long,
        val readEnd: Long,
        val writeEnd: Long,
    ) {
        override fun toString(): String = name
    }
}