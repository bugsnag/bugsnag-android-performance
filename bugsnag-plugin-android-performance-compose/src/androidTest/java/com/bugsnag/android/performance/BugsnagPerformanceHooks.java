package com.bugsnag.android.performance;

import com.bugsnag.android.performance.internal.SpanImpl;
import com.bugsnag.android.performance.internal.processing.BatchingSpanProcessor;
import com.bugsnag.android.performance.internal.processing.ForwardingSpanProcessor;

import java.util.Collection;

class BugsnagPerformanceHooks {
    static Collection<SpanImpl> takeCurrentBatch() {
        ForwardingSpanProcessor forwardingSpanProcessor =
                (ForwardingSpanProcessor) BugsnagPerformance
                        .INSTANCE
                        .getInstrumentedAppState$internal()
                        .getSpanProcessor();

        // forward the current batch to the new BatchingSpanProcessor
        BatchingSpanProcessor target = new BatchingSpanProcessor();
        forwardingSpanProcessor.forwardTo(target);
        return target.takeBatch();
    }
}
