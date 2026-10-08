package com.bugsnag.android.performance.internal.startup

import android.app.Application
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState

public class BugsnagPerformanceProvider : AbstractStartupProvider() {
    private val startupTracker get() = BugsnagPerformanceImpl.instrumentedAppState.startupTracker

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { app ->
            app.registerActivityLifecycleCallbacks(ForegroundState)
            // `attach` must run *before* we report the class load below, since it is what
            // installs the CPU/memory/rendering/disk metric sources (MetricsContainer.attach()).
            // If the Cold AppStart span were created first (via reportApplicationClassLoaded),
            // it would capture a `null` metrics snapshot and never collect any system metrics
            // for the lifetime of that span.
            BugsnagPerformanceImpl.instrumentedAppState.attach(app)
        }

        // report the earliest class load we are aware of, now that metric sources are attached
        // so the Cold AppStart span (created as a side effect of this call) can collect them
        BugsnagPerformanceImpl.reportApplicationClassLoaded()

        startupTracker.onApplicationCreate()

        return true
    }
}
