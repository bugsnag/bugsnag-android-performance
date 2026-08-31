package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 11 (Android):
 * Disk IOPS values are valid finite Float64 under high and burst I/O conditions.
 *
 * Workloads are modelled with injectable syscr/syscw counter deltas. Burst I/O is averaged
 * over the full span duration per the formula (end - start) / durationSec.
 *
 * Real SQLite / file-copy workloads cannot yield deterministic IOPS in Maze Runner, so these
 * cases are covered here instead of in features/full_tests.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsHighIopsTest {
    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                // intensive SQLite (1000+ queries) – high read IOPS, large but valid
                HighIopsCase(
                    name = "intensive_sqlite",
                    readStart = 0L,
                    writeStart = 0L,
                    readEnd = 200_000L,
                    writeEnd = 0L,
                    durationSec = 2.0,
                    expectedRead = 100_000.0,
                    expectedWrite = 0.0,
                    expectedTotal = 100_000.0,
                ),
                // 10MB burst write then idle – write averaged over full 10s duration
                HighIopsCase(
                    name = "burst_write_then_idle",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1000L,
                    writeEnd = 5500L,
                    durationSec = 10.0,
                    expectedRead = 0.0,
                    expectedWrite = 500.0,
                    expectedTotal = 500.0,
                ),
                // 50MB file copy – both read and write > 0
                HighIopsCase(
                    name = "large_file_copy",
                    readStart = 10_000L,
                    writeStart = 10_000L,
                    readEnd = 22_500L,
                    writeEnd = 22_500L,
                    durationSec = 5.0,
                    expectedRead = 2500.0,
                    expectedWrite = 2500.0,
                    expectedTotal = 5000.0,
                ),
            )
    }

    @Parameter
    lateinit var testCase: HighIopsCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-high-iops", null)
    }

    @Test
    fun emitsFiniteDoubleIopsForHighAndBurstWorkloads() {
        writeIoFile(syscr = testCase.readStart, syscw = testCase.writeStart)

        val startNanos = NANOS_PER_SECOND
        val endNanos = startNanos + (testCase.durationSec * NANOS_PER_SECOND).toLong()

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(startNanos, startNanos, endNanos)

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = testCase.readEnd, syscw = testCase.writeEnd)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            val iopsRead = span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ] as Double
            val iopsWrite = span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE] as Double
            val iopsTotal = span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL] as Double

            assertTrue(iopsRead.isFinite())
            assertTrue(iopsWrite.isFinite())
            assertTrue(iopsTotal.isFinite())
            assertFalse(iopsRead.isNaN())
            assertFalse(iopsWrite.isNaN())
            assertFalse(iopsTotal.isNaN())

            assertEquals(testCase.expectedRead, iopsRead, 0.001)
            assertEquals(testCase.expectedWrite, iopsWrite, 0.001)
            assertEquals(testCase.expectedTotal, iopsTotal, 0.001)
            assertEquals(iopsRead + iopsWrite, iopsTotal, 0.001)

            if (testCase.name == "large_file_copy") {
                assertTrue(iopsRead > 0.0)
                assertTrue(iopsWrite > 0.0)
            }
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

    internal data class HighIopsCase(
        val name: String,
        val readStart: Long,
        val writeStart: Long,
        val readEnd: Long,
        val writeEnd: Long,
        val durationSec: Double,
        val expectedRead: Double,
        val expectedWrite: Double,
        val expectedTotal: Double,
    ) {
        override fun toString(): String = name
    }
}
