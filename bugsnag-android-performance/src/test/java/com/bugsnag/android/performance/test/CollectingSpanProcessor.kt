package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanProcessor

class CollectingSpanProcessor : SpanProcessor {
    private val spans = ArrayList<Span>()

    fun toList(): List<Span> = spans.sortedBy { it.startTime }

    override fun onEnd(span: Span) {
        // unwind all of the Spans in the chain
        var s: Span? = span
        while (s != null) {
            spans.add(s)
            s = s.previous
        }
    }
}
