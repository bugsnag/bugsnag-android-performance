package com.bugsnag.android.performance.internal

import android.app.Application
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(BugsnagPerformance.tracer, defaultAttributeSource)
    val activityCallbacks = createLifecycleCallbacks()

    lateinit var app: Application
        private set

    internal fun configure(configuration: ImmutableConfig) {
        this.app = configuration.application

        configureLifecycleCallbacks(configuration)

        app.registerActivityLifecycleCallbacks(activityCallbacks)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(BugsnagPerformance.tracer))
    }

    private fun createLifecycleCallbacks(): PerformanceLifecycleCallbacks {
        return PerformanceLifecycleCallbacks(spanTracker, spanFactory) { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }
        }
    }

    private fun configureLifecycleCallbacks(configuration: ImmutableConfig) {
        activityCallbacks.apply {
            openLoadSpans = configuration.autoInstrumentActivities != AutoInstrument.OFF
            closeLoadSpans = configuration.autoInstrumentActivities == AutoInstrument.FULL
            instrumentAppStart = configuration.autoInstrumentAppStarts
        }
    }
}
