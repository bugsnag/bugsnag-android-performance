package com.bugsnag.android.performance.internal

import android.app.Activity
import android.os.SystemClock
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.ViewType
import java.net.URL
import java.util.UUID

internal typealias AttributeSource = (target: HasAttributes) -> Unit

class SpanFactory(
    private val spanProcessor: SpanProcessor,
    val spanAttributeSource: AttributeSource = {},
) {
    fun createCustomSpan(name: String, startTime: Long): Span =
        createSpan("Custom/$name", SpanKind.INTERNAL, startTime)

    fun createNetworkSpan(url: URL, verb: String, startTime: Long): Span {
        val verbUpper = verb.uppercase()
        val span = createSpan("HTTP/$verbUpper", SpanKind.CLIENT, startTime)
        span.setAttribute("bugsnag.span_category", "network")
        span.setAttribute("http.url", url.toString())
        span.setAttribute("http.method", verbUpper)
        return span
    }

    fun createViewLoadSpan(activity: Activity, startTime: Long): Span {
        val activityName = activity::class.java.simpleName
        return createViewLoadSpan(ViewType.ACTIVITY, activityName, startTime)
    }

    fun createViewLoadSpan(viewType: ViewType, viewName: String, startTime: Long): Span {
        val span = createSpan(
            "ViewLoaded/${viewType.spanName}/$viewName",
            SpanKind.INTERNAL,
            startTime
        )

        span.setAttribute("bugsnag.span_category", "view_load")
        span.setAttribute("bugsnag.view.type", viewType.typeName)
        span.setAttribute("bugsnag.view.name", viewName)
        return span
    }

    fun createAppStartSpan(startType: String): Span =
        createSpan("AppStart/$startType", SpanKind.INTERNAL, SystemClock.elapsedRealtimeNanos()).apply {
            setAttribute("bugsnag.span_category", "app_start")
            setAttribute("bugsnag.app_start.type", startType.lowercase())
        }

    private fun createSpan(name: String, kind: SpanKind, startTime: Long): Span {
        val span = Span(name, kind, startTime, UUID.randomUUID(), processor = spanProcessor)
        spanAttributeSource(span)
        return span
    }
}
