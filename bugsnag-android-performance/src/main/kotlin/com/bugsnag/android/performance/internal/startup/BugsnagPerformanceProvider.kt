package com.bugsnag.android.performance.internal.startup

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance

class BugsnagPerformanceProvider : AbstractStartupProvider() {
    private val startupTracker get() = BugsnagPerformance.instrumentedAppState.startupTracker

    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { app ->
            BugsnagPerformance.instrumentedAppState.attach(app)
        }

        startupTracker.onApplicationCreate()

        return true
    }

    companion object {
        init {
            // report the earliest class load we are aware of
            BugsnagPerformance.reportApplicationClassLoaded()
        }
    }
}
