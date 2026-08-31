package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ROAD 2233 – Scenario 8 (Android):
 * Orphaned span snapshots do not corrupt disk IOPS for spans that end normally.
 *
 * 50 spans start but never end; 100 additional spans start and end normally. The shared
 * [DiskIoMetricsSource] does not retain snapshots in global state — each lives on its
 * [SpanMetricsSnapshot] until finish or span GC — so completed spans still report correct IOPS.
 *
 * OOM with 50 orphaned snapshots is not asserted here (object overhead is tiny and flaky in CI).
 * Exact counter injection is not possible in Maze Runner, so this is covered with unit tests.
 */
internal class DiskIoMetricsOrphanedSpansTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-orphaned", null)
    }

    @Test
    fun completedSpansReportCorrectDiskIopsWithOrphanedSnapshotsPresent() {
        val diskSource = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
        var syscr = 1000L
        var syscw = 500L
        var clockNanos = NANOS_PER_SECOND

        // Hold orphaned snapshots for the lifetime of the test (spans started but never ended).
        val orphanedSnapshots = ArrayList<SpanMetricsSnapshot>(ORPHANED_SPAN_COUNT)

        withStaticMock<SystemClock> { clock ->
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos).thenAnswer { clockNanos }

            // Orphaned spans: capture start snapshots only.
            repeat(ORPHANED_SPAN_COUNT) {
                writeIoFile(syscr = syscr, syscw = syscw)
                syscr += 1L
                syscw += 1L
                clockNanos += 100_000_000L

                val snapshot =
                    SpanMetricsSnapshot.createIfRequired(
                        renderingMetricsSource = null,
                        cpuMetricsSource = null,
                        memoryMetricsSource = null,
                        diskIoMetricsSource = diskSource,
                    )
                assertNotNull(snapshot)
                orphanedSnapshots.add(snapshot!!)
            }

            assertEquals(ORPHANED_SPAN_COUNT, orphanedSnapshots.size)

            // Completed spans: each advances counters over a fixed 2s window.
            repeat(COMPLETED_SPAN_COUNT) {
                val startTime = clockNanos
                writeIoFile(syscr = syscr, syscw = syscw)

                val snapshot =
                    SpanMetricsSnapshot.createIfRequired(
                        renderingMetricsSource = null,
                        cpuMetricsSource = null,
                        memoryMetricsSource = null,
                        diskIoMetricsSource = diskSource,
                    )
                assertNotNull(snapshot)

                syscr += READ_DELTA_PER_SPAN
                syscw += WRITE_DELTA_PER_SPAN
                clockNanos = startTime + SPAN_DURATION_NS
                writeIoFile(syscr = syscr, syscw = syscw)

                val span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
                snapshot!!.finish(span)

                assertEquals(EXPECTED_READ_IOPS, span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ])
                assertEquals(EXPECTED_WRITE_IOPS, span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE])
                assertEquals(EXPECTED_TOTAL_IOPS, span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL])
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

    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val ORPHANED_SPAN_COUNT = 50
        private const val COMPLETED_SPAN_COUNT = 100
        private const val SPAN_DURATION_NS = 2L * NANOS_PER_SECOND
        private const val READ_DELTA_PER_SPAN = 20L
        private const val WRITE_DELTA_PER_SPAN = 10L
        private const val EXPECTED_READ_IOPS = 10.0
        private const val EXPECTED_WRITE_IOPS = 5.0
        private const val EXPECTED_TOTAL_IOPS = 15.0
    }
}
