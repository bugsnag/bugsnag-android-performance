package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanProcessor

object NoopSpanProcessor : SpanProcessor {
    override fun onEnd(span: Span) = Unit // discard all spans
}
