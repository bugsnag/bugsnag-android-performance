/**
 * Copyright (c) 2023 Bugsnag
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bugsnag.android.performance;

import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl;
import com.bugsnag.android.performance.internal.SpanImpl;
import com.bugsnag.android.performance.internal.processing.BatchingSpanProcessor;
import com.bugsnag.android.performance.internal.processing.ForwardingSpanProcessor;

import java.util.Collection;

class BugsnagPerformanceHooks {
    static Collection<SpanImpl> takeCurrentBatch() {
        ForwardingSpanProcessor forwardingSpanProcessor =
                (ForwardingSpanProcessor) BugsnagPerformanceImpl
                        .INSTANCE
                        .getInstrumentedAppState()
                        .getSpanProcessor();

        // forward the current batch to the new BatchingSpanProcessor
        BatchingSpanProcessor target = new BatchingSpanProcessor();
        forwardingSpanProcessor.forwardTo(target);
        return target.takeBatch();
    }

    static long durationOf(SpanImpl span) {
        long endTime = span.getEndTime$internal();

        if (endTime <= 0) {
            return 0;
        }

        return endTime - span.getStartTime$internal();
    }
}
