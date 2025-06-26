package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance.endViewLoadSpan
import com.bugsnag.android.performance.BugsnagPerformance.start
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.android.performance.internal.InstrumentedAppState
import java.net.URL

/**
 * Primary access to the Bugsnag Performance SDK.
 *
 * @see [start]
 */
public object BugsnagPerformance {
    public const val VERSION: String = BugsnagPerformanceImpl.VERSION

    private var isStarted = false

    private val instrumentedAppState: InstrumentedAppState
        get() = BugsnagPerformanceImpl.instrumentedAppState

    private val spanFactory get() = instrumentedAppState.spanFactory

    /**
     * Initialise the Bugsnag Performance SDK. This should be called within your
     * [Application.onCreate] to ensure that all measurements are accurately reported.
     *
     * @param context an Android `Context`, typically your `Application` instance
     */
    @JvmStatic
    public fun start(context: Context) {
        start(PerformanceConfiguration.load(context))
    }

    @JvmStatic
    public fun start(
        context: Context,
        apiKey: String,
    ) {
        start(PerformanceConfiguration.load(context, apiKey))
    }

    /**
     * Initialise the Bugsnag Performance SDK. This should be called within your
     * [Application.onCreate] to ensure that all measurements are accurately reported.
     *
     * @param configuration the configuration for the SDK
     */
    @JvmStatic
    public fun start(configuration: PerformanceConfiguration) {
        if (!isStarted) {
            synchronized(this) {
                if (!isStarted) {
                    BugsnagPerformanceImpl.startUnderLock(configuration)
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

    /**
     * Open a custom span with a given name and options.
     *
     * @param name the name of the custom span to open
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    public fun startSpan(
        name: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span = spanFactory.createCustomSpan(name, options)

    /**
     * Open a network span for a given url and HTTP [verb] to measure the time taken for an HTTP request.
     *
     * @param url the URL the returned span is measuring
     * @param verb the HTTP verb / method (GET, POST, PUT, etc.)
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    public fun startNetworkRequestSpan(
        url: URL,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span? = spanFactory.createNetworkSpan(url.toString(), verb, options)

    /**
     * Open a network span for a given url and HTTP [verb] to measure the time taken for an HTTP request.
     *
     * @param uri the URI/URL the returned span is measuring
     * @param verb the HTTP verb / method (GET, POST, PUT, etc.)
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    public fun startNetworkRequestSpan(
        uri: Uri,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span? = spanFactory.createNetworkSpan(uri.toString(), verb, options)

    /**
     * Open a ViewLoad span to measure the time taken to load and render a UI element (typically a screen).
     * These spans are created and measured [automatically for
     * activities](PerformanceConfiguration.autoInstrumentActivities). This function can be used
     * when the automated instrumentation is not well suited to your app.
     *
     * @param activity the activity load being measured
     * @param options the optional configuration for the span
     * @see [endViewLoadSpan]
     */
    @JvmStatic
    @JvmOverloads
    public fun startViewLoadSpan(
        activity: Activity,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span {
        return BugsnagPerformanceImpl.startViewLoadSpan(activity, options)
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
    public fun endViewLoadSpan(
        activity: Activity,
        endTime: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        instrumentedAppState.spanTracker.endSpan(activity, endTime = endTime)
    }

    /**
     * Open a ViewLoad span to measure the time taken to load a specific UI element.
     *
     * @param viewType the type of UI element being measured
     * @param viewName the name (typically class name) of the UI element being measured
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    public fun startViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span = spanFactory.createViewLoadSpan(viewType, viewName, options)

    /**
     * Report that your apps `Application` class has been loaded. This can be manually called from
     * your `Application` static initializer (or earlier if you have a custom `AppComponentFactory`)
     * to improve the app start time measurements:
     *
     * ```java
     * public class MyApplication extends Application {
     *     static {
     *         BugsnagPerformance.reportApplicationClassLoaded()
     * ```
     *
     * This function marks the start time of the `AppStart` span (if an earlier time has not already
     * been marked), and only affects cold start times. If this function is not called manually,
     * cold `AppStart` spans are opened by a `ContentProvider` (typically between the `Application`
     * object being created, and `Application.onCreate` being invoked).
     *
     * Kotlin apps can use the `init` block of the `companion object` to achieve the same effect.
     *
     * This function is typically called before [start] (and is ignored if called after [start]),
     * and can be safely called multiple times.
     */
    @JvmStatic
    public fun reportApplicationClassLoaded() {
        BugsnagPerformanceImpl.reportApplicationClassLoaded()
    }

    /**
     * Attempt to retrieve the span controls for a given [SpanQuery]. This is used to access
     * specialised behaviours for specific span types.
     *
     * @param query the span query to retrieve controls for
     * @return the span controls for the given query, or null if none exists or the query cannot
     *      be fulfilled
     */
    @JvmStatic
    public fun <C> getSpanControls(query: SpanQuery<C>): C? {
        return BugsnagPerformanceImpl.getSpanControls(query)
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
public inline fun <R> measureSpan(
    name: String,
    block: () -> R,
): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
