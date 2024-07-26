package com.bugsnag.android.performance

public fun interface SpanEndCallback{
    public fun onSpan(span: Span): Boolean
}