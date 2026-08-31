package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters
import java.io.File

/**
 * ROAD 2233 – Scenario 9 (Android):
 * Disk IOPS is captured correctly across app lifecycle transitions.
 *
 * [DiskIoMetricsSource] does not branch on foreground/background — metrics depend only on
 * syscr/syscw snapshots and elapsedRealtimeNanos at span start/end. Lifecycle rows therefore
 * verify that transitions do not alter the metric path; timelines model when start/end occur
 * relative to background/termination.
 *
 * Exact counter injection is not possible in Maze Runner on a real device, so these cases are
 * covered here instead of in features/full_tests.
 */
@RunWith(Parameterized::class)
internal class DiskIoMetricsLifecycleTest {
    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L

        @get:JvmStatic
        @get:Parameters(name = "{0}")
        val parameters =
            listOf(
                // foreground → background → foreground mid-span
                LifecycleCase(
                    name = "mid_span_background_transition",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1120L,
                    writeEnd = 560L,
                    startNanos = NANOS_PER_SECOND,
                    endNanos = 5L * NANOS_PER_SECOND,
                    expectedRead = 30.0,
                    expectedWrite = 15.0,
                    expectedTotal = 45.0,
                    finishSpan = true,
                ),
                // span ends while app remains in background
                LifecycleCase(
                    name = "ends_while_in_background",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1060L,
                    writeEnd = 530L,
                    startNanos = NANOS_PER_SECOND,
                    endNanos = 3L * NANOS_PER_SECOND,
                    expectedRead = 30.0,
                    expectedWrite = 15.0,
                    expectedTotal = 45.0,
                    finishSpan = true,
                ),
                // span starts while app is in background
                LifecycleCase(
                    name = "starts_in_background",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1020L,
                    writeEnd = 510L,
                    startNanos = NANOS_PER_SECOND,
                    endNanos = 2L * NANOS_PER_SECOND,
                    expectedRead = 20.0,
                    expectedWrite = 10.0,
                    expectedTotal = 30.0,
                    finishSpan = true,
                ),
                // app terminated mid-span — snapshot captured but span never ends
                LifecycleCase(
                    name = "orphaned_on_termination",
                    readStart = 1000L,
                    writeStart = 500L,
                    readEnd = 1060L,
                    writeEnd = 530L,
                    startNanos = NANOS_PER_SECOND,
                    endNanos = 3L * NANOS_PER_SECOND,
                    expectedRead = 0.0,
                    expectedWrite = 0.0,
                    expectedTotal = 0.0,
                    finishSpan = false,
                ),
            )
    }

    @Parameter
    lateinit var testCase: LifecycleCase

    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-lifecycle", null)
    }

    @Test
    fun diskIopsAcrossLifecycleTransitions() {
        val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))

        withStaticMock<SystemClock> { clock ->
            if (testCase.finishSpan) {
                clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                    .thenReturn(
                        testCase.startNanos,
                        testCase.startNanos,
                        testCase.endNanos,
                    )
            } else {
                clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                    .thenReturn(
                        testCase.startNanos,
                        testCase.startNanos,
                    )
            }

            writeIoFile(syscr = testCase.readStart, syscw = testCase.writeStart)
            val snapshot =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )
            assertNotNull(snapshot)

            val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)

            if (testCase.finishSpan) {
                writeIoFile(syscr = testCase.readEnd, syscw = testCase.writeEnd)
                snapshot!!.finish(span)

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
            } else {
                // Termination before span.end(): no disk attrs delivered on the span.
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
                assertNull(span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
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

    internal data class LifecycleCase(
        val name: String,
        val readStart: Long,
        val writeStart: Long,
        val readEnd: Long,
        val writeEnd: Long,
        val startNanos: Long,
        val endNanos: Long,
        val expectedRead: Double,
        val expectedWrite: Double,
        val expectedTotal: Double,
        val finishSpan: Boolean,
    ) {
        override fun toString(): String = name
    }
}
