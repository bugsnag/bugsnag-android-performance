package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

fun interface SpanProcessor {
    fun onEnd(span: Span)
}
