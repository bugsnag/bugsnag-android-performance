package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 6 (Android):
 * Disk IOPS attributes are emitted correctly for zero and asymmetric activity.
 *
 * Unlike Scenario 5 (unavailable source → omit attrs), a valid counter source with
 * unchanged counters must still emit all three attributes as 0.0.
 *
 * Exact counter injection is not possible in Maze Runner on a real device, so these
 * cases are covered here instead of in features/full_tests.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsAsymmetricActivityTest {
    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                // ROAD 2233 Scenario 6 – none (idle): attrs present, all 0.0
                AsymmetricActivityCase(
                    name = "none_idle",
                    readStart = 5000L,
                    writeStart = 2000L,
                    readEnd = 5000L,
                    writeEnd = 2000L,
                    durationSec = 3.0,
                    expectedRead = 0.0,
                    expectedWrite = 0.0,
                    expectedTotal = 0.0,
                ),
                // ROAD 2233 Scenario 6 – read-only: read > 0, write = 0.0, total = read
                AsymmetricActivityCase(
                    name = "read_only",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1060L,
                    writeEnd = 500L,
                    durationSec = 2.0,
                    expectedRead = 30.0,
                    expectedWrite = 0.0,
                    expectedTotal = 30.0,
                ),
                // ROAD 2233 Scenario 6 – write-only: read = 0.0, write > 0, total = write
                AsymmetricActivityCase(
                    name = "write_only",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1000L,
                    writeEnd = 530L,
                    durationSec = 2.0,
                    expectedRead = 0.0,
                    expectedWrite = 15.0,
                    expectedTotal = 15.0,
                ),
            )
    }

    @Parameter
    lateinit var testCase: AsymmetricActivityCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-asymmetric", null)
    }

    @Test
    fun emitsDiskIopsAttributesForZeroAndAsymmetricActivity() {
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

            val iopsRead = span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ]
            val iopsWrite = span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE]
            val iopsTotal = span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL]

            assertNotNull(iopsRead)
            assertNotNull(iopsWrite)
            assertNotNull(iopsTotal)

            assertEquals(testCase.expectedRead, iopsRead)
            assertEquals(testCase.expectedWrite, iopsWrite)
            assertEquals(testCase.expectedTotal, iopsTotal)
            assertEquals((iopsRead as Double) + (iopsWrite as Double), iopsTotal)
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

    internal data class AsymmetricActivityCase(
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
