package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

public fun interface SpanProcessor {
    public fun onEnd(span: Span)
}
