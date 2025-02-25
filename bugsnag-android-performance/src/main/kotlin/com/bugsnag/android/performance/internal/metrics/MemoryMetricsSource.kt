package com.bugsnag.android.performance.internal.metrics

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.AndroidException
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.getActivityManager
import com.bugsnag.android.performance.internal.util.FixedRingBuffer

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
                val pssPages = memoryInfoSample.dalvikPss +
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
            val target = snapshot.target
            if (target != null) {
                val from = snapshot.bufferIndex
                val to = endIndex
                val sampleCount = buffer.countItemsBetween(from, to)

                val deviceMemorySamples = LongArray(sampleCount)
                var deviceUsedMemoryTotal = 0L
                var deviceUsedMemoryCount = 0

                val artMemoryTimestamps = LongArray(sampleCount)
                val artUsedMemorySamples = LongArray(sampleCount)
                var artSizeMemory = 0L
                var artUsedMemoryTotal = 0L
                var artUsedMemoryCount = 0

                buffer.forEachIndexed(from, to) { index, sample ->
                    artMemoryTimestamps[index] = sample.timestamp
                    deviceMemorySamples[index] = sample.pss

                    val artUsedMemory = sample.totalMemory - sample.freeMemory
                    if (artUsedMemory > 0) {
                        artUsedMemorySamples[index] = artUsedMemory
                        artUsedMemoryTotal += artUsedMemory
                        artUsedMemoryCount++
                    }

                    if (sample.pss > 0) {
                        deviceUsedMemoryTotal += sample.pss
                        deviceUsedMemoryCount++
                    }

                    if (sample.totalMemory > artSizeMemory) {
                        artSizeMemory = sample.totalMemory
                    }
                }

                target.attributes["bugsnag.system.memory.spaces.space_names"] = SPACE_NAMES

                deviceMemory?.also {
                    target.attributes["bugsnag.device.physical_device_memory"] = it
                    target.attributes["bugsnag.system.memory.spaces.device.size"] = it
                }

                target.attributes["bugsnag.system.memory.spaces.device.used"] = deviceMemorySamples

                if (deviceUsedMemoryCount > 0) {
                    target.attributes["bugsnag.system.memory.spaces.device.mean"] =
                        deviceUsedMemoryTotal / deviceUsedMemoryCount
                }

                target.attributes["bugsnag.system.memory.spaces.art.size"] = artSizeMemory
                target.attributes["bugsnag.system.memory.spaces.art.used"] = artUsedMemorySamples

                if (artUsedMemoryCount > 0) {
                    target.attributes["bugsnag.system.memory.spaces.art.mean"] =
                        artUsedMemoryTotal / artUsedMemoryCount
                }

                target.attributes["bugsnag.system.memory.timestamps"] = artMemoryTimestamps

                snapshot.blocking?.cancel()
            }
        }
    }

    override fun createStartMetrics(): MemoryMetricsSnapshot {
        return MemoryMetricsSnapshot(buffer.currentIndex)
    }

    private fun calculateTotalMemory(): Long? {
        val totalMemory = appContext.getActivityManager()
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

        val SPACE_NAMES = arrayOf("device", "art")
    }
}

internal data class MemoryMetricsSnapshot(
    @JvmField
    internal val bufferIndex: Int,
) : LinkedMetricsSnapshot<MemoryMetricsSnapshot>()
