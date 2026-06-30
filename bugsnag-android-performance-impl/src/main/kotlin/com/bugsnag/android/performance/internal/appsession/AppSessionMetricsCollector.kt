package com.bugsnag.android.performance.internal.appsession

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.AndroidException
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.getActivityManager
import com.bugsnag.android.performance.internal.metrics.ProcStatReader
import com.bugsnag.android.performance.internal.metrics.SystemConfig
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Samples CPU, runtime (ART heap) memory, and device (PSS) memory at a fixed interval while an
 * app-session segment span is active. Call [start] when the span begins and [stop] when it ends;
 * [stop] returns the fully-aggregated [AppSessionMetrics] for the span.
 *
 * All sampling runs on a single shared background thread to minimise overhead.
 */
@Suppress("TooManyFunctions")
internal class AppSessionMetricsCollector(
    private val appContext: Context,
    private val enabledMetrics: EnabledMetrics = EnabledMetrics(false),
    private val samplingIntervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    // ── CPU state ─────────────────────────────────────────────────────────────
    private val procStatReader = ProcStatReader("/proc/${Process.myPid()}/stat")
    private val procStat = ProcStatReader.Stat()
    private var previousCpuTime = 0.0
    private var previousUptime = 0.0

    private val mainThreadTid = IntArray(1)
    private var mainThreadStatReader: ProcStatReader? = null
    private val mainThreadStat = ProcStatReader.Stat()
    private var previousMainThreadCpuTime = 0.0

    private var overheadStatReader: ProcStatReader? = null
    private val overheadStat = ProcStatReader.Stat()
    private var previousOverheadCpuTime = 0.0

    // ── Accumulator fields (written only on sampler thread) ──────────────────
    private val accumulators = Accumulators()

    // ── ActivityManager for PSS ───────────────────────────────────────────────
    private val physicalDeviceMemory = calculateTotalMemory()

    // ── Scheduler ────────────────────────────────────────────────────────────
    private var future: ScheduledFuture<*>? = null

    /** Call from the thread that starts the session segment span. */
    @Synchronized
    fun start() {
        if (!enabledMetrics.cpu && !enabledMetrics.memory) return

        // Immediate fallback: the main thread TID is usually the same as PID
        if (mainThreadTid[0] == 0) {
            mainThreadTid[0] = Process.myPid()
        }
        Loopers.onMainThread {
            mainThreadTid[0] = Process.myTid()
        }
        accumulators.reset()
        overheadStatReader = null
        // Prime the CPU samplers so the first delta is meaningful
        primeCpuSampler()

        future =
            sharedScheduler.scheduleWithFixedDelay(
                { takeSample() },
                // Start immediately to prime overhead sampler on the background thread
                0,
                samplingIntervalMs,
                TimeUnit.MILLISECONDS,
            )
    }

    /**
     * Stops sampling and returns the aggregated metrics.
     * Safe to call even if [start] was never called – returns [AppSessionMetrics.EMPTY].
     */
    @Synchronized
    fun stop(): AppSessionMetrics {
        future?.cancel(false)
        future = null

        // Always take a final sample to ensure we capture the state at the end
        // and that samplers needing two points (like overhead) get them.
        takeSample()

        return buildMetrics()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    @Synchronized
    private fun primeCpuSampler() {
        if (!enabledMetrics.cpu) return

        previousUptime = SystemClock.elapsedRealtime() / MS_PER_SECOND

        // Read once to establish a baseline so the first real sample has a valid delta
        if (procStatReader.parse(procStat)) {
            previousCpuTime = procStat.totalCpuTime / SystemConfig.clockTickHz

            val tid = mainThreadTid[0]
            if (tid > 0) {
                mainThreadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
                if (mainThreadStatReader?.parse(mainThreadStat) == true) {
                    previousMainThreadCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
                }
            }
        }
    }

    @Synchronized
    private fun takeSample() {
        @Suppress("TooGenericExceptionCaught")
        try {
            val timestamp = BugsnagClock.currentUnixNanoTime()
            if (enabledMetrics.cpu) sampleCpu(timestamp)
            if (enabledMetrics.memory) {
                sampleRuntimeMemory(timestamp)
                sampleDeviceMemory(timestamp)
            }
        } catch (e: Throwable) {
            Logger.w("AppSessionMetricsCollector failed to take sample", e)
        }
    }

    // ── CPU sample ────────────────────────────────────────────────────────────

    @Synchronized
    private fun sampleCpu(timestamp: Long) {
        if (!procStatReader.parse(procStat)) return

        val uptimeSec = SystemClock.elapsedRealtime() / MS_PER_SECOND
        val totalCpuTime = procStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousCpuTime
        val deltaUptime = uptimeSec - previousUptime

        if (deltaUptime > 0.0) {
            // Update baselines for the NEXT sample
            previousCpuTime = totalCpuTime
            previousUptime = uptimeSec

            val cpuPct = (PERCENT_100 * deltaCpu / deltaUptime) / SystemConfig.numCores
            if (cpuPct.isFinite() && cpuPct >= 0.0) {
                accumulators.addCpuSample(cpuPct, timestamp)
                sampleMainThreadCpu(deltaUptime)
                sampleOverheadCpu(deltaUptime)
            }
        }
    }

    @Synchronized
    private fun sampleMainThreadCpu(deltaUptime: Double) {
        val reader =
            mainThreadStatReader ?: run {
                val tid = mainThreadTid[0]
                if (tid > 0) {
                    mainThreadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
                    if (mainThreadStatReader?.parse(mainThreadStat) == true) {
                        previousMainThreadCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
                    }
                }
                return
            }
        if (reader.parse(mainThreadStat)) {
            val totalCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
            val deltaCpu = totalCpuTime - previousMainThreadCpuTime
            previousMainThreadCpuTime = totalCpuTime

            val cpuPct = (PERCENT_100 * deltaCpu / deltaUptime) / SystemConfig.numCores
            if (cpuPct.isFinite() && cpuPct >= 0.0) {
                accumulators.addMainThreadCpuSample(cpuPct)
            }
        }
    }

    @Synchronized
    private fun sampleOverheadCpu(deltaUptime: Double) {
        val reader =
            overheadStatReader ?: run {
                val tid = Process.myTid()
                overheadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
                if (overheadStatReader?.parse(overheadStat) == true) {
                    previousOverheadCpuTime = overheadStat.totalCpuTime / SystemConfig.clockTickHz
                }
                return
            }

        if (reader.parse(overheadStat)) {
            val totalCpuTime = overheadStat.totalCpuTime / SystemConfig.clockTickHz
            val deltaCpu = totalCpuTime - previousOverheadCpuTime
            previousOverheadCpuTime = totalCpuTime

            val cpuPct = (PERCENT_100 * deltaCpu / deltaUptime) / SystemConfig.numCores
            if (cpuPct.isFinite() && cpuPct >= 0.0) {
                accumulators.addOverheadCpuSample(cpuPct)
            }
        }
    }

    // ── Runtime (ART heap) memory sample ──────────────────────────────────────

    @Synchronized
    private fun sampleRuntimeMemory(timestamp: Long) {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        if (used > 0L) {
            accumulators.addRuntimeMemorySample(used, timestamp)
        }
    }

    // ── Device memory (PSS) sample ────────────────────────────────────────────

    @Synchronized
    private fun sampleDeviceMemory(timestamp: Long) {
        val memInfo = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memInfo)

        val pssBytes =
            (memInfo.dalvikPss.toLong() + memInfo.nativePss + memInfo.otherPss) * KILOBYTE

        if (pssBytes > 0L) {
            accumulators.addDeviceMemorySample(pssBytes, timestamp)
        }
    }

    // ── Build result ──────────────────────────────────────────────────────────

    @Synchronized
    private fun buildMetrics(): AppSessionMetrics {
        val builder = AppSessionMetricsBuilder()
        accumulators.applyTo(builder)
        if (enabledMetrics.memory) {
            builder.physicalDeviceMemory = physicalDeviceMemory ?: 0L
        }
        return builder.build()
    }

    private fun calculateTotalMemory(): Long? {
        val totalMemory =
            appContext.getActivityManager()
                ?.let { am -> ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) } }
                ?.totalMem
        if (totalMemory != null) {
            return totalMemory
        }

        return runCatching {
            @Suppress("PrivateApi")
            AndroidException::class.java.getDeclaredMethod("getTotalMemory").invoke(null) as Long?
        }.getOrNull()
    }

    private class Accumulators {
        var cpuCount = 0
        var cpuSum = 0.0
        var cpuMin = Double.MAX_VALUE
        var cpuMax = 0.0
        val cpuSamples = mutableListOf<Double>()
        val cpuTimestamps = mutableListOf<Long>()

        var cpuMainThreadMin = Double.MAX_VALUE
        var cpuMainThreadMax = 0.0
        var cpuMainThreadSum = 0.0
        var cpuMainThreadCount = 0
        val cpuMainThreadSamples = mutableListOf<Double>()

        var cpuOverheadMin = Double.MAX_VALUE
        var cpuOverheadMax = 0.0
        var cpuOverheadSum = 0.0
        var cpuOverheadCount = 0
        val cpuOverheadSamples = mutableListOf<Double>()

        var runtimeCount = 0
        var runtimeSum = 0L
        var runtimeMin = Long.MAX_VALUE
        var runtimeMax = Long.MIN_VALUE
        val runtimeSamples = mutableListOf<Long>()
        val runtimeTimestamps = mutableListOf<Long>()

        var deviceCount = 0
        var deviceSum = 0L
        var deviceMin = Long.MAX_VALUE
        var deviceMax = Long.MIN_VALUE
        val deviceSamples = mutableListOf<Long>()
        val deviceTimestamps = mutableListOf<Long>()

        fun reset() {
            cpuCount = 0
            cpuSum = 0.0
            cpuMin = Double.MAX_VALUE
            cpuMax = 0.0
            cpuSamples.clear()
            cpuTimestamps.clear()
            cpuMainThreadMin = Double.MAX_VALUE
            cpuMainThreadMax = 0.0
            cpuMainThreadSum = 0.0
            cpuMainThreadCount = 0
            cpuMainThreadSamples.clear()
            cpuOverheadMin = Double.MAX_VALUE
            cpuOverheadMax = 0.0
            cpuOverheadSum = 0.0
            cpuOverheadCount = 0
            cpuOverheadSamples.clear()
            runtimeCount = 0
            runtimeSum = 0L
            runtimeMin = Long.MAX_VALUE
            runtimeMax = Long.MIN_VALUE
            runtimeSamples.clear()
            runtimeTimestamps.clear()
            deviceCount = 0
            deviceSum = 0L
            deviceMin = Long.MAX_VALUE
            deviceMax = Long.MIN_VALUE
            deviceSamples.clear()
            deviceTimestamps.clear()
        }

        fun addCpuSample(
            cpuPct: Double,
            timestamp: Long,
        ) {
            cpuCount++
            cpuSum += cpuPct
            cpuMin = cpuMin.coerceAtMost(cpuPct)
            cpuMax = cpuMax.coerceAtLeast(cpuPct)
            cpuSamples.add(cpuPct)
            cpuTimestamps.add(timestamp)
        }

        fun addMainThreadCpuSample(cpuPct: Double) {
            cpuMainThreadCount++
            cpuMainThreadSum += cpuPct
            cpuMainThreadMin = cpuMainThreadMin.coerceAtMost(cpuPct)
            cpuMainThreadMax = cpuMainThreadMax.coerceAtLeast(cpuPct)
            cpuMainThreadSamples.add(cpuPct)
        }

        fun addOverheadCpuSample(cpuPct: Double) {
            cpuOverheadCount++
            cpuOverheadSum += cpuPct
            cpuOverheadMin = cpuOverheadMin.coerceAtMost(cpuPct)
            cpuOverheadMax = cpuOverheadMax.coerceAtLeast(cpuPct)
            cpuOverheadSamples.add(cpuPct)
        }

        fun addRuntimeMemorySample(
            used: Long,
            timestamp: Long,
        ) {
            runtimeCount++
            runtimeSum += used
            runtimeMin = runtimeMin.coerceAtMost(used)
            runtimeMax = runtimeMax.coerceAtLeast(used)
            runtimeSamples.add(used)
            runtimeTimestamps.add(timestamp)
        }

        fun addDeviceMemorySample(
            pss: Long,
            timestamp: Long,
        ) {
            deviceCount++
            deviceSum += pss
            deviceMin = deviceMin.coerceAtMost(pss)
            deviceMax = deviceMax.coerceAtLeast(pss)
            deviceSamples.add(pss)
            deviceTimestamps.add(timestamp)
        }

        fun applyTo(builder: AppSessionMetricsBuilder) {
            if (cpuCount > 0) {
                builder.cpuCount = cpuCount
                builder.cpuMin = cpuMin
                builder.cpuMax = cpuMax
                builder.cpuMean = (cpuSum / cpuCount).coerceIn(cpuMin, cpuMax)
                builder.cpuSamples = cpuSamples.toDoubleArray()
                builder.cpuTimestamps = cpuTimestamps.toLongArray()
            }
            if (cpuMainThreadCount > 0) {
                builder.cpuMainThreadMin = cpuMainThreadMin
                builder.cpuMainThreadMax = cpuMainThreadMax
                val mtMean =
                    (cpuMainThreadSum / cpuMainThreadCount)
                        .coerceIn(cpuMainThreadMin, cpuMainThreadMax)
                builder.cpuMainThreadMean = mtMean
                builder.cpuMainThreadSamples = cpuMainThreadSamples.toDoubleArray()
            }
            if (cpuOverheadCount > 0) {
                builder.cpuOverheadMin = cpuOverheadMin
                builder.cpuOverheadMax = cpuOverheadMax
                val ohMean =
                    (cpuOverheadSum / cpuOverheadCount)
                        .coerceIn(cpuOverheadMin, cpuOverheadMax)
                builder.cpuOverheadMean = ohMean
                builder.cpuOverheadSamples = cpuOverheadSamples.toDoubleArray()
            }
            applyMemoryTo(builder)
        }

        private fun applyMemoryTo(builder: AppSessionMetricsBuilder) {
            if (runtimeCount > 0) {
                builder.runtimeCount = runtimeCount
                builder.runtimeMin = runtimeMin
                builder.runtimeMax = runtimeMax
                val rtMean = (runtimeSum / runtimeCount).coerceIn(runtimeMin, runtimeMax)
                builder.runtimeMean = rtMean
                builder.runtimeSamples = runtimeSamples.toLongArray()
                builder.runtimeTimestamps = runtimeTimestamps.toLongArray()
            }
            if (deviceCount > 0) {
                builder.deviceCount = deviceCount
                builder.deviceMin = deviceMin
                builder.deviceMax = deviceMax
                val dvMean = (deviceSum / deviceCount).coerceIn(deviceMin, deviceMax)
                builder.deviceMean = dvMean
                builder.deviceSamples = deviceSamples.toLongArray()
                builder.deviceTimestamps = deviceTimestamps.toLongArray()
            }
        }

        private fun Double.coerceIn(
            min: Double,
            max: Double,
        ): Double {
            return when {
                this.isNaN() -> min
                this < min -> min
                this > max -> max
                else -> this
            }
        }
    }

    private class AppSessionMetricsBuilder {
        var cpuCount = 0
        var cpuMin = 0.0
        var cpuMax = 0.0
        var cpuMean = 0.0
        var cpuSamples = DoubleArray(0)
        var cpuTimestamps = LongArray(0)

        var cpuMainThreadMin = 0.0
        var cpuMainThreadMax = 0.0
        var cpuMainThreadMean = 0.0
        var cpuMainThreadSamples = DoubleArray(0)

        var cpuOverheadMin = 0.0
        var cpuOverheadMax = 0.0
        var cpuOverheadMean = 0.0
        var cpuOverheadSamples = DoubleArray(0)

        var runtimeCount = 0
        var runtimeMin = 0L
        var runtimeMax = 0L
        var runtimeMean = 0L
        var runtimeSamples = LongArray(0)
        var runtimeTimestamps = LongArray(0)

        var deviceCount = 0
        var deviceMin = 0L
        var deviceMax = 0L
        var deviceMean = 0L
        var deviceSamples = LongArray(0)
        var deviceTimestamps = LongArray(0)
        var physicalDeviceMemory = 0L

        fun build() =
            AppSessionMetrics(
                cpuCount = cpuCount,
                cpuMin = cpuMin,
                cpuMax = cpuMax,
                cpuMean = cpuMean,
                cpuSamples = cpuSamples,
                cpuMainThreadSamples = cpuMainThreadSamples,
                cpuOverheadSamples = cpuOverheadSamples,
                cpuMainThreadMin = cpuMainThreadMin,
                cpuMainThreadMax = cpuMainThreadMax,
                cpuMainThreadMean = cpuMainThreadMean,
                cpuOverheadMin = cpuOverheadMin,
                cpuOverheadMax = cpuOverheadMax,
                cpuOverheadMean = cpuOverheadMean,
                cpuTimestamps = cpuTimestamps,
                runtimeMemoryCount = runtimeCount,
                runtimeMemoryMinBytes = runtimeMin,
                runtimeMemoryMaxBytes = runtimeMax,
                runtimeMemoryMeanBytes = runtimeMean,
                runtimeMemorySamplesBytes = runtimeSamples,
                runtimeMemoryTimestamps = runtimeTimestamps,
                deviceMemoryCount = deviceCount,
                deviceMemoryMinBytes = deviceMin,
                deviceMemoryMaxBytes = deviceMax,
                deviceMemoryMeanBytes = deviceMean,
                deviceMemorySamplesBytes = deviceSamples,
                deviceMemoryTimestamps = deviceTimestamps,
                deviceMemorySizeBytes = physicalDeviceMemory,
            )
    }

    companion object {
        private const val DEFAULT_INTERVAL_MS = 1_000L
        private const val KILOBYTE = 1024L
        private const val PERCENT_100 = 100.0
        private const val MS_PER_SECOND = 1000.0

        /**
         * Single daemon thread shared across all active session segment collectors.
         * Low-priority so it doesn't interfere with app work.
         */
        private val sharedScheduler: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "bugsnag-session-metrics").apply {
                    isDaemon = true
                    priority = Thread.MIN_PRIORITY
                }
            }
    }
}
