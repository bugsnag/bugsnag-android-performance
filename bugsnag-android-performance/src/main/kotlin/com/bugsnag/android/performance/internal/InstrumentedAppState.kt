package com.bugsnag.android.performance.internal

import android.app.Application
import com.bugsnag.android.performance.AutoInstrument

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    var spanProcessor: SpanProcessor = RedirectingSpanProcessor()

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(spanProcessor, defaultAttributeSource)
    val lifecycleCallbacks = createLifecycleCallbacks()

    lateinit var app: Application
        private set

    private lateinit var connectivity: Connectivity
    private lateinit var worker: Worker

    internal fun bind(application: Application) {
        if (this::app.isInitialized) return

        app = application
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    internal fun configure(configuration: ImmutableConfig) {
        bind(configuration.application)

        val tracer = Tracer()

        val redirectingSpanProcessor = spanProcessor as? RedirectingSpanProcessor
        spanProcessor = tracer

        configureLifecycleCallbacks(configuration)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(tracer))

        lifecycleCallbacks.bugsnagPerformanceStart(configuration.autoInstrumentAppStarts)

        // update isInForeground to a more accurate value (if accessible)
        defaultAttributeSource.update {
            it.copy(isInForeground = isInForeground(app))
        }

        connectivity = configureConnectivity()
        worker = configureWorker(configuration, tracer)

        // drain the pre-start spans to the new tracer
        redirectingSpanProcessor?.drainAutoAppStartInstrumentation(
            configuration.autoInstrumentAppStarts,
            tracer,
        )

        tracer.worker = worker
        worker.start()
    }

    private fun RedirectingSpanProcessor.drainAutoAppStartInstrumentation(
        autoInstrumentAppStarts: Boolean,
        destination: SpanProcessor,
    ) {
        if (autoInstrumentAppStarts) {
            redirectTo(destination)
        } else {
            // when autoInstrumentAppStarts is off, we discard all of the AppStart/Phase spans
            redirectTo { span ->
                val category = (span as? SpanImpl)?.category
                if (category != SpanCategory.APP_START && category != SpanCategory.APP_START_PHASE) {
                    destination.onEnd(span)
                }
            }
        }
    }

    private fun createLifecycleCallbacks(): PerformanceLifecycleCallbacks {
        return PerformanceLifecycleCallbacks(spanTracker, spanFactory) { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }
        }
    }

    private fun configureLifecycleCallbacks(configuration: ImmutableConfig) {
        lifecycleCallbacks.apply {
            openLoadSpans = configuration.autoInstrumentActivities != AutoInstrument.OFF
            closeLoadSpans = configuration.autoInstrumentActivities == AutoInstrument.FULL
            instrumentAppStart = configuration.autoInstrumentAppStarts
        }
    }

    private fun configureConnectivity(): Connectivity {
        return Connectivity
            .newInstance(app) { status ->
                if (status.hasConnection && this::worker.isInitialized) {
                    worker.wake()
                }

                defaultAttributeSource.update {
                    it.copy(
                        networkType = status.networkType,
                        networkSubType = status.networkSubType,
                    )
                }
            }
            .apply { registerForNetworkChanges() }
    }

    private fun configureWorker(configuration: ImmutableConfig, tracer: Tracer): Worker {
        val httpDelivery = HttpDelivery(
            configuration.endpoint,
            requireNotNull(configuration.apiKey) {
                "PerformanceConfiguration.apiKey may not be null"
            },
            connectivity,
        )

        val persistence = Persistence(app)
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

        return Worker(
            startupTasks = listOf(LoadDeviceId(app, resourceAttributes)),
            tasks = workerTasks,
        )
    }

    fun startAppStartSpan(startType: String) = lifecycleCallbacks.startAppStartSpan(startType)

    fun startAppStartPhase(phase: AppStartPhase) = lifecycleCallbacks.startAppStartPhase(phase)

    companion object {
        /**
         * The token used to track the spans measuring the start of the app from when the
         * Application starts until the first Activity resumes.
         */
        val applicationToken = Any()
    }
}
