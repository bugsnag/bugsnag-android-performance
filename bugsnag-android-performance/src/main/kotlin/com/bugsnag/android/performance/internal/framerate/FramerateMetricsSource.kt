package com.bugsnag.android.performance.internal.framerate

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Choreographer
import android.view.Window
import android.view.Window.OnFrameMetricsAvailableListener
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.internal.MetricSource
import java.util.WeakHashMap

internal class FramerateMetricsSource : ActivityLifecycleCallbacks,
    MetricSource<FramerateMetricsSnapshot> {

    private val frameMetricsListeners = WeakHashMap<Activity, OnFrameMetricsAvailableListener>()
    private val thread = HandlerThread("Bugsnag Framerate Metrics")
    private val handler: Handler by lazy {
        thread.start()
        Handler(thread.looper)
    }

    private val metricsContainer = FramerateMetricsContainer()

    override fun createStartMetrics(): FramerateMetricsSnapshot {
        return metricsContainer.snapshot()
    }

    override fun endMetrics(startMetrics: FramerateMetricsSnapshot, attributes: HasAttributes) {
        val currentMetrics = metricsContainer.snapshot()

        if (currentMetrics.totalFrameCount > startMetrics.totalFrameCount) {
            attributes.setAttribute(
                "bugsnag.framerate.total_slow_frames",
                currentMetrics.slowFrameCount - startMetrics.slowFrameCount,
            )

            attributes.setAttribute(
                "bugsnag.framerate.total_frozen_frames",
                currentMetrics.frozenFrameCount - startMetrics.frozenFrameCount,
            )

            attributes.setAttribute(
                "bugsnag.framerate.total_frames",
                currentMetrics.totalFrameCount - startMetrics.totalFrameCount,
            )
        }
    }

    private fun createFrameMetricsAvailableListener(window: Window): OnFrameMetricsAvailableListener? =
        if (Build.VERSION.SDK_INT >= 31) FramerateCollector31(metricsContainer)
        else if (Build.VERSION.SDK_INT >= 26) FramerateCollector26(
            window,
            Choreographer.getInstance(),
            metricsContainer,
        )
        else if (Build.VERSION.SDK_INT >= 24) FramerateCollector24(
            window,
            Choreographer.getInstance(),
            metricsContainer,
        )
        else null

    override fun onActivityStarted(activity: Activity) {
        val window = activity.window ?: return
        val frameMetricsAvailableListener = createFrameMetricsAvailableListener(window) ?: return
        window.addOnFrameMetricsAvailableListener(frameMetricsAvailableListener, handler)
        frameMetricsListeners[activity] = frameMetricsAvailableListener
    }

    override fun onActivityStopped(activity: Activity) {
        val listener = frameMetricsListeners.remove(activity) ?: return
        try {
            activity.window.removeOnFrameMetricsAvailableListener(listener)
        } catch (_: RuntimeException) {
            // this possible (as a NullPointerException) when the activity has no listeners
            // it's unlikely, but we don't want to crash the app if it does ever happen
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
}