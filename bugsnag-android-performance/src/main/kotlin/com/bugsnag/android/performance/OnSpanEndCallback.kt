package com.bugsnag.android.performance

/**
 * Called whenever a [Span] is ending, but before it has been enqueued for delivery. These callbacks
 * can modify the attributes of a span or cause it to be discarded between the time [Span.end] is called
 * and when the span is enqueued for delivery.
 *
 * @see OnSpanStartCallback
 * @see [PerformanceConfiguration.addOnSpanEndCallback]
 */
public fun interface OnSpanEndCallback {
    /**
     * Called when a span is ending. This callback can modify the attributes of a span or cause it
     * to be discarded (by returning `false`).
     *
     * @param span the span that is ending
     * @return `true` if the span should be delivered, `false` if it should be discarded
     */
    public fun onSpanEnd(span: Span): Boolean
}
