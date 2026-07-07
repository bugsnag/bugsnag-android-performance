package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import java.util.UUID

class TestSpanFactory {
    private var spanCount = 1L

    internal val timeoutExecutor = TestTimeoutExecutor()

    fun openSpan(processor: SpanProcessor): SpanImpl =
        newSpan(
            name = null,
            kind = null,
            startTime = 0L,
            endTime = null,
            traceId = null,
            spanId = spanCount++,
            parentSpanId = 0L,
            spanCategory = null,
            processor = processor,
        )

    fun namedSpan(spanName: String): SpanImpl =
        newSpan(
            name = spanName,
            kind = null,
            startTime = System.currentTimeMillis(),
            endTime = null,
            traceId = null,
            spanId = 0L,
            parentSpanId = 0L,
            spanCategory = null,
            processor = NoopSpanProcessor.INSTANCE,
        )

    fun newSpan(
        traceId: UUID,
        spanId: Long,
    ): SpanImpl =
        newSpan(
            name = null,
            kind = null,
            startTime = System.currentTimeMillis(),
            endTime = null,
            traceId = traceId,
            spanId = spanId,
            parentSpanId = 0L,
            spanCategory = null,
            processor = NoopSpanProcessor.INSTANCE,
        )

    @Suppress("LongParameterList")
    fun newSpan(
        name: String? = null,
        kind: SpanKind? = null,
        startTime: Long = 0L,
        endTime: ((Long) -> Long)? = null,
        traceId: UUID? = null,
        spanId: Long = 0L,
        parentSpanId: Long = 0L,
        spanCategory: SpanCategory? = null,
        processor: SpanProcessor = NoopSpanProcessor.INSTANCE,
    ): SpanImpl {
        val resolvedSpanId = if (spanId <= 0L) spanCount++ else spanId
        val resolvedName = name ?: "Test/Span$resolvedSpanId"
        val resolvedKind = kind ?: SpanKind.INTERNAL
        val resolvedStartTime = if (startTime <= 0L) spanCount else startTime
        val resolvedTraceId = traceId ?: UUID(0L, resolvedSpanId)
        val resolvedCategory = spanCategory ?: SpanCategory.CUSTOM

        val span =
            SpanImpl(
                resolvedName,
                resolvedCategory.category,
                resolvedKind,
                resolvedStartTime,
                resolvedTraceId,
                resolvedSpanId,
                parentSpanId,
                true,
                timeoutExecutor,
                processor,
            )

        if (endTime != null) {
            span.end(endTime.invoke(resolvedStartTime))
        }

        return span
    }

    fun newSpans(
        count: Int,
        processor: SpanProcessor,
    ): List<SpanImpl> {
        return List(count) { newSpan(processor = processor) }
    }
}
