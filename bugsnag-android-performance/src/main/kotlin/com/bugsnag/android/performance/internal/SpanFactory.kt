package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.SystemClock
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.ViewType
import java.net.URL
import java.util.UUID

internal typealias AttributeSource = (target: HasAttributes) -> Unit

class SpanFactory(
    private val spanProcessor: SpanProcessor,
    val spanAttributeSource: AttributeSource = {},
) {
    fun createCustomSpan(name: String, startTime: Long): SpanImpl =
        createSpan("Custom/$name", SpanKind.INTERNAL, startTime)

    fun createNetworkSpan(url: URL, verb: String, startTime: Long): SpanImpl {
        val verbUpper = verb.uppercase()
        val span = createSpan("HTTP/$verbUpper", SpanKind.CLIENT, startTime)
        span.setAttribute("bugsnag.span.category", "network")
        span.setAttribute("http.url", url.toString())
        span.setAttribute("http.method", verbUpper)
        return span
    }

    fun createViewLoadSpan(activity: Activity, startTime: Long): SpanImpl {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, startTime)
    }

    fun createViewLoadSpan(viewType: ViewType, viewName: String, startTime: Long): SpanImpl {
        val span = createSpan(
            "ViewLoaded/${viewType.spanName}/$viewName",
            SpanKind.INTERNAL,
            startTime
        )

        span.setAttribute("bugsnag.span.category", "view_load")
        span.setAttribute("bugsnag.view.type", viewType.typeName)
        span.setAttribute("bugsnag.view.name", viewName)
        return span
    }

    fun createAppStartSpan(startType: String): SpanImpl =
        createSpan(
            "AppStart/$startType",
            SpanKind.INTERNAL,
            SystemClock.elapsedRealtimeNanos()
        ).apply {
            setAttribute("bugsnag.span.category", "app_start")
            setAttribute("bugsnag.app_start.type", startType.lowercase())
        }

    private fun createSpan(
        name: String,
        kind: SpanKind,
        startTime: Long,
        spanContext: SpanContext = SpanContext.current
    ): SpanImpl {
        val span = SpanImpl(
            name = name,
            kind = kind,
            startTime = startTime,
            traceId = spanContext.traceId.takeIf { it.isValidTraceId() } ?: UUID.randomUUID(),
            parentSpanId = spanContext.spanId,
            processor = spanProcessor
        )

        spanAttributeSource(span)
        return span
    }

    private fun UUID.isValidTraceId() = mostSignificantBits != 0L && leastSignificantBits != 0L
}
