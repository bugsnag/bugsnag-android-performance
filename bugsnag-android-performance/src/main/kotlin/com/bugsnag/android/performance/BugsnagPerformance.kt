package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance.start
import com.bugsnag.android.performance.internal.ConnectivityCompat
import com.bugsnag.android.performance.internal.DefaultAttributeSource
import com.bugsnag.android.performance.internal.HttpDelivery
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.PerformanceComponentCallbacks
import com.bugsnag.android.performance.internal.PerformanceLifecycleCallbacks
import com.bugsnag.android.performance.internal.RetryDelivery
import com.bugsnag.android.performance.internal.SendBatchTask
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.Tracer
import com.bugsnag.android.performance.internal.Worker
import com.bugsnag.android.performance.internal.createResourceAttributes
import com.bugsnag.android.performance.internal.isInForeground
import java.net.URL

/**
 * Primary access to the Bugsnag Performance SDK.
 *
 * @see [start]
 */
object BugsnagPerformance {
    const val VERSION: String = "0.0.0"

    private val tracer = Tracer()

    private val activitySpanTracker = SpanTracker<Activity>()

    private val defaultAttributeSource = DefaultAttributeSource()
    private val spanFactory = SpanFactory(tracer, defaultAttributeSource)
    private val platformCallbacks = createLifecycleCallbacks()

    private var isStarted = false

    private var worker: Worker? = null

    /**
     * Initialise the Bugsnag Performance SDK. This should be called within your
     * [Application.onCreate] to ensure that all measurements are accurately reported.
     *
     * @param context an Android `Context`, typically your `Application` instance
     */
    @JvmStatic
    fun start(context: Context) {
        start(PerformanceConfiguration.load(context))
    }

    @JvmStatic
    fun start(context: Context, apiKey: String) {
        start(PerformanceConfiguration.load(context, apiKey))
    }

    /**
     * Initialise the Bugsnag Performance SDK. This should be called within your
     * [Application.onCreate] to ensure that all measurements are accurately reported.
     *
     * @param configuration the configuration for the SDK
     */
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
                    worker?.wake()
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

        val delivery = RetryDelivery(
            InternalDebug.dropSpansOlderThanMs,
            HttpDelivery(
                configuration.endpoint,
                requireNotNull(configuration.apiKey) {
                    "PerformanceConfiguration.apiKey may not be null"
                }
            )
        )

        val bsgWorker = Worker(
            SendBatchTask(delivery, tracer, createResourceAttributes(configuration))
        )

        // register the Worker with the components that depend on it
        tracer.worker = bsgWorker

        bsgWorker.start()

        worker = bsgWorker
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

    /**
     * Open a custom span with a given name and optionally a specific [startTime]. The reported
     * name of these spans is `"Custom/$name"`.
     *
     * @param name the name of the custom span to open
     * @param startTime the [Span.startTime] in nanoseconds relative to [SystemClock.elapsedRealtimeNanos]
     */
    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        spanFactory.createCustomSpan(name, startTime)

    /**
     * Open a network span for a given url and HTTP [verb] to measure the time taken for an HTTP request.
     *
     * @param url the URL the returned span is measuring
     * @param verb the HTTP verb / method (GET, POST, PUT, etc.)
     * @param startTime the [Span.startTime] in nanoseconds relative to [SystemClock.elapsedRealtimeNanos]
     */
    @JvmStatic
    @JvmOverloads
    fun startNetworkSpan(
        url: URL,
        verb: String,
        startTime: Long = SystemClock.elapsedRealtimeNanos()
    ): Span = spanFactory.createNetworkSpan(url, verb, startTime)

    /**
     * Open a ViewLoad span to measure the time taken to load and render a UI element (typically a screen).
     * These spans are created and measured [automatically for
     * activities](PerformanceConfiguration.autoInstrumentActivities). This function can be used
     * when the automated instrumentation is not well suited to your app.
     *
     * @param activity the activity load being measured
     * @param startTime the [Span.startTime] in nanoseconds relative to [SystemClock.elapsedRealtimeNanos]
     * @see [endViewLoadSpan]
     */
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

    /**
     * End a ViewLoad span opened with `startViewLoadSpan`, useful when you don't want to manually
     * retain a reference to the [Span] returned. If no span is being tracked for the given [Activity]
     * or the measurement has already been ended this method does nothing.
     *
     * @param activity the activity load being measured
     * @param endTime the time that the load should be considered "ended" relative to [SystemClock.elapsedRealtimeNanos]
     */
    @JvmStatic
    @JvmOverloads
    fun endViewLoadSpan(activity: Activity, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        activitySpanTracker.endSpan(activity, endTime)
    }

    /**
     * Open a ViewLoad span to measure the time taken to load a specific UI element.
     *
     * @param viewType the type of UI element being measured
     * @param viewName the name (typically class name) of the UI element being measured
     * @param startTime the [Span.startTime] in nanoseconds relative to [SystemClock.elapsedRealtimeNanos]
     */
    @JvmStatic
    @JvmOverloads
    fun startViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        startTime: Long = SystemClock.elapsedRealtimeNanos()
    ): Span = spanFactory.createViewLoadSpan(viewType, viewName, startTime)

    /**
     * Report that your apps `Application` class has been loaded. This can be manually called from
     * a static initializer to improve the app start time measurements:
     *
     * ```java
     * public class MyApplication extends Application {
     *     static {
     *         BugsnagPerformance.reportApplicationClassLoaded()
     * ```
     *
     * Kotlin apps can use the `init` block of the `companion object` to achieve the same effect.
     *
     * This method is safe to call before [start].
     */
    @JvmStatic
    fun reportApplicationClassLoaded() {
        synchronized(this) {
            platformCallbacks.startAppLoadSpan("Cold")
        }
    }
}

/**
 * A utility method for Kotlin apps which compliments the standard Kotlin `measureTime`,
 * `measureTimeMillis`, and `measureNanoTime` methods. This method has the same behaviour as:
 * ```kotlin
 * BugsnagPerformance.startSpan(name).use { block() }
 * ```
 *
 * @param name the name of the block to measure
 * @param block the block of code to measure the execution time
 * @see [BugsnagPerformance.startSpan]
 */
inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
