package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanProcessor
import java.util.UUID

/**
 * Creates Span objects suitable for use in testing.
 */
class TestSpanFactory {
    private var spanCount = 1L

    @Suppress("LongParameterList")
    fun newSpan(
        name: String = "Test/Span$spanCount",
        kind: SpanKind = SpanKind.INTERNAL,
        startTime: Long = spanCount,
        endTime: ((Long) -> Long)? = { it + 10L },
        traceId: UUID = UUID(0L, spanCount),
        spanId: Long = spanCount,
        processor: SpanProcessor
    ): Span {
        spanCount++
        return Span(name, kind, startTime, traceId, spanId, processor)
            .apply { if (endTime != null) end(endTime(startTime)) }
    }

    fun newSpans(
        count: Int,
        processor: SpanProcessor
    ): MutableList<Span> {
        val spans = mutableListOf<Span>()
        repeat(count) { spans.add(newSpan(processor = processor)) }
        return spans
    }
}
