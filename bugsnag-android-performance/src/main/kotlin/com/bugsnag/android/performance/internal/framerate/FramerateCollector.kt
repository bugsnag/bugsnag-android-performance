package com.bugsnag.android.performance.internal.framerate

import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi

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
        val frameStartTime = frameMetrics.frameStart
        // early-exit if this appears to be a duplicate frame, or the first frame
        if (frameStartTime < previousFrameTime) {
            return
        } else if (frameMetrics.isFirstFrame) {
            previousFrameTime = frameStartTime
            return
        }

        // count the number of times onFrameMetricsAvailable is called - this must be the first
        // change (per frame) in the metricsContainer as we use it in optimistic read checks
        metricsContainer.update {
            val totalDuration = frameMetrics.totalDuration
            val deadline = frameMetrics.deadline

            // we allow for a 5% overrun when considering "slow" frames
            val adjustedDeadline: Long = (deadline * SLOW_FRAME_ADJUSTMENT).toLong()

        if (totalDuration >= adjustedDeadline) {
            if (totalDuration >= FROZEN_FRAME_TIME) {
                val frameEndTime = frameTimestampToClockTime(frameStartTime)
                countFrozenFrame(frameEndTime - totalDuration, frameEndTime)
            } else {
                countSlowFrame()
            }
        }

            previousFrameTime = frameStartTime + totalDuration
            return@update dropCountSinceLastInvocation + 1
        }
    }

    /**
     * Convert a frame start time from FrameMetrics to a timestamp relative to
     * SystemClock.elapsedRealtimeNanos
     */
    private fun frameTimestampToClockTime(vsyncTime: Long): Long {
        val nanoTime = System.nanoTime()
        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        return (vsyncTime - nanoTime) + elapsedRealtimeNanos
    }

    open val FrameMetrics.totalDuration: Long
        get() = (getMetric(FrameMetrics.UNKNOWN_DELAY_DURATION)
                + getMetric(FrameMetrics.INPUT_HANDLING_DURATION)
                + getMetric(FrameMetrics.ANIMATION_DURATION)
                + getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION)
                + getMetric(FrameMetrics.DRAW_DURATION)
                + getMetric(FrameMetrics.SYNC_DURATION))

    open val FrameMetrics.isFirstFrame: Boolean
        get() = getMetric(FrameMetrics.FIRST_DRAW_FRAME) != 0L

    abstract val FrameMetrics.deadline: Long
    abstract val FrameMetrics.frameStart: Long

    internal companion object {
        const val NANOS_IN_MS = 1_000_000L

        const val ONE_SECOND_MS = 1000L

        /**
         * Deadline for frozen frames, 700 milliseconds expressed as nanoseconds
         */
        const val FROZEN_FRAME_TIME = 700L * NANOS_IN_MS

        /**
         * Multiplier for the expected frame deadlines
         */
        const val SLOW_FRAME_ADJUSTMENT = 1.05

        const val FPS_MIN = 30f
        const val FPS_60 = 60f
        const val FPS_MAX = 200f
    }
}

@RequiresApi(Build.VERSION_CODES.N)
internal open class FramerateCollector24(
    private val window: Window,
    private val choreographer: Choreographer,
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector(metricsContainer) {
    override val FrameMetrics.frameStart: Long
        get() = choreographer.lastFrameTimeNanos ?: -1

    private val currentRefreshRate: Float
        get() {
            // avoid faulty return values (including 0), based heavily on
            // https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:metrics/metrics-performance/src/main/java/androidx/metrics/performance/JankStatsApi16Impl.kt;l=265
            val currentWindow = window

            @Suppress("DEPRECATION") // windowManager.defaultDisplay
            var refreshRate = currentWindow.windowManager.defaultDisplay.refreshRate
            if (refreshRate < FPS_MIN || refreshRate > FPS_MAX) {
                refreshRate = FPS_60
            }

            return refreshRate
        }

    override val FrameMetrics.deadline: Long
        get() = ((ONE_SECOND_MS * NANOS_IN_MS) / currentRefreshRate).toLong()
}

@RequiresApi(Build.VERSION_CODES.O)
internal open class FramerateCollector26(
    window: Window,
    choreographer: Choreographer,
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector24(window, choreographer, metricsContainer) {
    override val FrameMetrics.frameStart: Long
        get() = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
}

@RequiresApi(Build.VERSION_CODES.S)
internal class FramerateCollector31(
    metricsContainer: FramerateMetricsContainer,
) : FramerateCollector(metricsContainer) {
    override val FrameMetrics.deadline: Long
        get() = getMetric(FrameMetrics.DEADLINE)
    override val FrameMetrics.frameStart: Long
        get() = getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
}
