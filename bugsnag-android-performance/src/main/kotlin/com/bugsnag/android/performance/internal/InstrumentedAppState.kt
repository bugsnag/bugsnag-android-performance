package com.bugsnag.android.performance.internal

import android.app.Application
import android.os.Build
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.framerate.FramerateMetricsSource
import com.bugsnag.android.performance.internal.instrumentation.AbstractActivityCallbacks
import com.bugsnag.android.performance.internal.instrumentation.ActivityCallbacks
import com.bugsnag.android.performance.internal.instrumentation.ActivityInstrumentation
import com.bugsnag.android.performance.internal.instrumentation.AppStartInstrumentation
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

    public val autoInstrumentationCache: AutoInstrumentationCache = AutoInstrumentationCache()

    internal val startInstrumentation: AppStartInstrumentation =
        AppStartInstrumentation(spanTracker, spanFactory)

    internal val activityInstrumentation = ActivityInstrumentation(
        spanTracker,
        spanFactory,
        startInstrumentation,
        autoInstrumentationCache,
    )

    private val activityCallbacks = createActivityCallbacks(
        startInstrumentation,
        activityInstrumentation,
        autoInstrumentationCache,
    )

    public var tracePropagationUrls: Collection<Pattern> = emptySet()

    public lateinit var app: Application
        private set

    private var framerateMetricsSource: FramerateMetricsSource? = null

    @get:JvmName("getConfig\$internal")
    internal var config: ImmutableConfig? = null
        private set

    internal fun attach(application: Application) {
        if (this::app.isInitialized) {
            return
        }

        app = application
        app.registerActivityLifecycleCallbacks(activityCallbacks)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            framerateMetricsSource = FramerateMetricsSource()
            spanFactory.framerateMetricsSource = framerateMetricsSource
            app.registerActivityLifecycleCallbacks(framerateMetricsSource)
        }

        ForegroundState.addForegroundChangedCallback { inForeground ->
            defaultAttributeSource.update {
                it.copy(isInForeground = inForeground)
            }

            startInstrumentation.isInBackground = !inForeground
        }
    }

    internal fun configure(configuration: ImmutableConfig): Tracer {
        config = configuration
        attach(configuration.application)

        val bootstrapSpanProcessor = spanProcessor
        val tracer = Tracer(configuration.spanEndCallbacks)

        autoInstrumentationCache.configure(configuration)

        spanProcessor = tracer
        spanFactory.configure(
            tracer,
            configuration,
            configuration.networkRequestCallback,
        )

        tracePropagationUrls = configuration.tracePropagationUrls

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

        if (!configuration.autoInstrumentRendering && framerateMetricsSource != null) {
            spanFactory.framerateMetricsSource = null
            app.unregisterActivityLifecycleCallbacks(framerateMetricsSource)
            framerateMetricsSource = null
        }

        ForegroundState.addForegroundChangedCallback { inForeground ->
            if (!inForeground) {
                tracer.forceCurrentBatch()
            }
        }

        return tracer
    }

    private fun createActivityCallbacks(
        startInstrumentation: AppStartInstrumentation,
        activityInstrumentation: ActivityInstrumentation,
        autoInstrumentationCache: AutoInstrumentationCache,
    ): AbstractActivityCallbacks {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCallbacks(
                startInstrumentation,
                activityInstrumentation,
                autoInstrumentationCache,
            )
        } else {
            LegacyActivityInstrumentation(
                startInstrumentation,
                activityInstrumentation,
                autoInstrumentationCache,
            )
        }
    }

    public fun onBugsnagPerformanceStart() {
        startInstrumentation.onBugsnagPerformanceStart()
    }
}
