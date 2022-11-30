package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import com.bugsnag.android.performance.internal.ConnectivityCompat
import com.bugsnag.android.performance.internal.DefaultAttributeSource
import com.bugsnag.android.performance.internal.PerformanceComponentCallbacks
import com.bugsnag.android.performance.internal.PerformanceLifecycleCallbacks
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.Tracer
import com.bugsnag.android.performance.internal.isInForeground
import java.net.URL

object BugsnagPerformance {
    const val VERSION: String = "0.0.0"

    private val tracer = Tracer()

    private val activitySpanTracker = SpanTracker<Activity>()

    private val defaultAttributeSource = DefaultAttributeSource()
    private val spanFactory = SpanFactory(tracer, defaultAttributeSource)
    private val platformCallbacks = createLifecycleCallbacks()

    private var isStarted = false

    @JvmStatic
    fun start(configuration: PerformanceConfiguration) {
        if (!isStarted) {
            synchronized(this) {
                if (!isStarted) {
                    startUnderLock(configuration.validated())
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
        val application = configuration.context.applicationContext as Application
        configureLifecycleCallbacks(configuration)

        if (configuration.autoInstrumentAppStarts) {
            // mark the app as "starting" (if it isn't already)
            platformCallbacks.startAppLoadSpan("Cold")
        }

        // update isInForeground to a more accurate value (if accessible)
        defaultAttributeSource.update {
            it.copy(isInForeground = isInForeground(application))
        }

        tracer.sampler.fallbackProbability = configuration.samplingProbability

        val connectivity =
            ConnectivityCompat(application) { hasConnection, _, networkType, networkSubType ->
                if (hasConnection) {
                    tracer.sendNextBatch()
                }

                defaultAttributeSource.update {
                    it.copy(
                        networkType = networkType,
                        networkSubType = networkSubType
                    )
                }
            }

        connectivity.registerForNetworkChanges()

        application.registerActivityLifecycleCallbacks(platformCallbacks)
        application.registerComponentCallbacks(PerformanceComponentCallbacks(tracer))

        tracer.start(configuration)
    }

    private fun createLifecycleCallbacks(): PerformanceLifecycleCallbacks {
        return PerformanceLifecycleCallbacks(activitySpanTracker, spanFactory) { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }
        }
    }

    private fun configureLifecycleCallbacks(configuration: PerformanceConfiguration) {
        platformCallbacks.apply {
            openLoadSpans = configuration.autoInstrumentActivities != AutoInstrument.OFF
            closeLoadSpans = configuration.autoInstrumentActivities == AutoInstrument.FULL
            instrumentAppStart = configuration.autoInstrumentAppStarts
        }
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
            platformCallbacks.startAppLoadSpan("Cold")
        }
    }
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
