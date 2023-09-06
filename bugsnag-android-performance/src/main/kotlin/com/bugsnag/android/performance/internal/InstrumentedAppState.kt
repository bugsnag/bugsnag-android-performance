package com.bugsnag.android.performance.internal

import android.app.Application
import android.os.Build
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.instrumentation.AbstractActivityLifecycleInstrumentation
import com.bugsnag.android.performance.internal.instrumentation.ActivityLifecycleInstrumentation
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import com.bugsnag.android.performance.internal.instrumentation.LegacyActivityInstrumentation
import com.bugsnag.android.performance.internal.processing.ForwardingSpanProcessor
import com.bugsnag.android.performance.internal.processing.Tracer

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    var spanProcessor: SpanProcessor = ForwardingSpanProcessor()
        private set

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(spanProcessor, defaultAttributeSource)

    val startupTracker = AppStartTracker(spanTracker, spanFactory)

    internal val activityInstrumentation = createActivityInstrumentation()

    lateinit var app: Application
        private set

    internal fun attach(application: Application) {
        if (this::app.isInitialized) {
            return
        }

        app = application
        app.registerActivityLifecycleCallbacks(activityInstrumentation)

        ForegroundState.addForegroundChangedCallback { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }
        }
    }

    internal fun configure(configuration: ImmutableConfig): Tracer {
        attach(configuration.application)

        val bootstrapSpanProcessor = spanProcessor
        val tracer = Tracer()

        configureLifecycleCallbacks(configuration)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(tracer))

        spanProcessor = tracer
        spanFactory.spanProcessor = tracer
        spanFactory.networkRequestCallback = configuration.networkRequestCallback

        if (configuration.autoInstrumentAppStarts) {
            // redirect existing spanProcessor -> new Tracer
            (bootstrapSpanProcessor as? ForwardingSpanProcessor)?.forwardTo(spanProcessor)
        } else {
            // clear the contextStack to ensure that any new spans don't associate with
            // the discarded spans, this doesn't work if not on the main thread but
            // since that will be the most common case and there is no harm in doing it
            // on another thread - this is not conditional
            SpanContext.contextStack.clear()

            (bootstrapSpanProcessor as? ForwardingSpanProcessor)?.discard()
        }

        return tracer
    }

    private fun createActivityInstrumentation(): AbstractActivityLifecycleInstrumentation {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityLifecycleInstrumentation(
                spanTracker,
                spanFactory,
                startupTracker,
            )
        } else {
            LegacyActivityInstrumentation(
                spanTracker,
                spanFactory,
                startupTracker,
            )
        }
    }

    private fun configureLifecycleCallbacks(configuration: ImmutableConfig) {
        activityInstrumentation.apply {
            openLoadSpans = configuration.autoInstrumentActivities != AutoInstrument.OFF
            closeLoadSpans = configuration.autoInstrumentActivities == AutoInstrument.FULL
        }
    }

    fun onBugsnagPerformanceStart() {
        startupTracker.onBugsnagPerformanceStart()
    }

    companion object {
        /**
         * The token used to track the spans measuring the start of the app from when the
         * Application starts until the first Activity resumes.
         */
        val applicationToken = Any()
    }
}
