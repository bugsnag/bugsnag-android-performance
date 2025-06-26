package com.bugsnag.android.performance.internal

import android.app.Application
import android.os.Build
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.instrumentation.AbstractActivityLifecycleInstrumentation
import com.bugsnag.android.performance.internal.instrumentation.ActivityLifecycleInstrumentation
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState
import com.bugsnag.android.performance.internal.instrumentation.LegacyActivityInstrumentation
import com.bugsnag.android.performance.internal.processing.ForwardingSpanProcessor
import com.bugsnag.android.performance.internal.processing.ImmutableConfig
import com.bugsnag.android.performance.internal.processing.Tracer
import java.util.regex.Pattern

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InstrumentedAppState {
    internal val defaultAttributeSource = DefaultAttributeSource()

    public var spanProcessor: SpanProcessor = ForwardingSpanProcessor()
        private set

    public val spanTracker: SpanTracker = SpanTracker()

    public val spanFactory: SpanFactory = SpanFactory(spanProcessor, defaultAttributeSource)

    internal val startupTracker: AppStartTracker = AppStartTracker(spanTracker, spanFactory)

    public val autoInstrumentationCache: AutoInstrumentationCache = AutoInstrumentationCache()

    internal val activityInstrumentation = createActivityInstrumentation()

    public var tracePropagationUrls: Collection<Pattern> = emptySet()

    public lateinit var app: Application
        private set

    @get:JvmName("getConfig\$internal")
    internal var config: ImmutableConfig? = null
        private set

    internal fun attach(application: Application) {
        if (this::app.isInitialized) {
            return
        }

        app = application
        app.registerActivityLifecycleCallbacks(activityInstrumentation)

        spanFactory.attach(application)

        ForegroundState.addForegroundChangedCallback { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }

            startupTracker.isInBackground = !inForeground
        }
    }

    internal fun configure(configuration: ImmutableConfig): Tracer {
        config = configuration
        attach(configuration.application)

        val bootstrapSpanProcessor = spanProcessor
        val tracer = Tracer(configuration.spanEndCallbacks)

        configureLifecycleCallbacks(configuration)

        spanProcessor = tracer
        spanFactory.configure(
            tracer,
            configuration,
            configuration.spanStartCallbacks,
            configuration.networkRequestCallback,
            configuration.enabledMetrics,
        )

        tracePropagationUrls = configuration.tracePropagationUrls

        if (configuration.autoInstrumentAppStarts) {
            // redirect existing spanProcessor -> new Tracer
            (bootstrapSpanProcessor as? ForwardingSpanProcessor)?.forwardTo(spanProcessor)
            autoInstrumentationCache.configure(
                configuration.doNotEndAppStart,
                configuration.doNotAutoInstrument,
            )
        } else {
            // clear the contextStack to ensure that any new spans don't associate with
            // the discarded spans, this doesn't work if not on the main thread but
            // since that will be the most common case and there is no harm in doing it
            // on another thread - this is not conditional
            SpanContext.Storage.DEFAULT_STORAGE?.clear()

            (bootstrapSpanProcessor as? ForwardingSpanProcessor)?.discard()
        }

        ForegroundState.addForegroundChangedCallback { inForeground ->
            if (!inForeground) {
                tracer.forceCurrentBatch()
            }
        }

        return tracer
    }

    private fun createActivityInstrumentation(): AbstractActivityLifecycleInstrumentation {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityLifecycleInstrumentation(
                spanTracker,
                spanFactory,
                startupTracker,
                autoInstrumentationCache,
            )
        } else {
            LegacyActivityInstrumentation(
                spanTracker,
                spanFactory,
                startupTracker,
                autoInstrumentationCache,
            )
        }
    }

    private fun configureLifecycleCallbacks(configuration: ImmutableConfig) {
        activityInstrumentation.apply {
            openLoadSpans = configuration.autoInstrumentActivities != AutoInstrument.OFF
            closeLoadSpans = configuration.autoInstrumentActivities == AutoInstrument.FULL
        }
    }

    public fun onBugsnagPerformanceStart() {
        startupTracker.onBugsnagPerformanceStart()
    }
}
