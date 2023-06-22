package com.bugsnag.android.performance.internal

import android.app.Application
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(BugsnagPerformance.tracer, defaultAttributeSource)
    val lifecycleCallbacks = createLifecycleCallbacks()

    lateinit var app: Application
        private set

    internal fun bind(application: Application) {
        if (this::app.isInitialized) return

        app = application
        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(BugsnagPerformance.tracer))
    }

    internal fun configure(configuration: ImmutableConfig) {
        bind(configuration.application)
        configureLifecycleCallbacks(configuration)
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

    fun startAppStartSpan(startType: String) = lifecycleCallbacks.startAppStartSpan(startType)

    fun startAppStartPhase(phase: AppStartPhase) = lifecycleCallbacks.startAppStartPhase(phase)

    fun bugsnagPerformanceStart(instrumentAppStart: Boolean) =
        lifecycleCallbacks.bugsnagPerformanceStart(instrumentAppStart)

    companion object {
        /**
         * The token used to track the spans measuring the start of the app from when the
         * Application starts until the first Activity resumes.
         */
        val applicationToken = Any()
    }
}
