package com.bugsnag.android.performance.internal.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.AndroidException
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.getActivityManager
import com.bugsnag.android.performance.internal.util.FixedRingBuffer
import kotlin.math.max

internal class MemoryMetricsSource(
    private val appContext: Context,
    samplingDelayMs: Long,
    maxSampleCount: Int = DEFAULT_SAMPLE_COUNT,
) : AbstractSampledMetricsSource<MemoryMetricsSnapshot>(samplingDelayMs) {
    private val buffer = FixedRingBuffer(maxSampleCount) { MemorySampleData() }

    private val runtime = Runtime.getRuntime()
    private val activityManager = appContext.getActivityManager()

    private val deviceMemory by lazy {
        calculateTotalMemory()
    }

    override fun captureSample() {
        val timestamp = BugsnagClock.currentUnixNanoTime()
        buffer.put { sample ->
            sample.freeMemory = runtime.freeMemory()
            sample.totalMemory = runtime.totalMemory()
            sample.timestamp = timestamp

            val memoryInfo = activityManager?.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            val memoryInfoSample = memoryInfo?.getOrNull(0)?.takeIf { memoryInfo.size == 1 }
            if (memoryInfoSample != null) {
                val pssPages =
                    memoryInfoSample.dalvikPss +
                        memoryInfoSample.nativePss +
                        memoryInfoSample.otherPss

                sample.pss = pssPages * KILOBYTE
            } else {
                sample.pss = -1
            }
        }
    }

    override fun populatedWaitingTargets() {
        val endIndex = buffer.currentIndex
        takeSnapshotsAwaitingSample().forEach { snapshot ->
            populateWaitingSnapshot(snapshot, endIndex)
        }
    }

    private fun populateWaitingSnapshot(
        snapshot: MemoryMetricsSnapshot,
        endIndex: Int,
    ) {
        val target = snapshot.target ?: return

        val from = snapshot.bufferIndex
        val to = endIndex
        val sampleCount = buffer.countItemsBetween(from, to)
        val metrics = collectMemoryMetrics(from, to, sampleCount)

        applyMemoryMetrics(target, metrics)
        snapshot.blocking?.cancel()
    }

    private fun collectMemoryMetrics(
        from: Int,
        to: Int,
        sampleCount: Int,
    ): MemoryMetrics {
        val deviceMemorySamples = LongArray(sampleCount)
        var deviceUsedMemoryTotal = 0L
        var deviceUsedMemoryCount = 0
        var deviceMinMemory = Long.MAX_VALUE
        var deviceMaxMemory = Long.MIN_VALUE

        val artMemoryTimestamps = LongArray(sampleCount)
        val artUsedMemorySamples = LongArray(sampleCount)
        var artSizeMemory = 0L
        var artUsedMemoryTotal = 0L
        var artUsedMemoryCount = 0
        var artMinMemory = Long.MAX_VALUE
        var artMaxMemory = Long.MIN_VALUE

        buffer.forEachIndexed(from, to) { index, sample ->
            artMemoryTimestamps[index] = sample.timestamp
            deviceMemorySamples[index] = sample.pss

            val artUsedMemory = sample.totalMemory - sample.freeMemory
            if (artUsedMemory > 0) {
                artUsedMemorySamples[index] = artUsedMemory
                artUsedMemoryTotal += artUsedMemory
                artUsedMemoryCount++
                artMinMemory = artMinMemory.coerceAtMost(artUsedMemory)
                artMaxMemory = artMaxMemory.coerceAtLeast(artUsedMemory)
            }
            if (sample.pss > 0) {
                deviceUsedMemoryTotal += sample.pss
                deviceUsedMemoryCount++
                deviceMinMemory = deviceMinMemory.coerceAtMost(sample.pss)
                deviceMaxMemory = deviceMaxMemory.coerceAtLeast(sample.pss)
            }
            if (sample.totalMemory > artSizeMemory) {
                artSizeMemory = sample.totalMemory
            }
        }

        return MemoryMetrics().apply {
            this.deviceMemorySamples = deviceMemorySamples
            this.deviceUsedMemoryTotal = deviceUsedMemoryTotal
            this.deviceUsedMemoryCount = deviceUsedMemoryCount
            this.deviceMinMemory = deviceMinMemory
            this.deviceMaxMemory = deviceMaxMemory
            this.artMemoryTimestamps = artMemoryTimestamps
            this.artUsedMemorySamples = artUsedMemorySamples
            this.artSizeMemory = artSizeMemory
            this.artUsedMemoryTotal = artUsedMemoryTotal
            this.artUsedMemoryCount = artUsedMemoryCount
            this.artMinMemory = artMinMemory
            this.artMaxMemory = artMaxMemory
        }
    }

    private fun applyMemoryMetrics(
        target: SpanImpl,
        metrics: MemoryMetrics,
    ) {
        deviceMemory?.also {
            target.attributes["bugsnag.device.physical_device_memory"] = it
            target.attributes["bugsnag.system.memory.spaces.device.size"] = it
        }
        target.attributes["bugsnag.system.memory.spaces.device.used"] = metrics.deviceMemorySamples
        if (metrics.deviceUsedMemoryCount > 0) {
            target.attributes["bugsnag.system.memory.spaces.device.mean"] =
                (metrics.deviceUsedMemoryTotal / metrics.deviceUsedMemoryCount)
                    .coerceIn(metrics.deviceMinMemory, metrics.deviceMaxMemory)

            if (target.category == SpanCategory.APP_SESSION) {
                target.attributes["bugsnag.system.memory.spaces.device.min"] = metrics.deviceMinMemory
                target.attributes["bugsnag.system.memory.spaces.device.max"] = metrics.deviceMaxMemory
            }
        }
        target.attributes["bugsnag.system.memory.spaces.art.size"] = metrics.artSizeMemory
        target.attributes["bugsnag.system.memory.spaces.art.used"] = metrics.artUsedMemorySamples
        if (metrics.artUsedMemoryCount > 0) {
            target.attributes["bugsnag.system.memory.spaces.art.mean"] =
                (metrics.artUsedMemoryTotal / metrics.artUsedMemoryCount)
                    .coerceIn(metrics.artMinMemory, metrics.artMaxMemory)

            if (target.category == SpanCategory.APP_SESSION) {
                target.attributes["bugsnag.system.memory.spaces.art.min"] = metrics.artMinMemory
                target.attributes["bugsnag.system.memory.spaces.art.max"] = metrics.artMaxMemory
            }
        }
        target.attributes["bugsnag.system.memory.timestamps"] = metrics.artMemoryTimestamps
    }

    override fun createStartMetrics(): MemoryMetricsSnapshot {
        return MemoryMetricsSnapshot(max(buffer.currentIndex - 1, 0))
    }

    private fun calculateTotalMemory(): Long? {
        val totalMemory =
            appContext.getActivityManager()
                ?.let { am -> ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) } }
                ?.totalMem
        if (totalMemory != null) {
            return totalMemory
        }

        // we try falling back to a reflective API
        return runCatching {
            @Suppress("PrivateApi")
            AndroidException::class.java.getDeclaredMethod("getTotalMemory").invoke(null) as Long?
        }.getOrNull()
    }

    override fun toString(): String {
        return "memoryMetrics"
    }

    private data class MemorySampleData(
        @JvmField
        var freeMemory: Long = 0L,
        @JvmField
        var totalMemory: Long = 0L,
        @JvmField
        var pss: Long = 0L,
        @JvmField
        var timestamp: Long = 0L,
    )

    private companion object {
        /**
         * Default number of memory samples that can be stored at once (10 minutes, with 1
         * sample each second).
         */
        const val DEFAULT_SAMPLE_COUNT = 60 * 10

        const val KILOBYTE = 1024L
    }

    private class MemoryMetrics {
        lateinit var deviceMemorySamples: LongArray
        var deviceUsedMemoryTotal: Long = 0L
        var deviceUsedMemoryCount: Int = 0
        var deviceMinMemory: Long = 0L
        var deviceMaxMemory: Long = 0L
        lateinit var artMemoryTimestamps: LongArray
        lateinit var artUsedMemorySamples: LongArray
        var artSizeMemory: Long = 0L
        var artUsedMemoryTotal: Long = 0L
        var artUsedMemoryCount: Int = 0
        var artMinMemory: Long = 0L
        var artMaxMemory: Long = 0L
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public data class MemoryMetricsSnapshot(
    @JvmField
    internal val bufferIndex: Int,
) : LinkedMetricsSnapshot<MemoryMetricsSnapshot>()
