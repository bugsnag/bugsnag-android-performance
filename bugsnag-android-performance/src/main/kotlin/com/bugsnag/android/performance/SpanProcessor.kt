package com.bugsnag.android.performance

fun interface SpanProcessor {
    fun onEnd(span: Span)
}
