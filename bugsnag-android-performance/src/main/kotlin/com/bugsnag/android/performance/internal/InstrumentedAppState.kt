package com.bugsnag.android.performance.internal

import android.app.Application

import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(BugsnagPerformance.tracer, defaultAttributeSource)
    val platformCallbacks = createLifecycleCallbacks()

    lateinit var app: Application
        private set

    internal fun configure(application: Application, configuration: PerformanceConfiguration) {
        this.app = application

        configureLifecycleCallbacks(configuration)

        app.registerActivityLifecycleCallbacks(platformCallbacks)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(BugsnagPerformance.tracer))
    }

    private fun createLifecycleCallbacks(): PerformanceLifecycleCallbacks {
        return PerformanceLifecycleCallbacks(spanTracker, spanFactory) { inForeground ->
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
}
