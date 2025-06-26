package com.bugsnag.android.performance.internal.startup

import android.app.Application
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.android.performance.internal.instrumentation.ForegroundState

public class BugsnagPerformanceProvider : AbstractStartupProvider() {
    private val startupTracker get() = BugsnagPerformanceImpl.instrumentedAppState.startupTracker

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { app ->
            app.registerActivityLifecycleCallbacks(ForegroundState)
            BugsnagPerformanceImpl.instrumentedAppState.attach(app)
        }

        startupTracker.onApplicationCreate()

        return true
    }

    public companion object {
        init {
            // report the earliest class load we are aware of
            BugsnagPerformanceImpl.reportApplicationClassLoaded()
        }
    }
}
