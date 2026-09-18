package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.SpanImpl
import java.util.Locale
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
        val spanImpl = span as? SpanImpl
        if (spanImpl == null) {
            reportSkip(
                span = null,
                reason = "Span is not a SpanImpl ($span)",
            )
            return
        }

        // Always set a canary to prove this method was called
        spanImpl.attributes[ATTR_CANARY] = "true"

        when {
            startMetrics.status != "ok" ->
                reportSkip(
                    span = spanImpl,
                    reason = "start_parse_failed_${startMetrics.status}",
                )

            !startMetrics.isValid ->
                reportSkip(
                    span = spanImpl,
                    reason =
                        "invalid_start_snapshot_r${startMetrics.readSyscalls}_w${startMetrics.writeSyscalls}" +
                            "_ts${startMetrics.timestampNanos}",
                )

            else -> {
                val counters = ProcIoReader.IoCounters()
                val endStatus = reader.parse(counters)

                when (val result = computeMetrics(startMetrics, counters, endStatus)) {
                    is DiskIoComputationResult.Success ->
                        attachMetrics(
                            spanImpl = spanImpl,
                            startMetrics = startMetrics,
                            counters = counters,
                            stats = result.stats,
                        )

                    is DiskIoComputationResult.Failure ->
                        reportSkip(
                            span = spanImpl,
                            reason = result.reason,
                        )
                }
            }
        }
    }

    private fun reportSkip(
        span: SpanImpl?,
        reason: String,
    ) {
        Logger.w("Disk I/O metrics skipped: $reason")
        span?.attributes?.set(ATTR_SKIP_REASON, reason)
    }

    private fun attachMetrics(
        spanImpl: SpanImpl,
        startMetrics: DiskIoSnapshot,
        counters: ProcIoReader.IoCounters,
        stats: DiskIoStats,
    ) {
        Logger.d(String.format(Locale.US, "=== Disk I/O Metrics - Span Ended ==="))
        Logger.d(String.format(Locale.US, "Read Delta: %d (%.2f IOPS)", stats.readDelta, stats.iopsRead))
        Logger.d(String.format(Locale.US, "Write Delta: %d (%.2f IOPS)", stats.writeDelta, stats.iopsWrite))
        Logger.d(String.format(Locale.US, "Total IOPS: %.2f", stats.iopsTotal))
        Logger.d(String.format(Locale.US, "Duration: %.3f seconds", stats.durationSec))

        // Emit IntValues. Round read/write first, then derive total so
        // iops_total always equals iops_read + iops_write after integer conversion
        // (independent rounding of the float total can be off-by-one).
        val iopsRead = stats.iopsRead.roundToLong()
        val iopsWrite = stats.iopsWrite.roundToLong()
        spanImpl.attributes[ATTR_IOPS_READ] = iopsRead
        spanImpl.attributes[ATTR_IOPS_WRITE] = iopsWrite
        spanImpl.attributes[ATTR_IOPS_TOTAL] = iopsRead + iopsWrite

        if (InternalDebug.attachDiskIoSnapshots) {
            spanImpl.attributes[ATTR_READ_START] = startMetrics.readSyscalls
            spanImpl.attributes[ATTR_READ_END] = counters.readSyscalls
            spanImpl.attributes[ATTR_WRITE_START] = startMetrics.writeSyscalls
            spanImpl.attributes[ATTR_WRITE_END] = counters.writeSyscalls
        }
    }

    private fun computeMetrics(
        startMetrics: DiskIoSnapshot,
        counters: ProcIoReader.IoCounters,
        endStatus: String,
    ): DiskIoComputationResult {
        val endTimestamp = endTimestampNanos(startMetrics.timestampNanos)
        val durationNanos = endTimestamp - startMetrics.timestampNanos
        val durationSec = durationNanos / NANOS_PER_SECOND
        val readDelta = counters.readSyscalls - startMetrics.readSyscalls
        val writeDelta = counters.writeSyscalls - startMetrics.writeSyscalls
        val iopsRead = readDelta.toDouble() / durationSec
        val iopsWrite = writeDelta.toDouble() / durationSec
        val iopsTotal = iopsRead + iopsWrite

        return when {
            endStatus != "ok" ->
                DiskIoComputationResult.Failure("end_parse_failed_$endStatus")

            durationNanos <= 0L ->
                DiskIoComputationResult.Failure(
                    "invalid_duration_${durationNanos}ns_start${startMetrics.timestampNanos}" +
                        "_end$endTimestamp",
                )

            readDelta < 0L || writeDelta < 0L ->
                DiskIoComputationResult.Failure(
                    "negative_delta_r${readDelta}_w${writeDelta}_startR${startMetrics.readSyscalls}" +
                        "_endR${counters.readSyscalls}",
                )

            !iopsRead.isFinite() || !iopsWrite.isFinite() || !iopsTotal.isFinite() ->
                DiskIoComputationResult.Failure(
                    "non_finite_iops_r${iopsRead}_w${iopsWrite}_dur$durationSec",
                )

            else ->
                DiskIoComputationResult.Success(
                    DiskIoStats(
                        readDelta = readDelta,
                        writeDelta = writeDelta,
                        durationSec = durationSec,
                        iopsRead = iopsRead,
                        iopsWrite = iopsWrite,
                        iopsTotal = iopsTotal,
                    ),
                )
        }
    }

    private fun endTimestampNanos(startTimestampNanos: Long): Long {
        return when (InternalDebug.diskIoTimestampFault) {
            "zero" -> startTimestampNanos
            "negative" -> startTimestampNanos - 1L
            else -> SystemClock.elapsedRealtimeNanos()
        }
    }

    private data class DiskIoStats(
        val readDelta: Long,
        val writeDelta: Long,
        val durationSec: Double,
        val iopsRead: Double,
        val iopsWrite: Double,
        val iopsTotal: Double,
    )

    private sealed interface DiskIoComputationResult {
        data class Success(val stats: DiskIoStats) : DiskIoComputationResult

        data class Failure(val reason: String) : DiskIoComputationResult
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
