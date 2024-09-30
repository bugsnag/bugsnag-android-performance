package com.bugsnag.android.performance.internal.framerate

import android.view.Choreographer
import android.view.Display
import android.view.FrameMetrics
import android.view.Window
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

abstract class AbsFramerateCollectorTest {
    protected lateinit var window: Window
    protected lateinit var choreographer: Choreographer
    private lateinit var metricsContainer: FramerateMetricsContainer
    private lateinit var framerateCollector: FramerateCollector

    internal abstract fun createFramerateCollector(metricsContainer: FramerateMetricsContainer): FramerateCollector
    internal abstract fun notifyFrame(
        startTimeMs: Long,
        durationMs: Long,
        deadlineMs: Long,
        collector: FramerateCollector,
    )

    @Before
    open fun setUp() {
        window = mock<Window>()
        choreographer = mock<Choreographer>()
        metricsContainer = FramerateMetricsContainer()
        framerateCollector = createFramerateCollector(metricsContainer)
    }

    @Test
    fun idealFramerate() {
        val totalFrameCount = 1000

        var time = 100L
        val frameDuration = 16L
        repeat(totalFrameCount) {
            notifyFrame(time, frameDuration, frameDuration, framerateCollector)
            time += frameDuration
        }

        assertEquals(totalFrameCount.toLong(), metricsContainer.totalFrameCount)
        assertEquals(0L, metricsContainer.slowFrameCount)
        assertEquals(0L, metricsContainer.frozenFrameCount)
    }

    @Test
    fun droppedFrames() {
        val totalFrameCount = 1000

        var time = 100L
        val frameDeadline = 16L
        repeat(totalFrameCount) { index ->
            // we make every 4'th frame a "slow" frame
            val frameDuration =
                if (index % 4 == 0) {
                    frameDeadline * 2
                } else {
                    frameDeadline
                }

            notifyFrame(time, frameDuration, frameDeadline, framerateCollector)
            time += frameDuration
        }

        assertEquals(totalFrameCount.toLong(), metricsContainer.totalFrameCount)
        assertEquals(totalFrameCount / 4L, metricsContainer.slowFrameCount)
        assertEquals(0L, metricsContainer.frozenFrameCount)
    }

    fun frozenFrames() {
        val totalFrameCount = 1000

        var time = 100L
        val frameDeadline = 16L
        repeat(totalFrameCount) { index ->
            // we make every 10'th frame a "slow" frame
            val frameDuration =
                if (index % 10 == 0) {
                    1000L
                } else {
                    frameDeadline
                }

            notifyFrame(time, frameDuration, frameDeadline, framerateCollector)
            time += frameDuration
        }

        assertEquals(totalFrameCount.toLong(), metricsContainer.totalFrameCount)
        assertEquals(0L, metricsContainer.slowFrameCount)
        assertEquals(totalFrameCount / 10L, metricsContainer.frozenFrameCount)
    }
}

@Suppress("DEPRECATION") // windowManager.defaultDisplay
open class FramerateCollector24Test : AbsFramerateCollectorTest() {
    private lateinit var windowManager: WindowManager
    private lateinit var display: Display

    private var lastFrameStart: Long = 0

    @Before
    override fun setUp() {
        super.setUp()

        display = mock<Display>()
        windowManager = mock<WindowManager> {
            whenever(it.defaultDisplay).doReturn(display)
        }

        whenever(window.windowManager).doReturn(windowManager)
    }

    override fun createFramerateCollector(metricsContainer: FramerateMetricsContainer): FramerateCollector {
        // since we run against a stub Choreographer - there is no mLastFrameTimeNanos field
        // so instead we override the reflection for unit testing
        return object : FramerateCollector24(window, choreographer, metricsContainer) {
            override val FrameMetrics.frameStart: Long
                get() = lastFrameStart
        }
    }

    override fun notifyFrame(
        startTimeMs: Long,
        durationMs: Long,
        deadlineMs: Long,
        collector: FramerateCollector,
    ) {
        lastFrameStart = startTimeMs * FramerateCollector.NANOS_IN_MS
        whenever(window.windowManager.defaultDisplay.refreshRate)
            .doReturn((1000L / deadlineMs).toFloat())

        val metrics = createFrameMetrics(startTimeMs, durationMs)

        collector.onFrameMetricsAvailable(window, metrics, 0)
    }

    protected open fun createFrameMetrics(startTimeMs: Long, durationMs: Long) =
        mock<FrameMetrics> {
            whenever(it.getMetric(FrameMetrics.DRAW_DURATION))
                .doReturn(durationMs * FramerateCollector.NANOS_IN_MS)
        }
}

class FramerateCollector26Test : FramerateCollector24Test() {
    override fun createFrameMetrics(startTimeMs: Long, durationMs: Long) =
        mock<FrameMetrics> {
            whenever(it.getMetric(FrameMetrics.DRAW_DURATION))
                .doReturn(durationMs * FramerateCollector.NANOS_IN_MS)
            whenever(it.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                .doReturn(startTimeMs * FramerateCollector.NANOS_IN_MS)
        }
}

class FramerateCollector31Test : AbsFramerateCollectorTest() {
    override fun createFramerateCollector(metricsContainer: FramerateMetricsContainer): FramerateCollector {
        return FramerateCollector31(metricsContainer)
    }

    override fun notifyFrame(
        startTimeMs: Long,
        durationMs: Long,
        deadlineMs: Long,
        collector: FramerateCollector,
    ) {
        val metrics = mock<FrameMetrics> {
            whenever(it.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
                .doReturn(startTimeMs * FramerateCollector.NANOS_IN_MS)
            whenever(it.getMetric(FrameMetrics.DRAW_DURATION))
                .doReturn(durationMs * FramerateCollector.NANOS_IN_MS)
            whenever(it.getMetric(FrameMetrics.DEADLINE))
                .doReturn(deadlineMs * FramerateCollector.NANOS_IN_MS)
        }

        collector.onFrameMetricsAvailable(window, metrics, 0)
    }
}
