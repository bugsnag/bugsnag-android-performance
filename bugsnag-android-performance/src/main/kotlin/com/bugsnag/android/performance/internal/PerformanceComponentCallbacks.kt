package com.bugsnag.android.performance.internal

import android.content.ComponentCallbacks2
import android.content.res.Configuration

internal class PerformanceComponentCallbacks(private val tracer: Tracer) : ComponentCallbacks2 {
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
    override fun onLowMemory() = Unit

    override fun onTrimMemory(level: Int) {
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // App has been backgrounded
            tracer.flush()
        }
    }
}
