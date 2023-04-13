package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
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
        parentSpanId: Long = 0L,
        processor: SpanProcessor,
    ): SpanImpl {
        spanCount++
        return SpanImpl(
            name,
            SpanCategory.CUSTOM,
            kind,
            startTime,
            traceId,
            spanId,
            parentSpanId,
            processor,
            true,
        )
            .apply { if (endTime != null) end(endTime(startTime)) }
    }

    fun newSpans(
        count: Int,
        processor: SpanProcessor,
    ): MutableList<SpanImpl> {
        val spans = mutableListOf<SpanImpl>()
        repeat(count) { spans.add(newSpan(processor = processor)) }
        return spans
    }
}
