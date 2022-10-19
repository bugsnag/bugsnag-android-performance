package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.PerformanceActivityLifecycleCallbacks
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.Tracer
import java.net.URL
import java.util.UUID

object BugsnagPerformance {
    private val activitySpanTracker = SpanTracker<Activity>()

    private val tracer = Tracer()
    private var isStarted = false

    private val spanFactory = SpanFactory(tracer)

    @JvmStatic
    fun start(configuration: PerformanceConfiguration) {
        if (!isStarted) {
            synchronized(this) {
                if (!isStarted) {
                    startUnderLock(configuration)
                    isStarted = true
                }
            }
        } else {
            logAlreadyStarted()
        }
    }

    private fun logAlreadyStarted() {
        Logger.w("BugsnagPerformance.start has already been called")
    }

    private fun startUnderLock(configuration: PerformanceConfiguration) {
        tracer.start(
            Delivery(configuration.endpoint),
            configuration.context.packageName
        )

        val application = configuration.context.applicationContext as Application
        application.registerActivityLifecycleCallbacks(
            PerformanceActivityLifecycleCallbacks(
                configuration.autoInstrumentActivities != AutoInstrument.OFF,
                configuration.autoInstrumentActivities == AutoInstrument.FULL,
                activitySpanTracker,
                spanFactory
            )
        )
    }

    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        spanFactory.createSpan(name, startTime)

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
        return spanFactory.createViewLoadSpan(activity, startTime).also {
            activitySpanTracker[activity] = it
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
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
