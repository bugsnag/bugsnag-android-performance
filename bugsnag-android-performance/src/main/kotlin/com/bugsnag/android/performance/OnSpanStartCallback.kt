package com.bugsnag.android.performance

/**
 * Called whenever a [Span] is started. This callback can be used to setup the span before it is
 * used (such as setting attributes). These callbacks are considered to be "inside" the span, so
 * any time taken to run the callback is counted towards the span's duration.
 *
 * @see OnSpanEndCallback
 * @see [PerformanceConfiguration.addOnSpanStartCallback]
 */
public fun interface OnSpanStartCallback {
    /**
     * Called when a span is started. Called after the primary attributes are added, but before
     * the span is returned.
     */
    public fun onSpanStart(span: Span)
}
