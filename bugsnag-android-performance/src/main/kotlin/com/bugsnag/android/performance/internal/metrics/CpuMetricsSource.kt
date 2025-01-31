package com.bugsnag.android.performance.internal.metrics

import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.Loopers
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.Task
import com.bugsnag.android.performance.internal.util.FixedRingBuffer
import java.util.concurrent.atomic.AtomicReference

internal class CpuMetricsSource(
    maxSampleCount: Int = DEFAULT_SAMPLE_COUNT,
) : MetricSource<CpuMetricsSnapshot>, Runnable {

    private val buffer = FixedRingBuffer(maxSampleCount) { CpuSampleData() }

    private val processSampler = CpuMetricsSampler("/proc/${Process.myPid()}/stat")
    private var mainThreadStatReader: CpuMetricsSampler? = null

    /**
     * Chain/linked-list of CpuSnapshots awaiting the "next" sample, stored as an AtomicReference
     * in the same way as we deal with [SpanImpl] batching (see [BatchingSpanProcessor]) giving
     * us a lightweight way to collect spans that are waiting for a sample.
     */
    private val awaitingNextSample = AtomicReference<CpuMetricsSnapshot?>(null)

    init {
        Loopers.onMainThread {
            initMainThreadSampling(Process.myTid())
        }
    }

    override fun createStartMetrics(): CpuMetricsSnapshot {
        return CpuMetricsSnapshot(buffer.currentIndex)
    }

    override fun endMetrics(startMetrics: CpuMetricsSnapshot, span: Span) {
        startMetrics.target = span
        startMetrics.blocking = (span as SpanImpl).block(TWO_SECONDS)

        while (true) {
            val next = awaitingNextSample.get()
            startMetrics.next = next

            if (awaitingNextSample.compareAndSet(next, startMetrics)) {
                break
            }
        }
    }

    override fun run() {
        captureCpuSamples()
        populatedWaitingTargets()
    }

    private fun captureCpuSamples() {
        val timestamp = BugsnagClock.currentUnixNanoTime()
        buffer.put { sample ->
            sample.processCpuPct = processSampler.sampleCpuUse()
            sample.mainCpuPct = mainThreadStatReader?.sampleCpuUse() ?: -1.0
            sample.timestamp = timestamp
        }
    }

    private fun populatedWaitingTargets() {
        val endIndex = buffer.currentIndex
        var nextSnapshot = awaitingNextSample.getAndSet(null)

        while (nextSnapshot != null) {
            val target = nextSnapshot.target

            if (target != null) {
                val from = nextSnapshot.bufferIndex
                val to = endIndex

                val sampleCount = buffer.countItemsBetween(from, to)
                val processCpuSamples = DoubleArray(sampleCount)
                val mainThreadCpuSamples = DoubleArray(sampleCount)
                val cpuTimestamps = LongArray(sampleCount)
                var cpuUseTotal = 0.0
                var cpuUseSampleCount = 0
                var mainThreadCpuTotal = 0.0
                var mainThreadSampleCount = 0

                buffer.forEachIndexed(from, to) { index, sample ->
                    processCpuSamples[index] = sample.processCpuPct
                    mainThreadCpuSamples[index] = sample.mainCpuPct
                    cpuTimestamps[index] = sample.timestamp

                    if (sample.processCpuPct != -1.0) {
                        cpuUseTotal += sample.processCpuPct
                        cpuUseSampleCount++
                    }

                    if (sample.mainCpuPct != -1.0) {
                        mainThreadCpuTotal += sample.mainCpuPct
                        mainThreadSampleCount++
                    }
                }

                target.setAttribute("bugsnag.system.cpu_measures_total", processCpuSamples)
                target.setAttribute("bugsnag.system.cpu_measures_main_thread", mainThreadCpuSamples)
                target.setAttribute("bugsnag.system.cpu_measures_timestamps", cpuTimestamps)

                target.setAttribute(
                    "bugsnag.metrics.cpu_mean_total",
                    cpuUseTotal / cpuUseSampleCount
                )

                target.setAttribute(
                    "bugsnag.metrics.cpu_mean_main_thread",
                    mainThreadCpuTotal / mainThreadSampleCount
                )

                nextSnapshot.blocking?.cancel()
            }

            nextSnapshot = nextSnapshot.next
        }
    }

    private fun initMainThreadSampling(mainThreadTid: Int) {
        this.mainThreadStatReader = CpuMetricsSampler(
            "/proc/${Process.myPid()}/task/$mainThreadTid/stat",
        )
    }

    private class CpuSampleData(
        @JvmField
        var processCpuPct: Double = 0.0,

        @JvmField
        var mainCpuPct: Double = 0.0,

        @JvmField
        var timestamp: Long = 0L,
    )

    private companion object {
        /**
         * Default number of CPU samples that can be stored at once (10 minutes, with 1
         * sample each second).
         */
        const val DEFAULT_SAMPLE_COUNT = 60 * 10

        const val TWO_SECONDS = 2000L
    }
}

internal data class CpuMetricsSnapshot(
    @JvmField
    internal val bufferIndex: Int,
) {
    @JvmField
    internal var next: CpuMetricsSnapshot? = null

    @JvmField
    internal var blocking: SpanImpl.Condition? = null

    @JvmField
    internal var target: HasAttributes? = null
}

private class CpuMetricsSampler(
    statFile: String,
) {
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
