package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance.start
import com.bugsnag.android.performance.internal.Connectivity
import com.bugsnag.android.performance.internal.DiscardingSampler
import com.bugsnag.android.performance.internal.HttpDelivery
import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.LoadDeviceId
import com.bugsnag.android.performance.internal.Module
import com.bugsnag.android.performance.internal.Persistence
import com.bugsnag.android.performance.internal.ProbabilitySampler
import com.bugsnag.android.performance.internal.RetryDelivery
import com.bugsnag.android.performance.internal.RetryDeliveryTask
import com.bugsnag.android.performance.internal.SamplerTask
import com.bugsnag.android.performance.internal.SendBatchTask
import com.bugsnag.android.performance.internal.Task
import com.bugsnag.android.performance.internal.Worker
import com.bugsnag.android.performance.internal.createResourceAttributes
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.isInForeground
import com.bugsnag.android.performance.internal.metrics.SystemConfig
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import java.net.URL

/**
 * Primary access to the Bugsnag Performance SDK.
 *
 * @see [start]
 */
public object BugsnagPerformance {
    public const val VERSION: String = "1.12.0"

    @get:JvmName("getInstrumentedAppState\$internal")
    internal val instrumentedAppState = InstrumentedAppState()

    private var isStarted = false

    private lateinit var worker: Worker

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
                    startUnderLock(ImmutableConfig(configuration))
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

    private fun startUnderLock(configuration: ImmutableConfig) {
        Logger.delegate = configuration.logger
        val tracer = instrumentedAppState.configure(configuration)

        if (configuration.autoInstrumentAppStarts) {
            // mark the app as "starting" (if it isn't already)
            synchronized(this) {
                instrumentedAppState.onBugsnagPerformanceStart()
            }
        } else {
            instrumentedAppState.startupTracker.disableAppStartTracking()
        }

        val application = configuration.application

        // update isInForeground to a more accurate value (if accessible)
        instrumentedAppState.defaultAttributeSource.update {
            it.copy(isInForeground = isInForeground(application))
        }

        val bsgWorker = Worker {
            val resourceAttributes = createResourceAttributes(configuration)
            LoadDeviceId(application, resourceAttributes).run()

            SystemConfig.configure()

            val connectivity =
                Connectivity.newInstance(application) { status ->
                    if (status.hasConnection && this::worker.isInitialized) {
                        worker.wake()
                    }

                    instrumentedAppState.defaultAttributeSource.update {
                        it.copy(
                            networkType = status.networkType,
                            networkSubType = status.networkSubType,
                        )
                    }
                }

            connectivity.registerForNetworkChanges()

            val httpDelivery = HttpDelivery(
                configuration.endpoint,
                requireNotNull(configuration.apiKey) {
                    "PerformanceConfiguration.apiKey may not be null"
                },
                connectivity,
                configuration.samplingProbability != null,
                configuration,
            )

            val persistence = Persistence(application)
            val delivery = RetryDelivery(persistence.retryQueue, httpDelivery)

            val workerTasks = ArrayList<Task>()
            if (configuration.isReleaseStageEnabled) {
                val sampler: ProbabilitySampler
                if (configuration.samplingProbability == null) {
                    sampler = ProbabilitySampler(1.0)

                    val samplerTask = SamplerTask(
                        delivery,
                        sampler,
                        persistence.persistentState,
                    )
                    delivery.newProbabilityCallback = samplerTask
                    workerTasks.add(samplerTask)
                } else {
                    sampler = ProbabilitySampler(configuration.samplingProbability)
                }

                tracer.sampler = sampler
            } else {
                tracer.sampler = DiscardingSampler
            }

            workerTasks.add(SendBatchTask(delivery, tracer, resourceAttributes))
            workerTasks.add(RetryDeliveryTask(persistence.retryQueue, httpDelivery, connectivity))

            return@Worker workerTasks
        }

        // register the Worker with the components that depend on it
        tracer.worker = bsgWorker

        loadModules()

        bsgWorker.start()

        worker = bsgWorker

        NotifierIntegration.link()
    }

    private fun loadModules() {
        val moduleLoader = Module.Loader(instrumentedAppState)
        moduleLoader.loadModule("com.bugsnag.android.performance.AppCompatModule")
        moduleLoader.loadModule("com.bugsnag.android.performance.okhttp.OkhttpModule")
        moduleLoader.loadModule("com.bugsnag.android.performance.compose.ComposeModule")
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
        // create & track Activity referenced ViewLoad spans
        return instrumentedAppState.activityInstrumentation.startViewLoadSpan(activity, options)
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
        synchronized(this) {
            instrumentedAppState.startupTracker.onFirstClassLoadReported()
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
public inline fun <R> measureSpan(
    name: String,
    block: () -> R,
): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
