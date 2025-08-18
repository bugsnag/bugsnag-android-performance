package com.bugsnag.android.performance.internal

import android.app.Activity
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.controls.AppStartControlProvider
import com.bugsnag.android.performance.internal.controls.CompositeSpanControlProvider
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.metrics.SystemConfig
import com.bugsnag.android.performance.internal.plugins.PluginManager
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import com.bugsnag.android.performance.internal.util.Prioritized

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object BugsnagPerformanceImpl {
    public const val VERSION: String = "2.0.0"

    public val instrumentedAppState: InstrumentedAppState = InstrumentedAppState()

    public val spanFactory: SpanFactory get() = instrumentedAppState.spanFactory

    private lateinit var worker: Worker

    private val spanControlProvider =
        CompositeSpanControlProvider().apply {
            addProvider(
                Prioritized(
                    Int.MAX_VALUE,
                    AppStartControlProvider(instrumentedAppState.spanTracker),
                ),
            )
        }

    public fun startUnderLock(externalConfiguration: PerformanceConfiguration) {
        Logger.delegate = ImmutableConfig.getLogger(externalConfiguration)

        val pluginManager = PluginManager(externalConfiguration.plugins, spanFactory.spanTaskWorker)
        pluginManager.installPlugins(externalConfiguration)

        val configuration = ImmutableConfig(externalConfiguration, pluginManager)

        InternalDebug.configure(
            inDevelopment = externalConfiguration.isDevelopment,
            context = configuration.application,
        )

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

        val bsgWorker =
            Worker {
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

                val httpDelivery =
                    HttpDelivery(
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

                        val samplerTask =
                            SamplerTask(
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
                    spanFactory.sampler = sampler
                } else {
                    tracer.sampler = DiscardingSampler
                    spanFactory.sampler = DiscardingSampler
                }

                workerTasks.add(SendBatchTask(delivery, tracer, resourceAttributes))
                workerTasks.add(
                    RetryDeliveryTask(
                        persistence.retryQueue,
                        httpDelivery,
                        connectivity,
                    ),
                )

                // starting plugins is the last thing to do before starting the first tasks
                pluginManager.startPlugins()

                return@Worker workerTasks
            }

        // register the Worker with the components that depend on it
        tracer.worker = bsgWorker

        loadModules()

        spanControlProvider.addProviders(
            pluginManager.completeContext?.spanControlProviders.orEmpty(),
        )

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

    public fun startViewLoadSpan(
        activity: Activity,
        options: SpanOptions,
    ): Span {
        // create & track Activity referenced ViewLoad spans
        return instrumentedAppState.activityInstrumentation.startViewLoadSpan(activity, options)
    }

    public fun reportApplicationClassLoaded() {
        synchronized(this) {
            instrumentedAppState.startupTracker.onFirstClassLoadReported()
        }
    }

    public fun <C> getSpanControls(query: SpanQuery<C>): C? {
        @Suppress("UNCHECKED_CAST")
        return spanControlProvider[query as SpanQuery<Any>] as C
    }
}
