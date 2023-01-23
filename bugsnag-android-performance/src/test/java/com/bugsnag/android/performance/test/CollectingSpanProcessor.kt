package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import java.util.concurrent.ConcurrentLinkedQueue

class CollectingSpanProcessor : SpanProcessor {
    private val spans = ConcurrentLinkedQueue<SpanImpl>()

    fun toList(): List<SpanImpl> = spans.sortedBy { it.startTime }

    override fun onEnd(span: Span) {
        spans.add(span as SpanImpl)
    }
}
