package com.bugsnag.android.performance.internal.metrics

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.util.FixedRingBuffer

internal class CpuMetricsSource(
    samplingDelayMs: Long,
    maxSampleCount: Int = DEFAULT_SAMPLE_COUNT,
) : AbstractSampledMetricsSource<CpuMetricsSnapshot>(samplingDelayMs), Runnable {

    private val buffer = FixedRingBuffer(maxSampleCount) { CpuSampleData() }

    private val processSampler = CpuMetricsSampler(Process.myPid())
    private var mainThreadStatReader: CpuMetricsSampler? = null
    private var samplerThreadStatReader: CpuMetricsSampler? = null

    init {
        Loopers.onMainThread {
            initMainThreadSampling(Process.myTid())
        }
    }

    override fun run() {
        if (samplerThreadStatReader == null) {
            samplerThreadStatReader = CpuMetricsSampler(Process.myPid(), Process.myTid())
        }

        super.run()
    }

    override fun createStartMetrics(): CpuMetricsSnapshot {
        return CpuMetricsSnapshot(buffer.currentIndex)
    }

    override fun captureSample() {
        val timestamp = BugsnagClock.currentUnixNanoTime()
        buffer.put { sample ->
            sample.processCpuPct = processSampler.sampleCpuUse()
            sample.overheadCpuPct = samplerThreadStatReader?.sampleCpuUse() ?: -1.0
            sample.mainCpuPct = mainThreadStatReader?.sampleCpuUse() ?: -1.0
            sample.timestamp = timestamp
        }
    }

    override fun populatedWaitingTargets() {
        val endIndex = buffer.currentIndex
        takeSnapshotsAwaitingSample().forEach { snapshot ->
            val target = snapshot.target
            if (target != null) {
                val from = snapshot.bufferIndex
                val to = endIndex

                val sampleCount = buffer.countItemsBetween(from, to)
                val processCpuSamples = DoubleArray(sampleCount)
                val mainThreadCpuSamples = DoubleArray(sampleCount)
                val overheadCpuSamples = DoubleArray(sampleCount)
                val cpuTimestamps = LongArray(sampleCount)

                var cpuUseTotal = 0.0
                var cpuUseSampleCount = 0
                var mainThreadCpuTotal = 0.0
                var mainThreadSampleCount = 0
                var overheadCpuTotal = 0.0
                var overheadSampleCount = 0

                buffer.forEachIndexed(from, to) { index, sample ->
                    processCpuSamples[index] = sample.processCpuPct
                    mainThreadCpuSamples[index] = sample.mainCpuPct
                    overheadCpuSamples[index] = sample.overheadCpuPct
                    cpuTimestamps[index] = sample.timestamp

                    if (sample.processCpuPct != -1.0) {
                        cpuUseTotal += sample.processCpuPct
                        cpuUseSampleCount++
                    }

                    if (sample.mainCpuPct != -1.0) {
                        mainThreadCpuTotal += sample.mainCpuPct
                        mainThreadSampleCount++
                    }

                    if (sample.overheadCpuPct != -1.0) {
                        overheadCpuTotal += sample.overheadCpuPct
                        overheadSampleCount++
                    }
                }

                target.attributes["bugsnag.system.cpu_measures_total"] = processCpuSamples
                target.attributes["bugsnag.system.cpu_measures_main_thread"] = mainThreadCpuSamples
                target.attributes["bugsnag.system.cpu_measures_overhead"] = overheadCpuSamples
                target.attributes["bugsnag.system.cpu_measures_timestamps"] = cpuTimestamps

                if (cpuUseSampleCount > 0) {
                    target.attributes["bugsnag.metrics.cpu_mean_total"] =
                        cpuUseTotal / cpuUseSampleCount
                }

                if (mainThreadSampleCount > 0) {
                    target.attributes["bugsnag.metrics.cpu_mean_main_thread"] =
                        mainThreadCpuTotal / mainThreadSampleCount
                }

                if (overheadSampleCount > 0) {
                    target.attributes["bugsnag.system.cpu_mean_overhead"] =
                        overheadCpuTotal / overheadSampleCount
                }

                snapshot.blocking?.cancel()
            }
        }
    }

    private fun initMainThreadSampling(mainThreadTid: Int) {
        this.mainThreadStatReader = CpuMetricsSampler(Process.myPid(), mainThreadTid)
    }

    private class CpuSampleData(
        @JvmField
        var processCpuPct: Double = 0.0,

        @JvmField
        var mainCpuPct: Double = 0.0,

        @JvmField
        var overheadCpuPct: Double = 0.0,

        @JvmField
        var timestamp: Long = 0L,
    )

    private companion object {
        /**
         * Default number of CPU samples that can be stored at once (10 minutes, with 1
         * sample each second).
         */
        const val DEFAULT_SAMPLE_COUNT = 60 * 10
    }
}

internal data class CpuMetricsSnapshot(
    @JvmField
    internal val bufferIndex: Int,
) : LinkedMetricsSnapshot<CpuMetricsSnapshot>()

private class CpuMetricsSampler(statFile: String) {
    constructor(pid: Int) : this("/proc/$pid/stat")
    constructor(pid: Int, tid: Int) : this("/proc/$pid/task/$tid/stat")

    private val statReader = ProcStatReader(statFile)

    private val stat = ProcStatReader.Stat()

    private var previousUptime = 0.0
    private var previousCpuTime = 0.0

    fun sampleCpuUse(): Double {
        // get the latest sample data
        if (!statReader.parse(stat)) {
            return -1.0
        }

        val uptimeSec = SystemClock.elapsedRealtime() / 1000.0

        val totalCpuTime = stat.totalCpuTime / SystemConfig.clockTickHz
        // calculate the avg cpu % between the previous sample and now
        val deltaCpuTime = totalCpuTime - previousCpuTime
        val deltaUptime = uptimeSec - previousUptime

        previousCpuTime = totalCpuTime
        previousUptime = uptimeSec

        val cpuUsagePercent = 100.0 * (deltaCpuTime / deltaUptime)
        return cpuUsagePercent / SystemConfig.numCores
    }
}

internal object SystemConfig {
    private var _clockTickHz: Double = 100.0

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            _clockTickHz = Os.sysconf(OsConstants._SC_CLK_TCK).toDouble()
        }
    }

    fun configure() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        try {
            val getconfOutput = Runtime.getRuntime().exec("getconf CLK_TCK")
                .apply { waitFor() }
                .inputStream
                .bufferedReader()
                .readText()

            _clockTickHz = getconfOutput.toDouble()
        } catch (ex: Exception) {
            // ignore and leave the value as its default
        }
    }

    /**
     * The number of scheduler slices per second. Contrary to SC_CLK_TCK naming, this is not the
     * CPU clock speed (https://www.softprayog.in/tutorials/linux-process-execution-time).
     */
    val clockTickHz: Double get() = _clockTickHz

    val numCores: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Os.sysconf(OsConstants._SC_NPROCESSORS_CONF).toInt()
        } else {
            Runtime.getRuntime().availableProcessors()
        }
}
