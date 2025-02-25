package com.bugsnag.android.performance.internal

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.NetworkRequestInfo
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.ViewType
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.metrics.MetricsContainer
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import com.bugsnag.android.performance.internal.processing.SpanTaskWorker
import java.util.UUID

internal typealias AttributeSource = (target: SpanImpl) -> Unit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanFactory internal constructor(
    public var spanProcessor: SpanProcessor,
    public val spanAttributeSource: AttributeSource,
    private val spanTaskWorker: SpanTaskWorker = SpanTaskWorker(),
    internal val metricsContainer: MetricsContainer = MetricsContainer(spanTaskWorker),
) {

    public var networkRequestCallback: NetworkRequestInstrumentationCallback? = null

    internal var attributeLimits: AttributeLimits? = null

    public constructor(
        spanProcessor: SpanProcessor,
        spanAttributeSource: AttributeSource = {},
    ) : this(
        spanProcessor,
        spanAttributeSource,
        SpanTaskWorker(),
    )

    internal fun attach(application: Application) {
        metricsContainer.attach(application)
        spanTaskWorker.start()
    }

    internal fun configure(
        spanProcessor: SpanProcessor,
        attributeLimits: AttributeLimits,
        networkRequestCallback: NetworkRequestInstrumentationCallback?,
        enabledMetrics: EnabledMetrics,
    ) {
        this.spanProcessor = spanProcessor
        this.attributeLimits = attributeLimits
        this.networkRequestCallback = networkRequestCallback

        metricsContainer.configure(enabledMetrics)
    }

    @JvmOverloads
    public fun createCustomSpan(
        name: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        return createSpan(
            name,
            SpanKind.INTERNAL,
            SpanCategory.CUSTOM,
            options.startTime,
            options.parentContext,
            options.isFirstClass != false,
            options.makeContext,
            options.spanMetrics,
            spanProcessor,
        )
    }

    public fun createNetworkSpan(
        url: String,
        verb: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl? {
        val reqInfo = NetworkRequestInfo(url)
        networkRequestCallback?.onNetworkRequest(reqInfo)
        reqInfo.url?.let { resultUrl ->
            val verbUpper = verb.uppercase()
            val span = createSpan(
                "[HTTP/$verbUpper]",
                SpanKind.CLIENT,
                SpanCategory.NETWORK,
                options.startTime,
                options.parentContext,
                options.isFirstClass,
                options.makeContext,
                options.spanMetrics,
                spanProcessor,
            )
            span.attributes["http.url"] = resultUrl
            span.attributes["http.method"] = verbUpper
            return span
        }
        return null
    }

    public fun createViewLoadSpan(
        activity: Activity,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, options, spanProcessor)
    }

    public fun createViewLoadSpan(
        viewType: ViewType,
        viewName: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val isFirstClass = options.isFirstClass
            ?: SpanContext.contextStack.noSpansMatch { it.category == SpanCategory.VIEW_LOAD }

        val span = createSpan(
            "[ViewLoad/${viewType.spanName}]$viewName",
            SpanKind.INTERNAL,
            SpanCategory.VIEW_LOAD,
            options.startTime,
            options.parentContext,
            options.isFirstClass,
            options.makeContext,
            options.spanMetrics,
            spanProcessor,
        )

        span.attributes["bugsnag.view.type"] = viewType.typeName
        span.attributes["bugsnag.view.name"] = viewName
        span.attributes["bugsnag.span.first_class"] = isFirstClass

        val appStart = SpanContext.contextStack.findSpan { it.category == SpanCategory.APP_START }
        if (appStart != null && appStart.attributes["bugsnag.app_start.first_view_name"] == null) {
            appStart.attributes["bugsnag.view.type"] = viewType.typeName
            appStart.attributes["bugsnag.app_start.first_view_name"] = viewName
        }

        return span
    }

    public fun createViewLoadPhaseSpan(
        viewName: String,
        viewType: ViewType,
        phase: ViewLoadPhase,
        options: SpanOptions,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val phaseName = phase.phaseNameFor(viewType)
        val span = createSpan(
            "[ViewLoadPhase/$phaseName]$viewName",
            SpanKind.INTERNAL,
            SpanCategory.VIEW_LOAD_PHASE,
            options.startTime,
            options.parentContext,
            options.isFirstClass,
            options.makeContext,
            options.spanMetrics,
            spanProcessor,
        )

        span.attributes["bugsnag.view.name"] = viewName
        span.attributes["bugsnag.view.type"] = viewType.typeName
        span.attributes["bugsnag.phase"] = phaseName

        return span
    }

    public fun createViewLoadPhaseSpan(
        activity: Activity,
        phase: ViewLoadPhase,
        options: SpanOptions,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        return createViewLoadPhaseSpan(
            activity::class.java.simpleName,
            ViewType.ACTIVITY,
            phase,
            options,
            spanProcessor,
        )
    }

    public fun createAppStartSpan(
        startType: String,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val span = createSpan(
            "[AppStart/Android$startType]",
            SpanKind.INTERNAL,
            SpanCategory.APP_START,
            SystemClock.elapsedRealtimeNanos(),
            null,
            isFirstClass = true,
            makeContext = true,
            SpanMetrics(rendering = true),
            spanProcessor,
        )

        span.attributes["bugsnag.app_start.type"] = startType.lowercase()

        return span
    }

    public fun createAppStartPhaseSpan(
        phase: AppStartPhase,
        appStartContext: SpanContext,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val span = createSpan(
            "[AppStartPhase/${phase.phaseName}]",
            SpanKind.INTERNAL,
            SpanCategory.APP_START_PHASE,
            SystemClock.elapsedRealtimeNanos(),
            appStartContext,
            isFirstClass = false,
            makeContext = true,
            null,
            spanProcessor,
        )

        span.attributes["bugsnag.phase"] = "FrameworkLoad"

        return span
    }

    private fun createSpan(
        name: String,
        kind: SpanKind,
        category: SpanCategory,
        startTime: Long,
        parentContext: SpanContext?,
        isFirstClass: Boolean?,
        makeContext: Boolean,
        spanMetrics: SpanMetrics?,
        spanProcessor: SpanProcessor,
    ): SpanImpl {
        val parent = parentContext?.takeIf { it.traceId.isValidTraceId() }

        val metrics = metricsContainer.createSpanMetricsSnapshot(isFirstClass == true, spanMetrics)
        val span = SpanImpl(
            name = name,
            category = category,
            kind = kind,
            startTime = startTime,
            traceId = parent?.traceId ?: UUID.randomUUID(),
            parentSpanId = parent?.spanId ?: 0L,
            makeContext = makeContext,
            attributeLimits = attributeLimits,
            metrics = metrics,
            processor = spanProcessor,
            timeoutExecutor = spanTaskWorker,
        )

        if (isFirstClass != null) {
            span.attributes["bugsnag.span.first_class"] = isFirstClass
        }

        spanAttributeSource(span)

        NotifierIntegration.onSpanStarted(span)

        return span
    }

    private fun UUID.isValidTraceId() = mostSignificantBits != 0L || leastSignificantBits != 0L
}
