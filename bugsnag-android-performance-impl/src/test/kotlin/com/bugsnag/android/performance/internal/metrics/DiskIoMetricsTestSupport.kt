package com.bugsnag.android.performance.internal.metrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.io.File

/**
 * Shared diagnostics for disk IOPS unit tests. Surfaces ProcIoReader and SystemClock setup
 * failures with actionable messages instead of null attribute assertions or NPEs from casts.
 */
internal object DiskIoMetricsTestSupport {
    fun assertStartSnapshotValid(
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
            "SystemClock.elapsedRealtimeNanos was not set for span start " +
                    "(timestampNanos=${startSnapshot.timestampNanos}). " +
                    "Use Robolectric ShadowSystemClock.advanceBy or mockStatic(SystemClock) on this JVM.",
            startSnapshot.timestampNanos > 0L,
        )
        assertEquals(
            "Span start timestamp does not match the controlled clock " +
                    "(expected $expectedTimestampNanos, got ${startSnapshot.timestampNanos}).",
            expectedTimestampNanos,
            startSnapshot.timestampNanos,
        )
    }

    fun assertEndIoFileReadable(
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

    fun assertPositiveDuration(
        endTimestampNanos: Long,
        startSnapshot: DiskIoSnapshot,
    ) {
        assertTrue(
            "Expected positive span duration but end timestamp ($endTimestampNanos) <= " +
                    "start timestamp (${startSnapshot.timestampNanos}). " +
                    "Advance the test clock before endMetrics (ShadowSystemClock or mockStatic).",
            endTimestampNanos > startSnapshot.timestampNanos,
        )
    }

    fun assertDiskIopsOnSpan(
        span: com.bugsnag.android.performance.internal.SpanImpl,
        expectedRead: Double,
        expectedWrite: Double,
        expectedTotal: Double,
        tolerance: Double = 0.001,
    ) {
        assertNotNull(
            "Disk IOPS read attribute was not set on span. " +
                    "If diagnostics above passed, check DiskIoMetricsSource guard paths.",
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ],
        )
        assertNotNull(
            "Disk IOPS write attribute was not set on span.",
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE],
        )
        assertNotNull(
            "Disk IOPS total attribute was not set on span.",
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL],
        )
        assertEquals(
            expectedRead,
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_READ] as Double,
            tolerance,
        )
        assertEquals(
            expectedWrite,
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_WRITE] as Double,
            tolerance,
        )
        assertEquals(
            expectedTotal,
            span.attributes[DiskIoMetricsSource.ATTR_IOPS_TOTAL] as Double,
            tolerance,
        )
    }

    fun writeIoFile(
        ioFile: File,
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
}
