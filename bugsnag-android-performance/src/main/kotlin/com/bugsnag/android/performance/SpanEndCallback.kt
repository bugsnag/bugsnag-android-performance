package com.bugsnag.android.performance

/**
 * Called whenever a [Span] is ending, but before it has been enqueued for delivery. These callbacks
 * can modify the attributes of a span or cause it to be discarded between the time [Span.end] is called
 * and when the span is enqueued for delivery.
 */
public fun interface SpanEndCallback {
    public fun onSpanEnd(span: Span): Boolean
}
