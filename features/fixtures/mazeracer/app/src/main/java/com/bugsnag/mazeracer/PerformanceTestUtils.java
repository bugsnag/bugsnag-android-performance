package com.bugsnag.mazeracer;

import android.annotation.SuppressLint;

import com.bugsnag.android.performance.BugsnagPerformance;
import com.bugsnag.android.performance.internal.InstrumentedAppState;
import com.bugsnag.android.performance.internal.SpanProcessor;
import com.bugsnag.android.performance.internal.processing.Tracer;

public class PerformanceTestUtils {
    private PerformanceTestUtils() {
    }

    /**
     * @noinspection KotlinInternalInJava
     */
    @SuppressLint("RestrictedApi")
    public static void flushBatch() {
        LogKt.log("PerformanceTestUtils.flushBatch()");
        InstrumentedAppState appState = BugsnagPerformance.INSTANCE.getInstrumentedAppState$internal();
        SpanProcessor processor = appState.getSpanProcessor();

        if (processor instanceof Tracer) {
            Tracer tracer = (Tracer) processor;
            tracer.forceCurrentBatch();
        }
    }
}
