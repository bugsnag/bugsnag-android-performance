package com.example.bugsnag.performance

import android.app.Application
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.context.HybridSpanContextStorage

class PerformanceApplication : Application() {
    companion object {
        init {
            // To simplify span parenting the Example uses the HybridSpanContextStorage which
            // has a single global stack of spans while allowing threads to optionally create
            // a ThreadLocal SpanContext store for work that needs to be isolated from the main
            // app process.
            SpanContext.defaultStorage = HybridSpanContextStorage()

            // While calling reportApplicationClassLoaded in the static initializer isn't required
            // it does slightly improve the quality of the AppStart spans by having them start
            // before the ContentProviders are initialized
            BugsnagPerformance.reportApplicationClassLoaded()
        }
    }

    override fun onCreate() {
        super.onCreate()
        BugsnagPerformance.start(PerformanceConfiguration.load(this))
    }
}