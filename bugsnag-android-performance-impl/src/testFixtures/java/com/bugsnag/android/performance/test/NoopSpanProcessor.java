package com.bugsnag.android.performance.test;

import com.bugsnag.android.performance.Span;
import com.bugsnag.android.performance.internal.SpanProcessor;

public class NoopSpanProcessor implements SpanProcessor {
    public static final NoopSpanProcessor INSTANCE = new NoopSpanProcessor();

    @Override
    public void onEnd(Span span) {
    }
}
