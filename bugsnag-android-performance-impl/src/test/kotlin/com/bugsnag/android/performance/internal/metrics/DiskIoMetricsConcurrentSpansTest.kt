package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ROAD 2233 – Scenario 7 (Android):
 * Concurrent spans each compute independent disk IOPS without snapshot collision.
 *
 * Timeline:
 * - Span A starts at T0, Span B starts at T1 (T1 > T0)
 * - Span B ends at T2, Span A ends at T3
 * - Span B IOPS covers (T2 - T1) only; Span A IOPS covers (T3 - T0) only
 *
 * Exact counter progression cannot be controlled in Maze Runner on a real device, so this
 * scenario is covered here with injectable io fixtures and mocked clocks.
 */
internal class DiskIoMetricsConcurrentSpansTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-concurrent", null)
    }

    @Test
    fun overlappingSpansComputeIndependentDiskIops() {
        val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(
                    T0,
                    T0, // Span A start (createStartMetrics logs then snapshots)
                    T1,
                    T1, // Span B start
                    T2, // Span B end
                    T3, // Span A end
                )

            writeIoFile(syscr = 1000L, syscw = 500L)
            val snapshotA =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )

            writeIoFile(syscr = 1060L, syscw = 530L)
            val snapshotB =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )

            assertNotNull(snapshotA)
            assertNotNull(snapshotB)

            writeIoFile(syscr = 1080L, syscw = 540L)
            val spanB = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            snapshotB!!.finish(spanB)

            writeIoFile(syscr = 1120L, syscw = 560L)
            val spanA = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            snapshotA!!.finish(spanA)

            // Span B: (T2 - T1) = 1s, delta read=20 write=10
            assertEquals(20.0, spanB.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(10.0, spanB.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(30.0, spanB.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])

            // Span A: (T3 - T0) = 4s, delta read=120 write=60
            assertEquals(30.0, spanA.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(15.0, spanA.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(45.0, spanA.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])

            assertNotEquals(
                spanA.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL],
                spanB.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL],
            )
        }
    }

    @Test
    fun finishingNestedSpanDoesNotAffectOuterSpanMetrics() {
        val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(
                    T0,
                    T0,
                    T1,
                    T1,
                    T2,
                    T3,
                )

            writeIoFile(syscr = 200L, syscw = 100L)
            val outerSnapshot =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )

            writeIoFile(syscr = 220L, syscw = 110L)
            val innerSnapshot =
                SpanMetricsSnapshot.createIfRequired(
                    renderingMetricsSource = null,
                    cpuMetricsSource = null,
                    memoryMetricsSource = null,
                    diskIoMetricsSource = diskSource,
                )

            writeIoFile(syscr = 230L, syscw = 115L)
            innerSnapshot!!.finish(TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE))

            writeIoFile(syscr = 280L, syscw = 140L)
            val outerSpan = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            outerSnapshot!!.finish(outerSpan)

            // Inner span ended first; outer span still gets metrics over its full T0–T3 window.
            assertEquals(20.0, outerSpan.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
            assertEquals(10.0, outerSpan.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
            assertEquals(30.0, outerSpan.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
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
        private const val T0 = 1_000_000_000L
        private const val T1 = 2_000_000_000L
        private const val T2 = 3_000_000_000L
        private const val T3 = 5_000_000_000L
    }
}
