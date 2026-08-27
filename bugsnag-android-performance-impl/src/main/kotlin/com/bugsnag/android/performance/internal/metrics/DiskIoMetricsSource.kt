package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl

internal class DiskIoMetricsSource(
    private val reader: ProcIoReader = ProcIoReader(),
) : MetricSource<DiskIoSnapshot> {

    // Reusable counters to avoid allocation per read.
    private val counters = ProcIoReader.IoCounters()

    override fun createStartMetrics(): DiskIoSnapshot {
        val success = reader.parse(counters)
        return if (success) {
            DiskIoSnapshot(
                readSyscalls = counters.readSyscalls,
                writeSyscalls = counters.writeSyscalls,
                timestampNanos = SystemClock.elapsedRealtimeNanos(),
            )
        } else {
            Logger.w("Failed to read /proc/self/io at span start")
            DiskIoSnapshot(
                readSyscalls = -1L,
                writeSyscalls = -1L,
                timestampNanos = 0L,
            )
        }
    }

    override fun endMetrics(
        startMetrics: DiskIoSnapshot,
        span: Span,
    ) {
        // Guard 1: start snapshot was invalid
        if (!startMetrics.isValid) {
            Logger.w("Disk I/O metrics: Invalid start snapshot")
            return
        }

        // Guard 2: end read fails
        if (!reader.parse(counters)) {
            Logger.w("Disk I/O metrics: Failed to read /proc/self/io at span end")
            return
        }

        val endTimestamp = SystemClock.elapsedRealtimeNanos()

        // Guard 3: duration must be positive
        val durationNanos = endTimestamp - startMetrics.timestampNanos
        if (durationNanos <= 0L) {
            Logger.w("Disk I/O metrics: Invalid duration: $durationNanos nanos")
            return
        }

        val durationSec = durationNanos / NANOS_PER_SECOND

        // Guard 4: negative deltas
        val readDelta = counters.readSyscalls - startMetrics.readSyscalls
        val writeDelta = counters.writeSyscalls - startMetrics.writeSyscalls
        if (readDelta < 0L || writeDelta < 0L) {
            Logger.w("Disk I/O metrics: Negative delta detected - Read: $readDelta, Write: $writeDelta")
            return
        }

        val iopsRead = readDelta.toDouble() / durationSec
        val iopsWrite = writeDelta.toDouble() / durationSec
        val iopsTotal = iopsRead + iopsWrite

        Logger.d("=== Disk I/O Metrics - Span Ended ===")
        Logger.d("Start Read Syscalls: ${startMetrics.readSyscalls}")
        Logger.d("End Read Syscalls: ${counters.readSyscalls}")
        Logger.d("Read Delta: $readDelta (${String.format("%.2f", iopsRead)} IOPS)")
        Logger.d("Start Write Syscalls: ${startMetrics.writeSyscalls}")
        Logger.d("End Write Syscalls: ${counters.writeSyscalls}")
        Logger.d("Write Delta: $writeDelta (${String.format("%.2f", iopsWrite)} IOPS)")
        Logger.d("Total IOPS: ${String.format("%.2f", iopsTotal)}")
        Logger.d("Duration: ${String.format("%.3f", durationSec)} seconds")

        val spanImpl = span as? SpanImpl ?: return
        spanImpl.attributes[ATTR_IOPS_READ] = iopsRead
        spanImpl.attributes[ATTR_IOPS_WRITE] = iopsWrite
        spanImpl.attributes[ATTR_IOPS_TOTAL] = iopsTotal
    }

    private val DiskIoSnapshot.isValid: Boolean
        get() = readSyscalls >= 0L && writeSyscalls >= 0L && timestampNanos > 0L

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        internal const val ATTR_IOPS_READ = "bugsnag.device.disk.iops_read"
        internal const val ATTR_IOPS_WRITE = "bugsnag.device.disk.iops_write"
        internal const val ATTR_IOPS_TOTAL = "bugsnag.device.disk.iops_total"
    }
}




