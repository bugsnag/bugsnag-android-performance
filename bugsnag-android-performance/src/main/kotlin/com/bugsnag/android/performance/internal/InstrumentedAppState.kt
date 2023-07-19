package com.bugsnag.android.performance.internal

import android.app.Application
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.processing.ForwardingSpanProcessor
import com.bugsnag.android.performance.internal.processing.Tracer
import kotlin.math.max

class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    var spanProcessor: SpanProcessor = ForwardingSpanProcessor()
        private set

    val spanTracker = SpanTracker()

    val spanFactory = SpanFactory(spanProcessor, defaultAttributeSource)

    val startupTracker = AppStartTracker(spanTracker, spanFactory)

    val lifecycleCallbacks = createLifecycleCallbacks()

    lateinit var app: Application
        private set

    internal fun configure(configuration: ImmutableConfig): Tracer {
        this.app = configuration.application

        val bootstrapSpanProcessor = spanProcessor
        val tracer = Tracer()

        configureLifecycleCallbacks(configuration)

        app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        app.registerComponentCallbacks(PerformanceComponentCallbacks(tracer))

        spanProcessor = tracer
        spanFactory.spanProcessor = tracer

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

    private fun createLifecycleCallbacks(): PerformanceLifecycleCallbacks {
        return PerformanceLifecycleCallbacks(
            spanTracker,
            spanFactory,
            startupTracker,
        ) { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }
        }
    }

    private fun configureLifecycleCallbacks(configuration: ImmutableConfig) {
        lifecycleCallbacks.apply {
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
