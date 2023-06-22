package com.bugsnag.android.performance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import com.bugsnag.android.performance.BugsnagPerformance.start
import com.bugsnag.android.performance.internal.AppStartPhase
import com.bugsnag.android.performance.internal.Connectivity
import com.bugsnag.android.performance.internal.DiscardingSampler
import com.bugsnag.android.performance.internal.HttpDelivery
import com.bugsnag.android.performance.internal.ImmutableConfig
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
    const val VERSION: String = "0.1.8"

    internal val tracer = Tracer()

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
        instrumentedAppState.configure(configuration)

        // mark the app as "starting" (if it isn't already)
        synchronized(this) {
            instrumentedAppState.bugsnagPerformanceStart(configuration.autoInstrumentAppStarts)
        }

        val application = configuration.application

        // update isInForeground to a more accurate value (if accessible)
        instrumentedAppState.defaultAttributeSource.update {
            it.copy(isInForeground = isInForeground(application))
        }

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
        )

        val persistence = Persistence(application)
        val delivery = RetryDelivery(persistence.retryQueue, httpDelivery)

        val workerTasks = ArrayList<Task>()

        if (configuration.isReleaseStageEnabled) {
            val sampler = ProbabilitySampler(1.0)

            val samplerTask = SamplerTask(
                delivery,
                sampler,
                persistence.persistentState,
            )

            delivery.newProbabilityCallback = samplerTask
            workerTasks.add(samplerTask)

            tracer.sampler = sampler
        } else {
            tracer.sampler = DiscardingSampler
        }

        val resourceAttributes = createResourceAttributes(configuration)
        workerTasks.add(SendBatchTask(delivery, tracer, resourceAttributes))
        workerTasks.add(RetryDeliveryTask(persistence.retryQueue, httpDelivery, connectivity))

        val bsgWorker = Worker(
            startupTasks = listOf(
                LoadDeviceId(application, resourceAttributes),
            ),
            tasks = workerTasks,
        )

        // register the Worker with the components that depend on it
        tracer.worker = bsgWorker

        loadModules()

        bsgWorker.start()

        worker = bsgWorker
    }

    private fun loadModules() {
        val moduleLoader = Module.Loader(instrumentedAppState)
        moduleLoader.loadModule("com.bugsnag.android.performance.AppCompatModule")
    }

    /**
     * Open a custom span with a given name and options.
     *
     * @param name the name of the custom span to open
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, options: SpanOptions = SpanOptions.DEFAULTS): Span =
        spanFactory.createCustomSpan(name, options)

    /**
     * Open a network span for a given url and HTTP [verb] to measure the time taken for an HTTP request.
     *
     * @param url the URL the returned span is measuring
     * @param verb the HTTP verb / method (GET, POST, PUT, etc.)
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    fun startNetworkRequestSpan(
        url: URL,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span = spanFactory.createNetworkSpan(url.toString(), verb, options)

    /**
     * Open a network span for a given url and HTTP [verb] to measure the time taken for an HTTP request.
     *
     * @param uri the URI/URL the returned span is measuring
     * @param verb the HTTP verb / method (GET, POST, PUT, etc.)
     * @param options the optional configuration for the span
     */
    @JvmStatic
    @JvmOverloads
    fun startNetworkRequestSpan(
        uri: Uri,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span = spanFactory.createNetworkSpan(uri.toString(), verb, options)

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
    fun startViewLoadSpan(
        activity: Activity,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span {
        // create & track Activity referenced ViewLoad spans
        return instrumentedAppState.activityCallbacks.startViewLoadSpan(activity, options)
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
    fun startViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
    ): Span = spanFactory.createViewLoadSpan(viewType, viewName, options)

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
            instrumentedAppState.startAppStartSpan("Cold")
            instrumentedAppState.startAppStartPhase(AppStartPhase.APPLICATION_INIT)
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
