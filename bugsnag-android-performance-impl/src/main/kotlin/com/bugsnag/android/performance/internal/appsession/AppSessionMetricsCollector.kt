package com.bugsnag.android.performance.internal.appsession

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.AndroidException
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
    private var cpuCount = 0
    private var cpuSum = 0.0
    private var cpuMin = Double.MAX_VALUE
    private var cpuMax = Double.MIN_VALUE
    private val cpuSamples = mutableListOf<Double>()
    private var cpuMainThreadMin = Double.MAX_VALUE
    private var cpuMainThreadMax = Double.MIN_VALUE
    private var cpuMainThreadSum = 0.0
    private var cpuMainThreadCount = 0
    private val cpuMainThreadSamples = mutableListOf<Double>()
    private var cpuOverheadMin = Double.MAX_VALUE
    private var cpuOverheadMax = Double.MIN_VALUE
    private var cpuOverheadSum = 0.0
    private var cpuOverheadCount = 0
    private val cpuOverheadSamples = mutableListOf<Double>()
    private val cpuTimestamps = mutableListOf<Long>()

    private var runtimeCount = 0
    private var runtimeSum = 0L
    private var runtimeMin = Long.MAX_VALUE
    private var runtimeMax = Long.MIN_VALUE
    private val runtimeSamples = mutableListOf<Long>()
    private val runtimeTimestamps = mutableListOf<Long>()

    private var deviceCount = 0
    private var deviceSum = 0L
    private var deviceMin = Long.MAX_VALUE
    private var deviceMax = Long.MIN_VALUE
    private val deviceSamples = mutableListOf<Long>()
    private val deviceTimestamps = mutableListOf<Long>()

    // ── ActivityManager for PSS ───────────────────────────────────────────────
    private val activityManager: ActivityManager? = appContext.getActivityManager()
    private val physicalDeviceMemory = calculateTotalMemory()

    // ── Scheduler ────────────────────────────────────────────────────────────
    private var future: ScheduledFuture<*>? = null

    /** Call from the thread that starts the session segment span. */
    fun start() {
        Loopers.onMainThread {
            mainThreadTid[0] = Process.myTid()
        }
        resetAccumulators()
        // Prime the CPU sampler so the first delta is meaningful
        primeCpuSampler()
        future = sharedScheduler.scheduleWithFixedDelay(
            ::takeSample,
            samplingIntervalMs,
            samplingIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    /**
     * Stops sampling and returns the aggregated metrics.
     * Safe to call even if [start] was never called – returns [AppSessionMetrics.EMPTY].
     */
    fun stop(): AppSessionMetrics {
        future?.cancel(false)
        future = null

        // Take one final synchronous sample so the tail of the span is captured
        takeSample()

        return buildMetrics()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal
    // ─────────────────────────────────────────────────────────────────────────

    private fun resetAccumulators() {
        cpuCount = 0; cpuSum = 0.0
        cpuMin = Double.MAX_VALUE; cpuMax = Double.MIN_VALUE

        runtimeCount = 0; runtimeSum = 0L
        runtimeMin = Long.MAX_VALUE; runtimeMax = Long.MIN_VALUE
        runtimeSamples.clear(); runtimeTimestamps.clear()

        deviceCount = 0; deviceSum = 0L
        deviceMin = Long.MAX_VALUE; deviceMax = Long.MIN_VALUE
        deviceSamples.clear(); deviceTimestamps.clear()

        cpuSamples.clear(); cpuTimestamps.clear()
        cpuMainThreadMin = Double.MAX_VALUE; cpuMainThreadMax = Double.MIN_VALUE
        cpuMainThreadSum = 0.0; cpuMainThreadCount = 0; cpuMainThreadSamples.clear()
        cpuOverheadMin = Double.MAX_VALUE; cpuOverheadMax = Double.MIN_VALUE
        cpuOverheadSum = 0.0; cpuOverheadCount = 0; cpuOverheadSamples.clear()
        overheadStatReader = null
    }

    private fun primeCpuSampler() {
        // Read once to establish a baseline so the first real sample has a valid delta
        if (procStatReader.parse(procStat)) {
            previousCpuTime = procStat.totalCpuTime / SystemConfig.clockTickHz
            previousUptime = SystemClock.elapsedRealtime() / 1000.0

            val tid = mainThreadTid[0]
            if (tid > 0) {
                mainThreadStatReader = ProcStatReader("/proc/${Process.myPid()}/task/$tid/stat")
                if (mainThreadStatReader?.parse(mainThreadStat) == true) {
                    previousMainThreadCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
                }
            }
        }
    }

    private fun takeSample() {
        val timestamp = BugsnagClock.currentUnixNanoTime()
        sampleCpu(timestamp)
        sampleRuntimeMemory(timestamp)
        sampleDeviceMemory(timestamp)
    }

    // ── CPU sample ────────────────────────────────────────────────────────────

    private fun sampleCpu(timestamp: Long) {
        if (!procStatReader.parse(procStat)) return

        val uptimeSec = SystemClock.elapsedRealtime() / 1000.0
        val totalCpuTime = procStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousCpuTime
        val deltaUptime = uptimeSec - previousUptime

        previousCpuTime = totalCpuTime
        previousUptime = uptimeSec

        if (deltaUptime <= 0.0) return

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuCount++
        cpuSum += cpuPct
        if (cpuPct < cpuMin) cpuMin = cpuPct
        if (cpuPct > cpuMax) cpuMax = cpuPct
        cpuSamples.add(cpuPct)
        cpuTimestamps.add(timestamp)

        sampleMainThreadCpu(deltaUptime)
        sampleOverheadCpu(deltaUptime)
    }

    private fun sampleMainThreadCpu(deltaUptime: Double) {
        val reader = mainThreadStatReader ?: return
        if (!reader.parse(mainThreadStat)) return

        val totalCpuTime = mainThreadStat.totalCpuTime / SystemConfig.clockTickHz
        val deltaCpu = totalCpuTime - previousMainThreadCpuTime
        previousMainThreadCpuTime = totalCpuTime
        if (deltaUptime <= 0.0) return

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuMainThreadCount++
        cpuMainThreadSum += cpuPct
        if (cpuPct < cpuMainThreadMin) cpuMainThreadMin = cpuPct
        if (cpuPct > cpuMainThreadMax) cpuMainThreadMax = cpuPct
        cpuMainThreadSamples.add(cpuPct)
    }

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
        if (deltaUptime <= 0.0) return

        val cpuPct = (100.0 * deltaCpu / deltaUptime) / SystemConfig.numCores
        if (!cpuPct.isFinite() || cpuPct < 0.0) return

        cpuOverheadCount++
        cpuOverheadSum += cpuPct
        if (cpuPct < cpuOverheadMin) cpuOverheadMin = cpuPct
        if (cpuPct > cpuOverheadMax) cpuOverheadMax = cpuPct
        cpuOverheadSamples.add(cpuPct)
    }

    // ── Runtime (ART heap) memory sample ──────────────────────────────────────

    private fun sampleRuntimeMemory(timestamp: Long) {
        val rt = Runtime.getRuntime()
        val used = rt.totalMemory() - rt.freeMemory()
        if (used <= 0L) return

        runtimeCount++
        runtimeSum += used
        if (used < runtimeMin) runtimeMin = used
        if (used > runtimeMax) runtimeMax = used
        runtimeSamples.add(used)
        runtimeTimestamps.add(timestamp)
    }

    // ── Device memory (PSS) sample ────────────────────────────────────────────

    private fun sampleDeviceMemory(timestamp: Long) {
        val memInfo = activityManager
            ?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            ?.getOrNull(0)
            ?: return

        val pssBytes =
            (memInfo.dalvikPss + memInfo.nativePss + memInfo.otherPss) * KILOBYTE

        if (pssBytes <= 0L) return

        deviceCount++
        deviceSum += pssBytes
        if (pssBytes < deviceMin) deviceMin = pssBytes
        if (pssBytes > deviceMax) deviceMax = pssBytes
        deviceSamples.add(pssBytes)
        deviceTimestamps.add(timestamp)
    }

    // ── Build result ──────────────────────────────────────────────────────────

    private fun buildMetrics(): AppSessionMetrics {
        if (cpuCount == 0 && runtimeCount == 0 && deviceCount == 0) {
            return AppSessionMetrics.EMPTY
        }

        return AppSessionMetrics(
            // CPU
            cpuCount = cpuCount,
            cpuMin = if (cpuCount > 0) cpuMin else 0.0,
            cpuMax = if (cpuCount > 0) cpuMax else 0.0,
            cpuMean = if (cpuCount > 0) cpuSum / cpuCount else 0.0,
            cpuSamples = cpuSamples.toDoubleArray(),
            cpuMainThreadSamples = cpuMainThreadSamples.toDoubleArray(),
            cpuOverheadSamples = cpuOverheadSamples.toDoubleArray(),
            cpuMainThreadMin = if (cpuMainThreadCount > 0) cpuMainThreadMin else 0.0,
            cpuMainThreadMax = if (cpuMainThreadCount > 0) cpuMainThreadMax else 0.0,
            cpuMainThreadMean =
                if (cpuMainThreadCount > 0) cpuMainThreadSum / cpuMainThreadCount else 0.0,
            cpuOverheadMin = if (cpuOverheadCount > 0) cpuOverheadMin else 0.0,
            cpuOverheadMax = if (cpuOverheadCount > 0) cpuOverheadMax else 0.0,
            cpuOverheadMean = if (cpuOverheadCount > 0) cpuOverheadSum / cpuOverheadCount else 0.0,
            cpuTimestamps = cpuTimestamps.toLongArray(),

            // Runtime memory
            runtimeMemoryCount = runtimeCount,
            runtimeMemoryMinBytes = if (runtimeCount > 0) runtimeMin else 0L,
            runtimeMemoryMaxBytes = if (runtimeCount > 0) runtimeMax else 0L,
            runtimeMemoryMeanBytes = if (runtimeCount > 0) runtimeSum / runtimeCount else 0L,
            runtimeMemorySamplesBytes = runtimeSamples.toLongArray(),
            runtimeMemoryTimestamps = runtimeTimestamps.toLongArray(),

            // Device memory (PSS)
            deviceMemoryCount = deviceCount,
            deviceMemoryMinBytes = if (deviceCount > 0) deviceMin else 0L,
            deviceMemoryMaxBytes = if (deviceCount > 0) deviceMax else 0L,
            deviceMemoryMeanBytes = if (deviceCount > 0) deviceSum / deviceCount else 0L,
            deviceMemorySamplesBytes = deviceSamples.toLongArray(),
            deviceMemoryTimestamps = deviceTimestamps.toLongArray(),
            deviceMemorySizeBytes = physicalDeviceMemory ?: 0L,
        )
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

