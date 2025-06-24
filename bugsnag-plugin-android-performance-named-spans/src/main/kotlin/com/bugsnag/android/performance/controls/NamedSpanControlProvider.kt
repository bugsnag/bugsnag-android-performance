package com.bugsnag.android.performance.controls

import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.processing.Timeout
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class NamedSpanControlProvider(
    private val timeoutExecutor: TimeoutExecutor,
    private val timeout: Long,
    private val timeoutUnit: TimeUnit,
    private val onSpanTimeoutCallback: NamedSpanControlsPlugin.OnSpanTimeoutCallback? = null,
) : SpanControlProvider<Span>,
    OnSpanStartCallback,
    OnSpanEndCallback {
    private val spansByName = ConcurrentHashMap<String, SpanRef>()

    override fun <Q : SpanQuery<Span>> get(query: Q): Span? {
        if (query is NamedSpanQuery) {
            return spansByName[query.name]?.span
        }

        return null
    }

    override fun onSpanStart(span: Span) {
        val spanRef = SpanRef(span)
        spansByName[span.name] = spanRef
        timeoutExecutor.scheduleTimeout(spanRef)
    }

    override fun onSpanEnd(span: Span): Boolean {
        val ref = spansByName[span.name]
        if (ref?.span == span) {
            spansByName.remove(span.name, ref)
        }

        return true
    }

    private inner class SpanRef(val span: Span) : Timeout {
        override val target: Long = System.currentTimeMillis() + timeoutUnit.toMillis(timeout)

        override fun run() {
            onSpanEnd(span)
            onSpanTimeoutCallback?.onSpanTimeout(span)
        }
    }
}
