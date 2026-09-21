package com.example.bugsnag.performance

import android.app.Application
import android.os.Handler
import android.os.Looper
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

            // While calling reportApplicationClassLoaded in the static initializer isn't required
            // it does slightly improve the quality of the AppStart spans by having them start
            // before the ContentProviders are initialized
            BugsnagPerformance.reportApplicationClassLoaded()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val config = PerformanceConfiguration.load(this)
        config.enabledMetrics = EnabledMetrics(true)
        // Set higher sampling intervals for App Sessions to reduce CPU usage on low-end hardware
        // Note: The SDK enforces a maximum of 60 seconds for all sampling intervals.
        config.appSessionConfig.samplingIntervalMs = 30_000L // Sample CPU/ART every 60 seconds
        config.appSessionConfig.deviceMemorySamplingIntervalMs = 30_000L // Sample PSS every 60 seconds
        config.appSessionConfig.maxSessionDurationMs = 60_000L // Auto-finalize session after 120s

        // Disable automatic session management for manual testing
        config.appSessionConfig.autoStartSession = false
        config.appSessionConfig.backgroundTimeoutMs = 0L // No automatic timeout
        BugsnagPerformance.start(config)
    }
}