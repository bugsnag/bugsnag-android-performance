package com.bugsnag.android.performance.internal.plugins

import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.internal.util.Prioritized

internal class PluginContextImpl(
    override val configuration: PerformanceConfiguration,
) : PluginContext {
    internal val spanStartCallbacks = mutableListOf<Prioritized<OnSpanStartCallback>>()
    internal val spanEndCallbacks = mutableListOf<Prioritized<OnSpanEndCallback>>()
    internal val spanControlProviders = mutableListOf<Prioritized<SpanControlProvider<*>>>()

    internal fun mergeFrom(other: PluginContextImpl) {
        spanStartCallbacks.addAll(other.spanStartCallbacks)
        spanEndCallbacks.addAll(other.spanEndCallbacks)
        spanControlProviders.addAll(other.spanControlProviders)
    }

    override fun addOnSpanStartCallback(
        priority: Int,
        callback: OnSpanStartCallback,
    ) {
        spanStartCallbacks.add(Prioritized(priority, callback))
    }

    override fun addOnSpanEndCallback(
        priority: Int,
        callback: OnSpanEndCallback,
    ) {
        spanEndCallbacks.add(Prioritized(priority, callback))
    }

    override fun addSpanControlProvider(
        priority: Int,
        provider: SpanControlProvider<*>,
    ) {
        spanControlProviders.add(Prioritized(priority, provider))
    }
}
