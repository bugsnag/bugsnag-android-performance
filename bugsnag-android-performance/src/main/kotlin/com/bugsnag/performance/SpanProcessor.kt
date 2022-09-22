package com.bugsnag.performance

fun interface SpanProcessor {
    fun onEnd(span: Span)
}
