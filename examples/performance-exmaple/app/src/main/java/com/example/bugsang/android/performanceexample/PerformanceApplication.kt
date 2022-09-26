package com.example.bugsang.android.performanceexample

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance

class PerformanceApplication: Application() {
    override fun onCreate() {
        super.onCreate()
        BugsnagPerformance.start()
    }
}