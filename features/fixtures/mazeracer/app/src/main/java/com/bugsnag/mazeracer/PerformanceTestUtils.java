package com.bugsnag.mazeracer;

import android.annotation.SuppressLint;

import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl;
import com.bugsnag.android.performance.internal.InstrumentedAppState;
import com.bugsnag.android.performance.internal.Persistence;
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
        InstrumentedAppState appState = BugsnagPerformanceImpl.INSTANCE.getInstrumentedAppState();
        SpanProcessor processor = appState.getSpanProcessor();

        try {
            Thread.sleep(1500);
        } catch(InterruptedException e) {
            // ignore
        }

        if (processor instanceof Tracer) {
            Tracer tracer = (Tracer) processor;
            tracer.forceCurrentBatch();
        }
    }

    /**
     * @noinspection KotlinInternalInJava
     */
    @SuppressLint("RestrictedApi")
    public static void clearBugsnagPerformanceState() {
        LogKt.log("PerformanceTestUtils.clearBugsnagPerformanceState()");
        new Persistence(MazeRacerApplication.Companion.applicationContext()).clear();
    }
}
