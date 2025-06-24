package com.bugsnag.android.performance.controls

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.OnSpanStartCallback
import com.bugsnag.android.performance.Plugin
import com.bugsnag.android.performance.PluginContext
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.plugins.PluginContextImpl
import com.bugsnag.android.performance.internal.processing.Timeout
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * A [SpanQuery] that retrieves a [Span] by its name from [BugsnagPerformance.getSpanControls].
 * This can only be used if the [NamedSpanControlsPlugin] has been installed in the
 * [startup configuration](com.bugsnag.android.performance.PerformanceConfiguration.addPlugin).
 *
 * This query can be used to access spans that have been started and not yet ended (or discarded
 * due to timeout).
 *
 * @see [NamedSpanControlsPlugin] for the implementation that provides this functionality.
 */
public data class NamedSpanQuery(val name: String) : SpanQuery<Span>

internal class NamedSpanControlProvider(
    private val timeoutExecutor: TimeoutExecutor,
    private val timeout: Long,
    private val timeoutUnit: TimeUnit,
    private val onSpanTimeoutCallback: NamedSpanControlsPlugin.OnSpanTimeoutCallback? = null,
) : SpanControlProvider<Span>,
    OnSpanStartCallback,
    OnSpanEndCallback {
    private val spansByName = ConcurrentHashMap<String, SpanRef>()

    override fun <Q : SpanQuery<Span>> get(query: Q): Span? {
        if (query is NamedSpanQuery) {
            return spansByName[query.name]?.span
        }

        return null
    }

    override fun onSpanStart(span: Span) {
        val spanRef = SpanRef(span)
        spansByName[span.name] = spanRef
        timeoutExecutor.scheduleTimeout(spanRef)
    }

    override fun onSpanEnd(span: Span): Boolean {
        val ref = spansByName[span.name]
        if (ref?.span == span) {
            spansByName.remove(span.name, ref)
        }

        return true
    }

    private inner class SpanRef(val span: Span) : Timeout {
        override val target: Long = System.currentTimeMillis() + timeoutUnit.toMillis(timeout)

        override fun run() {
            onSpanEnd(span)
            onSpanTimeoutCallback?.onSpanTimeout(span)
        }
    }
}

/**
 * A [Plugin] that provides access to spans by their name through the
 * [BugsnagPerformance.getSpanControls] function. This only works for spans that have been
 * started and not yet ended or discarded due to timeout. In order avoid memory leaks,
 * the spans are retained for a limited time after they have been started, which can be
 * configured using the constructor parameters.
 */
public class NamedSpanControlsPlugin(
    /**
     * How long to retain spans after they have been started. Typically spans are released when
     * they end. If they are not ended for some reason, this is to ensure that spans are not leaked.
     */
    private val retainSpanTimeout: Long = 10L,
    /**
     * The unit of the [retainSpanTimeout].
     */
    private val retainSpanTimeoutUnit: TimeUnit = TimeUnit.MINUTES,
    /**
     * Callback that is invoked when a span has timed out and is no longer available.
     * This can be used to perform any cleanup or logging related to the span.
     *
     * This callback does not strictly imply a memory leak, but it is a signal that the span
     * has exceeded its retention time and is no longer available via the [NamedSpanControlsPlugin].
     */
    private val onSpanTimeoutCallback: OnSpanTimeoutCallback? = null,
) : Plugin {
    override fun install(ctx: PluginContext) {
        ctx as PluginContextImpl
        val controlProvider =
            NamedSpanControlProvider(
                ctx.timeoutExecutor,
                retainSpanTimeout,
                retainSpanTimeoutUnit,
                onSpanTimeoutCallback,
            )

        ctx.addOnSpanStartCallback(Int.MAX_VALUE, controlProvider)
        ctx.addOnSpanEndCallback(Int.MIN_VALUE, controlProvider)
        ctx.addSpanControlProvider(controlProvider)
    }

    /**
     * Callback interface that is invoked when a span has timed out and is no longer available.
     * This is called strictly after the span has been removed from the controls, so it can only
     * be used for cleanup or logging purposes and cannot cause the span to be retained further.
     *
     * This callback does not strictly imply a memory leak, but it is a signal that the span
     * has exceeded its retention time and is no longer available via the [NamedSpanControlsPlugin].
     */
    public fun interface OnSpanTimeoutCallback {
        /**
         * Called when a span has timed out and is no longer available.
         *
         * @param span the span that has timed out
         */
        public fun onSpanTimeout(span: Span)
    }
}
