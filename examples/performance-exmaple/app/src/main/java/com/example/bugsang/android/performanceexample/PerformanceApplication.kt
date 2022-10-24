package com.example.bugsang.android.performanceexample

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration

class PerformanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BugsnagPerformance.start(PerformanceConfiguration(this).apply {
            endpoint = "http://10.0.2.2:9339"
        })
    }
}