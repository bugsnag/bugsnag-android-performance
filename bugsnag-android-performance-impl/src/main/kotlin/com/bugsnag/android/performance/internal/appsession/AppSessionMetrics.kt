package com.bugsnag.android.performance.internal.appsession

/**
 * Aggregated metrics for one app-session segment span (foreground or background).
 *
 * All three metric categories expose the same four aggregates:
 *   - count  : number of samples collected
 *   - min    : lowest observed value
 *   - max    : highest observed value
 *   - mean   : arithmetic average
 *
 * CPU values are percentages (0–100).
 * Memory values are bytes.
 */
internal data class AppSessionMetrics(
    // ── CPU (process-level, normalised across cores) ─────────────────────────
    val cpuCount: Int,
    val cpuMin: Double,
    val cpuMax: Double,
    val cpuMean: Double,
    val cpuSamples: DoubleArray,
    val cpuMainThreadSamples: DoubleArray,
    val cpuOverheadSamples: DoubleArray,
    val cpuMainThreadMin: Double,
    val cpuMainThreadMax: Double,
    val cpuMainThreadMean: Double,
    val cpuOverheadMin: Double,
    val cpuOverheadMax: Double,
    val cpuOverheadMean: Double,
    val cpuTimestamps: LongArray,

    // ── Runtime / ART heap memory ────────────────────────────────────────────
    val runtimeMemoryCount: Int,
    val runtimeMemoryMinBytes: Long,
    val runtimeMemoryMaxBytes: Long,
    val runtimeMemoryMeanBytes: Long,
    val runtimeMemorySamplesBytes: LongArray,
    val runtimeMemoryTimestamps: LongArray,

    // ── Device memory (PSS – physical memory used by this process) ───────────
    val deviceMemoryCount: Int,
    val deviceMemoryMinBytes: Long,
    val deviceMemoryMaxBytes: Long,
    val deviceMemoryMeanBytes: Long,
    val deviceMemorySamplesBytes: LongArray,
    val deviceMemoryTimestamps: LongArray,
    val deviceMemorySizeBytes: Long,
) {
    companion object {
        /** Returned when a collector is stopped before any sample was taken. */
        val EMPTY = AppSessionMetrics(
            cpuCount = 0, cpuMin = 0.0, cpuMax = 0.0, cpuMean = 0.0,
            cpuSamples = doubleArrayOf(),
            cpuMainThreadSamples = doubleArrayOf(),
            cpuOverheadSamples = doubleArrayOf(),
            cpuMainThreadMin = 0.0, cpuMainThreadMax = 0.0, cpuMainThreadMean = 0.0,
            cpuOverheadMin = 0.0, cpuOverheadMax = 0.0, cpuOverheadMean = 0.0,
            cpuTimestamps = longArrayOf(),
            runtimeMemoryCount = 0,
            runtimeMemoryMinBytes = 0L, runtimeMemoryMaxBytes = 0L, runtimeMemoryMeanBytes = 0L,
            runtimeMemorySamplesBytes = longArrayOf(), runtimeMemoryTimestamps = longArrayOf(),
            deviceMemoryCount = 0,
            deviceMemoryMinBytes = 0L, deviceMemoryMaxBytes = 0L, deviceMemoryMeanBytes = 0L,
            deviceMemorySamplesBytes = longArrayOf(), deviceMemoryTimestamps = longArrayOf(),
            deviceMemorySizeBytes = 0L,
        )
    }
}

