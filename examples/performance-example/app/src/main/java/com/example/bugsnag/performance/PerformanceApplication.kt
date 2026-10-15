package com.example.bugsnag.performance

import android.app.Application
import android.content.Context
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.EnabledMetrics
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
        }
    }

    override fun onCreate() {
        super.onCreate()
        val config = PerformanceConfiguration.load(this as Context)
        config.enabledMetrics = EnabledMetrics(true, true, true, true)
        BugsnagPerformance.start(config)
    }
}