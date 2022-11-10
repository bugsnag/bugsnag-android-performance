package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.SystemClock
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.PerformancePlatformCallbacks
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.Tracer
import java.net.URL


object BugsnagPerformance: ComponentCallbacks2 {
    private val tracer = Tracer()

    private val activitySpanTracker = SpanTracker<Activity>()
    private val spanFactory = SpanFactory(tracer)

    private val platformCallbacks = PerformancePlatformCallbacks(activitySpanTracker, spanFactory)

    private var isStarted = false

    @JvmStatic
    fun start(configuration: PerformanceConfiguration) {
        if (!isStarted) {
            synchronized(this) {
                if (!isStarted) {
                    startUnderLock(configuration)
                    isStarted = true
                }
            }
            configuration.context.registerComponentCallbacks(this)
        } else {
            logAlreadyStarted()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {}
    override fun onLowMemory() {}
    override fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // App has been backgrounded
            tracer.sendNextBatch()
        }
    }

    private fun logAlreadyStarted() {
        Logger.w("BugsnagPerformance.start has already been called")
    }

    private fun startUnderLock(configuration: PerformanceConfiguration) {
        val application = configuration.context.applicationContext as Application

        platformCallbacks.openLoadSpans =
            configuration.autoInstrumentActivities != AutoInstrument.OFF
        platformCallbacks.closeLoadSpans =
            configuration.autoInstrumentActivities == AutoInstrument.FULL
        platformCallbacks.instrumentAppStart = configuration.autoInstrumentAppStarts

        if (configuration.autoInstrumentAppStarts) {
            // mark the app as "starting" (if it isn't already)
            platformCallbacks.startAppLoadSpanUnderLock("Cold")
        }

        application.registerActivityLifecycleCallbacks(platformCallbacks)

        tracer.start(
            Delivery(configuration.endpoint),
            configuration.context.packageName
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        spanFactory.createCustomSpan(name, startTime)

    @JvmStatic
    @JvmOverloads
    fun startNetworkSpan(
        url: URL,
        verb: String,
        startTime: Long = SystemClock.elapsedRealtimeNanos()
    ): Span = spanFactory.createNetworkSpan(url, verb, startTime)

    @JvmStatic
    @JvmOverloads
    fun startViewLoadSpan(
        activity: Activity,
        startTime: Long = SystemClock.elapsedRealtimeNanos()
    ): Span {
        // create & track Activity referenced ViewLoad spans
        return activitySpanTracker.track(activity) {
            spanFactory.createViewLoadSpan(activity, startTime)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun endViewLoadSpan(activity: Activity, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        activitySpanTracker.endSpan(activity, endTime)
    }

    @JvmStatic
    @JvmOverloads
    fun startViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        startTime: Long = SystemClock.elapsedRealtimeNanos()
    ): Span = spanFactory.createViewLoadSpan(viewType, viewName, startTime)

    @JvmStatic
    fun reportApplicationClassLoaded() {
        synchronized(this) {
            platformCallbacks.startAppLoadSpanUnderLock("Cold")
        }
    }
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
