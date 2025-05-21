package com.bugsnag.android.performance.internal.plugins

import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.android.performance.internal.util.Prioritized

internal class PluginContextImpl(
    override val configuration: PerformanceConfiguration,
) : PluginContext {
    internal val spanStartCallbacks = mutableListOf<Prioritized<OnSpanStartCallback>>()
    internal val spanEndCallbacks = mutableListOf<Prioritized<OnSpanEndCallback>>()

    override fun addOnSpanStartCallback(priority: Int, sb: OnSpanStartCallback) {
        spanStartCallbacks.add(Prioritized(priority, sb))
    }

    override fun addOnSpanEndCallback(priority: Int, sb: OnSpanEndCallback) {
        spanEndCallbacks.add(Prioritized(priority, sb))
    }
}
