package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanProcessor
import java.util.concurrent.ConcurrentLinkedQueue

class CollectingSpanProcessor : SpanProcessor {
    private val spans = ConcurrentLinkedQueue<Span>()

    fun toList(): List<Span> = spans.sortedBy { it.startTime }

    override fun onEnd(span: Span) {
        spans.add(span)
    }
}
