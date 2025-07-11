package com.bugsnag.android.performance.internal.framerate

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Window
import android.view.Window.OnFrameMetricsAvailableListener
import androidx.annotation.RequiresApi
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.metrics.MetricSource
import java.util.WeakHashMap

internal class FramerateMetricsSource(
    private val spanFactory: SpanFactory,
) : Application.ActivityLifecycleCallbacks,
    MetricSource<FramerateMetricsSnapshot> {
    private val frameMetricsListeners = WeakHashMap<Activity, OnFrameMetricsAvailableListener>()
    private val thread = HandlerThread("Bugsnag FrameMetrics thread")
    private val handler: Handler by lazy {
        thread.start()
        Handler(thread.looper)
    }

    private val metricsContainer = FramerateMetricsContainer()

    override fun createStartMetrics(): FramerateMetricsSnapshot {
        return metricsContainer.snapshot()
    }

    override fun endMetrics(
        startMetrics: FramerateMetricsSnapshot,
        span: Span,
    ) {
        val currentMetrics = metricsContainer.snapshot()

        if (currentMetrics.totalFrameCount > startMetrics.totalFrameCount) {
            val attributes = (span as SpanImpl).attributes
            attributes["bugsnag.rendering.slow_frames"] =
                currentMetrics.slowFrameCount - startMetrics.slowFrameCount
            attributes["bugsnag.rendering.frozen_frames"] =
                currentMetrics.frozenFrameCount - startMetrics.frozenFrameCount
            attributes["bugsnag.rendering.total_frames"] =
                currentMetrics.totalFrameCount - startMetrics.totalFrameCount

            startMetrics.forEachFrozenFrameUntil(currentMetrics) { start, end ->
                spanFactory.createCustomSpan(
                    "FrozenFrame",
                    SpanOptions
                        .within(span)
                        .startTime(start)
                        .setFirstClass(false)
                        .makeCurrentContext(false),
                ).end(end)
            }
        }
    }

    private fun createFrameMetricsAvailableListener(window: Window): OnFrameMetricsAvailableListener? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            FramerateCollector31(metricsContainer)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            FramerateCollector26(window, Choreographer.getInstance(), metricsContainer)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FramerateCollector24(window, Choreographer.getInstance(), metricsContainer)
        } else {
            null
        }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityStarted(activity: Activity) {
        val window = activity.window ?: return
        val frameMetricsAvailableListener = createFrameMetricsAvailableListener(window) ?: return
        window.addOnFrameMetricsAvailableListener(frameMetricsAvailableListener, handler)
        frameMetricsListeners[activity] = frameMetricsAvailableListener
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onActivityStopped(activity: Activity) {
        val listener = frameMetricsListeners.remove(activity) ?: return
        try {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: RuntimeException) {
            // this is possible (as a NullPointerException) when the activity has no listeners
            // it's unlikely, but we don't want to crash the app if it does ever happen
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) = Unit

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit
}
