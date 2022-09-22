package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.Span

fun interface SpanProcessor {
    fun onEnd(span: Span)
}
