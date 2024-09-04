package com.bugsnag.android.performance.internal.framerate

import android.os.Build
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import kotlin.math.max

@RequiresApi(Build.VERSION_CODES.N)
internal abstract class FramerateCollector(
    private val metricsContainer: FramerateMetricsContainer,
) : Window.OnFrameMetricsAvailableListener {

    private var previousFrameTime: Long = 0L

    override fun onFrameMetricsAvailable(
        window: Window,
        frameMetrics: FrameMetrics,
        dropCountSinceLastInvocation: Int,
    ) {
        val frameStartTime = max(previousFrameTime, frameMetrics.frameStart)
        // early-exit if this appears to be a duplicate frame
        if (frameStartTime == previousFrameTime) {
            return
        }

        // count the number of times onFrameMetricsAvailable is called - this *must* be done first
        metricsContainer.totalMetricsCount++

        val totalDuration = frameMetrics.totalDuration
        val deadline = frameMetrics.deadline

        // we allow for a 5% overrun when considering "slow" frames
        val adjustedDeadline: Long = (deadline * 1.05).toLong()

        if (totalDuration >= FROZEN_FRAME_TIME) {
            metricsContainer.frozenFrameCount++
        } else if (totalDuration >= adjustedDeadline) {
            metricsContainer.slowFrameCount++
        }

        metricsContainer.totalFrameCount += dropCountSinceLastInvocation + 1
        previousFrameTime = frameStartTime
    }

    open val FrameMetrics.totalDuration: Long
        get() = (getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
                + getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
                + getMetric(FrameMetrics.ANIMATION_DURATION)
                + getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                + getMetric(FrameMetrics.DRAW_DURATION)
                + getMetric(FrameMetrics.SYNC_DURATION))

    abstract val FrameMetrics.deadline: Long
    abstract val FrameMetrics.frameStart: Long

    internal companion object {
        /**
         * Deadline for frozen frames, in nanoseconds
         */
        const val FROZEN_FRAME_TIME = 700 * 1_000_000L
    }
}

@RequiresApi(24)
internal open class FramerateCollector24(
    private val window: Window,
    private val choreographer: Choreographer,
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector(metricsContainer) {
    override val FrameMetrics.frameStart: Long
        get() = choreographer.lastFrameTimeNanos ?: -1

    private val currentRefreshRate: Float
        get() {
            // based heavily on
            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi16Impl.kt;l=265
            val currentWindow = window ?: return 60f

            @Suppress("DEPRECATION")
            var refreshRate = currentWindow.windowManager.defaultDisplay.refreshRate
            if (refreshRate < 30f || refreshRate > 200f) {
                refreshRate = 60f
            }

            return refreshRate
        }

    override val FrameMetrics.deadline: Long
        get() = ((1000L * 1_000_000L) / currentRefreshRate).toLong()
}

@RequiresApi(26)
internal open class FramerateCollector26(
    window: Window,
    choreographer: Choreographer,
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector24(window, choreographer, metricsContainer) {
    override val FrameMetrics.frameStart: Long
        get() = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
}

@RequiresApi(31)
internal class FramerateCollector31(
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector(metricsContainer) {
    override val FrameMetrics.deadline: Long
        get() = getMetric(FrameMetrics.DEADLINE)
    override val FrameMetrics.frameStart: Long
        get() = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
}
