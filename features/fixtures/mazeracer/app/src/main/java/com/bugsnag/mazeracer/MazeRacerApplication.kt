package com.bugsnag.mazeracer

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance

class MazeRacerApplication : Application() {
    init {
        BugsnagPerformance.reportApplicationClassLoaded()
    }

    override fun onCreate() {
        super.onCreate()

        // if there is stored "startup" config then we start BugsnagPerformance before the scenario
        // this is used to test things like app-start instrumentation
        readStartupConfig()?.let { config ->
            BugsnagPerformance.start(config)
        }
    }
}
