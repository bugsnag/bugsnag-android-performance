package com.bugsnag.android.performance.test;

import com.bugsnag.android.performance.SpanKind;
import com.bugsnag.android.performance.internal.SpanCategory;
import com.bugsnag.android.performance.internal.SpanImpl;
import com.bugsnag.android.performance.internal.SpanProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import kotlin.jvm.functions.Function1;

public class TestSpanFactory {
    private long spanCount = 1L;

    final TestTimeoutExecutor timeoutExecutor = new TestTimeoutExecutor();

    public SpanImpl openSpan(SpanProcessor processor) {
        return newSpan(
                null,
                null,
                0L,
                null,
                null,
                spanCount++,
                0L,
                null,
                processor
        );
    }

    public SpanImpl namedSpan(String spanName) {
        return newSpan(
                spanName,
                null,
                System.currentTimeMillis(),
                null,
                null,
                0L,
                0L,
                null,
                NoopSpanProcessor.INSTANCE
        );
    }

    public SpanImpl newSpan(UUID traceId, long spanId) {
        return newSpan(
                null,
                null,
                System.currentTimeMillis(),
                null,
                traceId,
                spanId,
                0L,
                null,
                NoopSpanProcessor.INSTANCE
        );
    }

    public SpanImpl newSpan(
            String name,
            SpanKind kind,
            long startTime,
            Function1<Long, Long> endTime,
            UUID traceId,
            long spanId,
            long parentSpanId,
            String spanCategory,
            SpanProcessor processor
    ) {
        if (spanId <= 0L) spanId = spanCount++;
        if (name == null) name = "Test/Span" + spanId;
        if (kind == null) kind = SpanKind.INTERNAL;
        if (startTime <= 0L) startTime = spanCount;
        if (traceId == null) traceId = new UUID(0L, spanId);
        if (spanCategory == null) spanCategory = SpanCategory.CATEGORY_CUSTOM;

        SpanImpl span = new SpanImpl(
                name,
                spanCategory,
                kind,
                startTime,
                traceId,
                spanId,
                parentSpanId,
                true,
                timeoutExecutor,
                processor
        );

        if (endTime != null) {
            span.end(endTime.invoke(startTime));
        }

        return span;
    }

    public SpanImpl newSpan(SpanProcessor processor) {
        return newSpan(
                null,
                null,
                spanCount,
                t -> t + 10L,
                null,
                spanCount,
                0L,
                null,
                processor
        );
    }

    public List<SpanImpl> newSpans(int count, SpanProcessor processor) {
        List<SpanImpl> spans = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            spans.add(newSpan(processor));
        }
        return spans;
    }
}
