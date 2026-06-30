package com.bugsnag.android.performance.internal

import android.app.Activity
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.AppSessionConfig
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.internal.appsession.AppSessionBuffer
import com.bugsnag.android.performance.internal.appsession.AppSessionSpanController
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
    public const val VERSION: String = "2.3.0"

    public val instrumentedAppState: InstrumentedAppState = InstrumentedAppState()

    public val spanFactory: SpanFactory get() = instrumentedAppState.spanFactory

    private lateinit var worker: Worker
    private var appSessionSpanController: AppSessionSpanController? = null

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
            synchronized(this) {
                instrumentedAppState.onBugsnagPerformanceStart()
            }
        } else {
            instrumentedAppState.startupTracker.disableAppStartTracking()
        }

        val application = configuration.application

        // ── Per-app-session heap buffer + disk persistence ────────────────────
        val appSessionBuffer = AppSessionBuffer(application).also { it.start() }

        // ── Session controller wired for immediate per-app-session delivery ───
        // onAppSessionReady calls tracer.forceCurrentBatch() so each app-session span
        // is sent to Bugsnag as soon as it closes — not batched with later spans.
        appSessionSpanController = AppSessionSpanController(
            appContext = application,
            spanFactory = spanFactory,
            enabledMetrics = configuration.enabledMetrics,
            sessionConfig = externalConfiguration.appSessionConfig,
            buffer = appSessionBuffer,
            onAppSessionReady = { tracer.forceCurrentBatch() },
        )

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

                pluginManager.startPlugins()

                return@Worker workerTasks
            }

        tracer.worker = bsgWorker

        loadModules()

        spanControlProvider.addProviders(
            pluginManager.completeContext?.spanControlProviders.orEmpty(),
        )

        bsgWorker.start()

        worker = bsgWorker

        NotifierIntegration.link()
    }

    // ── Public session API ────────────────────────────────────────────────────

    /**
     * Manually start an app-session segment span (foreground or background).
     *
     * Each segment is sent to Bugsnag **immediately** when it ends (no batching with other
     * segments). A typed copy is also stored in the SDK's internal app-session buffer and
     * periodically persisted to disk so data survives process death.
     *
     * @param appSessionName optional human-readable label for this app session retained in the internal
     *   app-session buffer for local diagnostics / recovery. It is not emitted as a delivered span
     *   attribute. If null, uses [AppSessionConfig.manualSessionDefaultName] when configured.
     */
    public fun startAppSessionSpan(appSessionName: String? = null) {
        val options = resolveManualAppSessionStartOptions(
            appSessionConfig = appSessionSpanController?.sessionConfig,
            appSessionName = appSessionName,
        )
        appSessionSpanController?.startAppSessionSpan(appSessionName = options.appSessionName)
    }

    /**
     * Manually end the active app-session segment span.
     * The span is sent immediately to Bugsnag upon calling this method.
     */
    public fun endAppSessionSpan() {
        appSessionSpanController?.endAppSessionSpan()
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

internal data class ManualAppSessionStartOptions(
    val appSessionName: String?,
)

internal fun resolveManualAppSessionStartOptions(
    appSessionConfig: AppSessionConfig?,
    appSessionName: String?,
): ManualAppSessionStartOptions {
    val resolvedAppSessionName = appSessionName ?: appSessionConfig?.manualSessionDefaultName
    return ManualAppSessionStartOptions(
        appSessionName = resolvedAppSessionName,
    )
}
