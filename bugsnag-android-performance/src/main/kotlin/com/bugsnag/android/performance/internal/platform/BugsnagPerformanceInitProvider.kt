package com.bugsnag.android.performance.internal.platform

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance

class BugsnagPerformanceInitProvider : AbstractCreateProvider() {
    override fun onCreate(): Boolean {
        (context?.applicationContext as? Application)?.let { app ->
            BugsnagPerformance.instrumentedAppState.bind(app)
            BugsnagPerformance.reportApplicationClassLoaded()
        }
        return true
    }
}
