package com.bugsnag.android.performance.internal

import android.app.Activity
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.NetworkRequestInfo
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.ViewType
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import java.util.UUID

internal typealias AttributeSource = (target: SpanImpl) -> Unit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanFactory(
    public var spanProcessor: SpanProcessor,
    public val spanAttributeSource: AttributeSource = {},
) {
    public var networkRequestCallback: NetworkRequestInstrumentationCallback? = null
    internal var attributeLimits: AttributeLimits? = null

    public fun createCustomSpan(
        name: String,
        options: SpanOptions = SpanOptions.DEFAULTS,
        spanProcessor: SpanProcessor = this.spanProcessor,
    ): SpanImpl {
        val isFirstClass = options.isFirstClass != false
        val span = createSpan(name, SpanKind.INTERNAL, SpanCategory.CUSTOM, options, spanProcessor)
        span.attributes["bugsnag.span.first_class"] = isFirstClass
        return span
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
                options,
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
            options,
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
            options,
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
            SpanOptions.within(null),
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
            SpanOptions.within(appStartContext),
            spanProcessor,
        )

        span.attributes["bugsnag.phase"] = "FrameworkLoad"

        return span
    }

    private fun createSpan(
        name: String,
        kind: SpanKind,
        category: SpanCategory,
        options: SpanOptions,
        spanProcessor: SpanProcessor,
    ): SpanImpl {
        val parentContext = options.parentContext?.takeIf { it.traceId.isValidTraceId() }

        val span = SpanImpl(
            name = name,
            kind = kind,
            category = category,
            startTime = options.startTime,
            traceId = parentContext?.traceId ?: UUID.randomUUID(),
            parentSpanId = parentContext?.spanId ?: 0L,
            processor = spanProcessor,
            makeContext = options.makeContext,
            attributeLimits = attributeLimits,
        )

        spanAttributeSource(span)

        NotifierIntegration.onSpanStarted(span)

        return span
    }

    private fun UUID.isValidTraceId() = mostSignificantBits != 0L || leastSignificantBits != 0L
}
