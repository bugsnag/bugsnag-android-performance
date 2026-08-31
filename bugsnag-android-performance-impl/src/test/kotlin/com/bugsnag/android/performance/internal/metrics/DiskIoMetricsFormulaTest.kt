package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 2 (Android):
 * Disk IOPS is computed as (end - start) / durationSec from syscr/syscw.
 *
 * These cases match the Android rows of the Scenario Outline examples table.
 * Exact counter injection is not possible in Maze Runner on a real device, so
 * formula correctness is covered here instead of in features/full_tests.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsFormulaTest {
    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                // ROAD 2233 Scenario 2 – Android true IOPS
                FormulaCase(
                    name = "android_true_iops",
                    readStart = 1200L,
                    writeStart = 400L,
                    readEnd = 1260L,
                    writeEnd = 430L,
                    durationSec = 2.0,
                    expectedRead = 30.0,
                    expectedWrite = 15.0,
                    expectedTotal = 45.0,
                ),
                // ROAD 2233 Scenario 2 – Android zero activity
                FormulaCase(
                    name = "android_zero_activity",
                    readStart = 5000L,
                    writeStart = 2000L,
                    readEnd = 5000L,
                    writeEnd = 2000L,
                    durationSec = 3.0,
                    expectedRead = 0.0,
                    expectedWrite = 0.0,
                    expectedTotal = 0.0,
                ),
            )
    }

    @Parameter
    lateinit var testCase: FormulaCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-formula", null)
    }

    @Test
    fun computesIopsUsingSyscrSyscwFormula() {
        writeIoFile(syscr = testCase.readStart, syscw = testCase.writeStart)

        val startNanos = NANOS_PER_SECOND
        val endNanos = startNanos + (testCase.durationSec * NANOS_PER_SECOND).toLong()

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(startNanos, startNanos, endNanos)

            val reader = ProcIoReader(ioFile.absolutePath)
            val source = DiskIoMetricsSource(reader)
            val startSnapshot = source.createStartMetrics()
            assertStartSnapshotValid(
                ioFile = ioFile,
                startSnapshot = startSnapshot,
                expectedReadSyscalls = testCase.readStart,
                expectedWriteSyscalls = testCase.writeStart,
                expectedTimestampNanos = startNanos,
            )

            writeIoFile(syscr = testCase.readEnd, syscw = testCase.writeEnd)
            assertEndIoFileReadable(
                reader = reader,
                ioFile = ioFile,
                expectedReadSyscalls = testCase.readEnd,
                expectedWriteSyscalls = testCase.writeEnd,
            )
            assertTrue(
                "Expected positive span duration but mocked endNanos ($endNanos) <= " +
                        "start timestamp (${startSnapshot.timestampNanos}). " +
                        "SystemClock.elapsedRealtimeNanos mock may not be applied on this JVM.",
                endNanos > startSnapshot.timestampNanos,
            )

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)

            assertNotNull(
                "Disk IOPS attributes were not set after endMetrics. " +
                        "If preconditions above passed, check DiskIoMetricsSource guard paths " +
                        "(invalid start, failed end read, non-positive duration, negative delta).",
                span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ],
            )

            assertEquals(
                testCase.expectedRead,
                span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ],
            )
            assertEquals(
                testCase.expectedWrite,
                span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE],
            )
            assertEquals(
                testCase.expectedTotal,
                span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL],
            )
        }
    }

    private fun assertStartSnapshotValid(
        ioFile: File,
        startSnapshot: DiskIoSnapshot,
        expectedReadSyscalls: Long,
        expectedWriteSyscalls: Long,
        expectedTimestampNanos: Long,
    ) {
        assertTrue(
            "ProcIoReader.parse failed at span start for ${ioFile.absolutePath} " +
                    "(readSyscalls=${startSnapshot.readSyscalls}, " +
                    "writeSyscalls=${startSnapshot.writeSyscalls}). " +
                    "Ensure ProcIoReader.kt changes are present and the temp io file is readable.",
            startSnapshot.readSyscalls >= 0L && startSnapshot.writeSyscalls >= 0L,
        )
        assertEquals(
            "Unexpected read syscalls at span start for ${ioFile.absolutePath}.",
            expectedReadSyscalls,
            startSnapshot.readSyscalls,
        )
        assertEquals(
            "Unexpected write syscalls at span start for ${ioFile.absolutePath}.",
            expectedWriteSyscalls,
            startSnapshot.writeSyscalls,
        )
        assertTrue(
            "SystemClock.elapsedRealtimeNanos mock may not be applied " +
                    "(timestampNanos=${startSnapshot.timestampNanos}). " +
                    "DiskIoMetricsSource requires timestampNanos > 0. " +
                    "Try the project JDK (Android Studio JBR) or check mockito-inline compatibility with your JVM.",
            startSnapshot.timestampNanos > 0L,
        )
        assertEquals(
            "Mocked SystemClock value was not used for span start timestamp " +
                    "(expected $expectedTimestampNanos, got ${startSnapshot.timestampNanos}). " +
                    "mockStatic(SystemClock) may not be intercepting calls on this JVM.",
            expectedTimestampNanos,
            startSnapshot.timestampNanos,
        )
    }

    private fun assertEndIoFileReadable(
        reader: ProcIoReader,
        ioFile: File,
        expectedReadSyscalls: Long,
        expectedWriteSyscalls: Long,
    ) {
        val endCounters = ProcIoReader.IoCounters()
        assertTrue(
            "ProcIoReader.parse failed at span end for ${ioFile.absolutePath}. " +
                    "Check io fixture content and ProcIoReader strict numeric parsing (ED §3.1.4).",
            reader.parse(endCounters),
        )
        assertEquals(
            "Unexpected read syscalls at span end for ${ioFile.absolutePath}.",
            expectedReadSyscalls,
            endCounters.readSyscalls,
        )
        assertEquals(
            "Unexpected write syscalls at span end for ${ioFile.absolutePath}.",
            expectedWriteSyscalls,
            endCounters.writeSyscalls,
        )
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

    internal data class FormulaCase(
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
