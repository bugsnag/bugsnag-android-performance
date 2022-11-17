package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.SystemClock
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanProcessor
import com.bugsnag.android.performance.ViewType
import java.net.URL
import java.util.UUID

internal typealias AttributeSource = (HasAttributes) -> Unit

class SpanFactory(
    private val spanProcessor: SpanProcessor,
    var spanAttributeSource: AttributeSource = {},
) {
    fun createCustomSpan(name: String, startTime: Long): Span =
        createSpan("Custom/$name", SpanKind.INTERNAL, startTime)

    fun createNetworkSpan(url: URL, verb: String, startTime: Long): Span {
        val verbUpper = verb.uppercase()
        val span = createSpan("HTTP/$verbUpper", SpanKind.CLIENT, startTime)
        span.attributes["http.url"] = url.toString()
        span.attributes["http.method"] = verbUpper
        return span
    }

    fun createViewLoadSpan(activity: Activity, startTime: Long): Span {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, startTime)
    }

    fun createViewLoadSpan(viewType: ViewType, viewName: String, startTime: Long): Span {
        val span = createSpan(
            "ViewLoad/${viewType.spanName}/$viewName",
            SpanKind.INTERNAL,
            startTime
        )

        span.attributes["bugsnag.span_category"] = "view_load"
        span.attributes["bugsnag.view.type"] = viewType.typeName
        span.attributes["bugsnag.view.name"] = viewName
        return span
    }

    fun createAppStartSpan(startType: String): Span =
        createSpan("AppStart/$startType", SpanKind.INTERNAL, SystemClock.elapsedRealtimeNanos())

    private fun createSpan(name: String, kind: SpanKind, startTime: Long): Span {
        val span = Span(name, kind, startTime, UUID.randomUUID(), processor = spanProcessor)
        spanAttributeSource(span)
        return span
    }
}
