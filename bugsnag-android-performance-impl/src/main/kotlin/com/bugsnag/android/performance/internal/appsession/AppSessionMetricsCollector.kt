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
import com.bugsnag.android.performance.internal.metrics.SystemConfig
import com.bugsnag.android.performance.internal.metrics.ProcStatReader
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
    @Volatile private var cpuCount = 0
    @Volatile private var cpuSum = 0.0
    @Volatile private var cpuMin = Double.MAX_VALUE
    @Volatile private var cpuMax = 0.0
    private val cpuSamples = mutableListOf<Double>()
    @Volatile private var cpuMainThreadMin = Double.MAX_VALUE
    @Volatile private var cpuMainThreadMax = 0.0
    @Volatile private var cpuMainThreadSum = 0.0
    @Volatile private var cpuMainThreadCount = 0
    private val cpuMainThreadSamples = mutableListOf<Double>()
    @Volatile private var cpuOverheadMin = Double.MAX_VALUE
    @Volatile private var cpuOverheadMax = 0.0
    @Volatile private var cpuOverheadSum = 0.0
    @Volatile private var cpuOverheadCount = 0
    private val cpuOverheadSamples = mutableListOf<Double>()
    private val cpuTimestamps = mutableListOf<Long>()

    @Volatile private var runtimeCount = 0
    @Volatile private var runtimeSum = 0L
    @Volatile private var runtimeMin = Long.MAX_VALUE
    @Volatile private var runtimeMax = Long.MIN_VALUE
    private val runtimeSamples = mutableListOf<Long>()
    private val runtimeTimestamps = mutableListOf<Long>()

    @Volatile private var deviceCount = 0
    @Volatile private var deviceSum = 0L
    @Volatile private var deviceMin = Long.MAX_VALUE
    @Volatile private var deviceMax = Long.MIN_VALUE
    private val deviceSamples = mutableListOf<Long>()
    private val deviceTimestamps = mutableListOf<Long>()

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
        resetAccumulators()
        // Prime the CPU samplers so the first delta is meaningful
        primeCpuSampler()
        
        future = sharedScheduler.scheduleWithFixedDelay(
            { takeSample() },
            0, // Start immediately to prime overhead sampler on the background thread
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

    private fun resetAccumulators() {
        cpuCount = 0; cpuSum = 0.0
        cpuMin = Double.MAX_VALUE; cpuMax = 0.0

        runtimeCount = 0; runtimeSum = 0L
        runtimeMin = Long.MAX_VALUE; runtimeMax = Long.MIN_VALUE
        runtimeSamples.clear(); runtimeTimestamps.clear()

        deviceCount = 0; deviceSum = 0L
        deviceMin = Long.MAX_VALUE; deviceMax = Long.MIN_VALUE
        deviceSamples.clear(); deviceTimestamps.clear()

        cpuSamples.clear(); cpuTimestamps.clear()
        cpuMainThreadMin = Double.MAX_VALUE; cpuMainThreadMax = 0.0
        cpuMainThreadSum = 0.0; cpuMainThreadCount = 0; cpuMainThreadSamples.clear()
        cpuOverheadMin = Double.MAX_VALUE; cpuOverheadMax = 0.0
        cpuOverheadSum = 0.0; cpuOverheadCount = 0; cpuOverheadSamples.clear()
        overheadStatReader = null
    }

    @Synchronized
    private fun primeCpuSampler() {
        if (!enabledMetrics.cpu) return
        
        previousUptime = SystemClock.elapsedRealtime() / 1000.0
        
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

        val uptimeSec = SystemClock.elapsedRealtime() / 1000.0
        val totalCpuTime = procStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousCpuTime
        val deltaUptime = uptimeSec - previousUptime

        if (deltaUptime <= 0.0) return

        // Update baselines for the NEXT sample
        previousCpuTime = totalCpuTime
        previousUptime = uptimeSec

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuCount++
        cpuSum += cpuPct
        cpuMin = cpuMin.coerceAtMost(cpuPct)
        cpuMax = cpuMax.coerceAtLeast(cpuPct)
        cpuSamples.add(cpuPct)
        cpuTimestamps.add(timestamp)

        sampleMainThreadCpu(deltaUptime)
        sampleOverheadCpu(deltaUptime)
    }

    @Synchronized
    private fun sampleMainThreadCpu(deltaUptime: Double) {
        if (mainThreadStatReader == null) {
            val tid = mainThreadTid[0]
            if (tid > 0) {
                mainThreadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
                if (mainThreadStatReader?.parse(mainThreadStat) == true) {
                    previousMainThreadCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
                }
            }
            return
        }
        if (!mainThreadStatReader!!.parse(mainThreadStat)) return

        val totalCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousMainThreadCpuTime
        previousMainThreadCpuTime = totalCpuTime

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuMainThreadCount++
        cpuMainThreadSum += cpuPct
        cpuMainThreadMin = cpuMainThreadMin.coerceAtMost(cpuPct)
        cpuMainThreadMax = cpuMainThreadMax.coerceAtLeast(cpuPct)
        cpuMainThreadSamples.add(cpuPct)
    }

    @Synchronized
    private fun sampleOverheadCpu(deltaUptime: Double) {
        if (overheadStatReader == null) {
            val tid = Process.myTid()
            overheadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
            if (overheadStatReader?.parse(overheadStat) == true) {
                previousOverheadCpuTime = overheadStat.totalCpuTime / SystemConfig.clockTickHz
            }
            return
        }

        if (overheadStatReader?.parse(overheadStat) != true) return
        val totalCpuTime = overheadStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousOverheadCpuTime
        previousOverheadCpuTime = totalCpuTime

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuOverheadCount++
        cpuOverheadSum += cpuPct
        cpuOverheadMin = cpuOverheadMin.coerceAtMost(cpuPct)
        cpuOverheadMax = cpuOverheadMax.coerceAtLeast(cpuPct)
        cpuOverheadSamples.add(cpuPct)
    }

    // ── Runtime (ART heap) memory sample ──────────────────────────────────────

    @Synchronized
    private fun sampleRuntimeMemory(timestamp: Long) {
        val rt = Runtime.getRuntime()
        val total = rt.totalMemory()
        val free = rt.freeMemory()
        val used = total - free
        if (used <= 0L) return

        runtimeCount++
        runtimeSum += used
        runtimeMin = runtimeMin.coerceAtMost(used)
        runtimeMax = runtimeMax.coerceAtLeast(used)
        runtimeSamples.add(used)
        runtimeTimestamps.add(timestamp)
    }

    // ── Device memory (PSS) sample ────────────────────────────────────────────

    @Synchronized
    private fun sampleDeviceMemory(timestamp: Long) {
        val memInfo = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memInfo)

        val pssBytes =
            (memInfo.dalvikPss + memInfo.nativePss + memInfo.otherPss).toLong() * KILOBYTE

        if (pssBytes <= 0L) return

        deviceCount++
        deviceSum += pssBytes
        deviceMin = deviceMin.coerceAtMost(pssBytes)
        deviceMax = deviceMax.coerceAtLeast(pssBytes)
        deviceSamples.add(pssBytes)
        deviceTimestamps.add(timestamp)
    }

    // ── Build result ──────────────────────────────────────────────────────────

    @Synchronized
    private fun buildMetrics(): AppSessionMetrics {
        val cCount = cpuCount
        val rCount = runtimeCount
        val dCount = deviceCount

        val dMeanCoerced = if (dCount > 0) {
            (deviceSum.toDouble() / dCount).coerceIn(deviceMin.toDouble(), deviceMax.toDouble()).toLong()
        } else 0L
        val rMeanCoerced = if (rCount > 0) {
            (runtimeSum.toDouble() / rCount).coerceIn(runtimeMin.toDouble(), runtimeMax.toDouble()).toLong()
        } else 0L
        return AppSessionMetrics(
            // CPU
            cpuCount = cCount,
            cpuMin = if (cCount > 0) cpuMin else 0.0,
            cpuMax = if (cCount > 0) cpuMax else 0.0,
            cpuMean = if (cCount > 0) (cpuSum / cCount).coerceIn(cpuMin, cpuMax) else 0.0,
            cpuSamples = cpuSamples.toDoubleArray(),
            cpuMainThreadSamples = cpuMainThreadSamples.toDoubleArray(),
            cpuOverheadSamples = cpuOverheadSamples.toDoubleArray(),
            cpuMainThreadMin = if (cpuMainThreadCount > 0) cpuMainThreadMin else 0.0,
            cpuMainThreadMax = if (cpuMainThreadCount > 0) cpuMainThreadMax else 0.0,
            cpuMainThreadMean = if (cpuMainThreadCount > 0)
                (cpuMainThreadSum / cpuMainThreadCount).coerceIn(cpuMainThreadMin, cpuMainThreadMax)
            else 0.0,
            cpuOverheadMin = if (cpuOverheadCount > 0) cpuOverheadMin else 0.0,
            cpuOverheadMax = if (cpuOverheadCount > 0) cpuOverheadMax else 0.0,
            cpuOverheadMean = if (cpuOverheadCount > 0)
                (cpuOverheadSum / cpuOverheadCount).coerceIn(cpuOverheadMin, cpuOverheadMax)
            else 0.0,
            cpuTimestamps = cpuTimestamps.toLongArray(),
            // Runtime memory (ART) — use pre-computed coerced values
            runtimeMemoryCount = rCount,
            runtimeMemoryMinBytes = if (rCount > 0) runtimeMin else 0L,
            runtimeMemoryMaxBytes = if (rCount > 0) runtimeMax else 0L,
            runtimeMemoryMeanBytes = rMeanCoerced,
            runtimeMemorySamplesBytes = runtimeSamples.toLongArray(),
            runtimeMemoryTimestamps = runtimeTimestamps.toLongArray(),
            // Device memory (PSS) — use pre-computed coerced values
            deviceMemoryCount = dCount,
            deviceMemoryMinBytes = if (dCount > 0) deviceMin else 0L,
            deviceMemoryMaxBytes = if (dCount > 0) deviceMax else 0L,
            deviceMemoryMeanBytes = dMeanCoerced,
            deviceMemorySamplesBytes = deviceSamples.toLongArray(),
            deviceMemoryTimestamps = deviceTimestamps.toLongArray(),
            deviceMemorySizeBytes = if (enabledMetrics.memory) (physicalDeviceMemory ?: 0L) else 0L,
        )
    }
    private fun Double.coerceIn(min: Double, max: Double): Double {
        return if (this < min) min else if (this > max) max else this
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

    companion object {
        private const val DEFAULT_INTERVAL_MS = 1_000L
        private const val KILOBYTE = 1024L

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

