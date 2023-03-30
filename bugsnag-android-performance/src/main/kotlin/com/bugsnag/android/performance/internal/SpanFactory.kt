package com.bugsnag.android.performance.internal

import android.app.Activity
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.ViewType
import java.net.URL
import java.util.UUID

internal typealias AttributeSource = (target: HasAttributes) -> Unit

class SpanFactory(
    private val spanProcessor: SpanProcessor,
    val spanAttributeSource: AttributeSource = {},
) {
    fun createCustomSpan(name: String, options: SpanOptions = SpanOptions.DEFAULTS): SpanImpl {
        val isFirstClass = options.isFirstClass
        val span = createSpan(name, SpanKind.INTERNAL, options)
        span.setAttribute("bugsnag.span.first_class", isFirstClass)
        return span
    }

    fun createNetworkSpan(url: URL, verb: String, options: SpanOptions = SpanOptions.DEFAULTS): SpanImpl {
        val verbUpper = verb.uppercase()
        val span = createSpan("[HTTP/$verbUpper]", SpanKind.CLIENT, options)
        span.setAttribute("bugsnag.span.category", "network")
        span.setAttribute("http.url", url.toString())
        span.setAttribute("http.method", verbUpper)
        return span
    }

    fun createViewLoadSpan(activity: Activity, options: SpanOptions = SpanOptions.DEFAULTS): SpanImpl {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, options)
    }

    fun createViewLoadSpan(viewType: ViewType, viewName: String,
                           options: SpanOptions = SpanOptions.DEFAULTS
    ): SpanImpl {
        val isFirstClass = options.isFirstClass
        val span = createSpan(
            "[ViewLoad/${viewType.spanName}]$viewName",
            SpanKind.INTERNAL,
            options
        )

        span.setAttribute("bugsnag.span.category", "view_load")
        span.setAttribute("bugsnag.view.type", viewType.typeName)
        span.setAttribute("bugsnag.view.name", viewName)
        span.setAttribute("bugsnag.span.first_class", isFirstClass)
        return span
    }

    fun createAppStartSpan(startType: String): SpanImpl =
        createSpan(
            "[AppStart/$startType]",
            SpanKind.INTERNAL
        ).apply {
            setAttribute("bugsnag.span.category", "app_start")
            setAttribute("bugsnag.app_start.type", startType.lowercase())
        }

    private fun createSpan(
        name: String,
        kind: SpanKind,
        options: SpanOptions = SpanOptions.DEFAULTS
    ): SpanImpl {
        val span = SpanImpl(
            name = name,
            kind = kind,
            startTime = options.startTime,
            traceId = options.parentContext?.traceId?.takeIf { it.isValidTraceId() } ?: UUID.randomUUID(),
            parentSpanId = options.parentContext?.spanId ?: 0L,
            processor = spanProcessor,
            makeContext = options.makeContext
        )

        spanAttributeSource(span)

        return span
    }

    private fun UUID.isValidTraceId() = mostSignificantBits != 0L && leastSignificantBits != 0L
}
