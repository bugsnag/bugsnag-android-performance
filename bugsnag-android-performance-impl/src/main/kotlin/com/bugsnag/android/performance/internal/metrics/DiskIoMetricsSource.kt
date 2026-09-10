package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.SpanImpl
import kotlin.math.roundToLong

internal class DiskIoMetricsSource(
    private val reader: ProcIoReader = ProcIoReader(),
) : MetricSource<DiskIoSnapshot> {

    override fun createStartMetrics(): DiskIoSnapshot {
        val counters = ProcIoReader.IoCounters()
        val status = reader.parse(counters)
        return if (status == "ok") {
            DiskIoSnapshot(
                readSyscalls = counters.readSyscalls,
                writeSyscalls = counters.writeSyscalls,
                timestampNanos = SystemClock.elapsedRealtimeNanos(),
                status = "ok",
            )
        } else {
            Logger.w("DiskIoMetricsSource: Failed to read /proc/self/io at span start: $status")
            DiskIoSnapshot(
                readSyscalls = -1L,
                writeSyscalls = -1L,
                timestampNanos = SystemClock.elapsedRealtimeNanos(),
                status = status,
            )
        }
    }

    override fun endMetrics(
        startMetrics: DiskIoSnapshot,
        span: Span,
    ) {
        val spanImpl = span as? SpanImpl ?: run {
            Logger.w("Disk I/O metrics skipped: Span is not a SpanImpl ($span)")
            return
        }

        // Always set a canary to prove this method was called
        spanImpl.attributes[ATTR_CANARY] = "true"

        if (startMetrics.status != "ok") {
            val reason = "start_parse_failed_${startMetrics.status}"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        if (!startMetrics.isValid) {
            val reason = "invalid_start_snapshot_r${startMetrics.readSyscalls}_w${startMetrics.writeSyscalls}_ts${startMetrics.timestampNanos}"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        val counters = ProcIoReader.IoCounters()
        val endStatus = reader.parse(counters)
        if (endStatus != "ok") {
            val reason = "end_parse_failed_$endStatus"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        val endTimestamp = endTimestampNanos(startMetrics.timestampNanos)
        val durationNanos = endTimestamp - startMetrics.timestampNanos
        if (durationNanos <= 0L) {
            val reason = "invalid_duration_${durationNanos}ns_start${startMetrics.timestampNanos}_end${endTimestamp}"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        val durationSec = durationNanos / NANOS_PER_SECOND
        val readDelta = counters.readSyscalls - startMetrics.readSyscalls
        val writeDelta = counters.writeSyscalls - startMetrics.writeSyscalls
        if (readDelta < 0L || writeDelta < 0L) {
            val reason = "negative_delta_r${readDelta}_w${writeDelta}_startR${startMetrics.readSyscalls}_endR${counters.readSyscalls}"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        val iopsRead = readDelta.toDouble() / durationSec
        val iopsWrite = writeDelta.toDouble() / durationSec
        val iopsTotal = iopsRead + iopsWrite

        if (!iopsRead.isFinite() || !iopsWrite.isFinite() || !iopsTotal.isFinite()) {
            val reason = "non_finite_iops_r${iopsRead}_w${iopsWrite}_dur${durationSec}"
            Logger.w("Disk I/O metrics skipped: $reason")
            spanImpl.attributes[ATTR_SKIP_REASON] = reason
            return
        }

        Logger.d("=== Disk I/O Metrics - Span Ended ===")
        Logger.d("Read Delta: $readDelta (${String.format("%.2f", iopsRead)} IOPS)")
        Logger.d("Write Delta: $writeDelta (${String.format("%.2f", iopsWrite)} IOPS)")
        Logger.d("Total IOPS: ${String.format("%.2f", iopsTotal)}")
        Logger.d("Duration: ${String.format("%.3f", durationSec)} seconds")

        // SDK emits all three disk IOPS attributes as IntValue (Long internally)
        spanImpl.attributes[ATTR_IOPS_READ] = iopsRead.roundToLong()
        spanImpl.attributes[ATTR_IOPS_WRITE] = iopsWrite.roundToLong()
        spanImpl.attributes[ATTR_IOPS_TOTAL] = iopsTotal.roundToLong()

        if (InternalDebug.attachDiskIoSnapshots) {
            spanImpl.attributes[ATTR_READ_START] = startMetrics.readSyscalls
            spanImpl.attributes[ATTR_READ_END] = counters.readSyscalls
            spanImpl.attributes[ATTR_WRITE_START] = startMetrics.writeSyscalls
            spanImpl.attributes[ATTR_WRITE_END] = counters.writeSyscalls
        }
    }

    private fun endTimestampNanos(startTimestampNanos: Long): Long {
        return when (InternalDebug.diskIoTimestampFault) {
            "zero" -> startTimestampNanos
            "negative" -> startTimestampNanos - 1L
            else -> SystemClock.elapsedRealtimeNanos()
        }
    }

    private val DiskIoSnapshot.isValid: Boolean
        get() = readSyscalls >= 0L && writeSyscalls >= 0L && timestampNanos > 0L

    companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000.0

        internal const val ATTR_IOPS_READ = "bugsnag.device.disk.iops_read"
        internal const val ATTR_IOPS_WRITE = "bugsnag.device.disk.iops_write"
        internal const val ATTR_IOPS_TOTAL = "bugsnag.device.disk.iops_total"

        internal const val ATTR_SKIP_REASON = "bugsnag.internal.disk_io.skip_reason"
        internal const val ATTR_CANARY = "bugsnag.internal.disk_io.end_metrics_called"
        internal const val ATTR_READ_START = "bugsnag.internal.disk_io.read_start"
        internal const val ATTR_READ_END = "bugsnag.internal.disk_io.read_end"
        internal const val ATTR_WRITE_START = "bugsnag.internal.disk_io.write_start"
        internal const val ATTR_WRITE_END = "bugsnag.internal.disk_io.write_end"
    }
}
