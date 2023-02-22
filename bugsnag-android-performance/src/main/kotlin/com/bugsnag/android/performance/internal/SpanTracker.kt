package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl.Companion.NO_END_TIME
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Container for [Span]s that are associated with other objects, which have their own
 * lifecycles (`Activity`, `Fragment`, etc.). SpanTracker encapsulates tracking of the `Span`s
 * that *might* be closed automatically *or* manually. As such a `Span` can be
 * [marked as auto ended](markSpanAutomaticEnd) which will leave the `Span` open, but remember
 * the `endTime`. If the `Span` is then [marked as leaked](markSpanLeaked) then its "auto end"
 * time is used to close it.
 */
@JvmInline
value class SpanTracker<T>(
    @PublishedApi
    internal val backingStore: ConcurrentMap<T, SpanBinding> = ConcurrentHashMap()
) {
    operator fun get(token: T): SpanImpl? {
        return backingStore[token]?.span
    }

    /**
     * Ensure that the specified `token` is tracked by this `SpanTracker` creating a new `Span`
     * if required (by calling [createSpan]). If the `token` already has an associated `Span`
     * then that is returned, otherwise [createSpan] is used.
     *
     * Note: in race scenarios the [createSpan] may be invoked and the resulting `Span` discarded,
     * the currently tracked `Span` will however always be returned.
     */
    inline fun track(token: T, createSpan: () -> SpanImpl): SpanImpl {
        var spanBinding: SpanBinding? = backingStore[token]
        if (spanBinding == null) {
            spanBinding = SpanBinding(createSpan())
            val racedSpan = backingStore.putIfAbsent(token, spanBinding)

            if (racedSpan != null) {
                spanBinding = racedSpan
            }
        }

        return spanBinding.span
    }

    /**
     * Mark when a `Span` would have been ended automatically. *If* the associated `Span` is later
     * marked as [leaked](markSpanLeaked) then its `endTime` will be set to [autoEndTime]. Otherwise
     * this value will be discarded.
     */
    fun markSpanAutomaticEnd(token: T) {
        backingStore[token]?.autoEndTime = SystemClock.elapsedRealtimeNanos()
    }

    /**
     * Attempt to mark a tracked `Span` as "leaked", closing it with its (automatic end)[markSpanAutomaticEnd].
     * Returns `true` if the `Span` was marked as leaked, or `false` if the `Span` was already
     * considered to be closed (or was not tracked).
     */
    fun markSpanLeaked(token: T): Boolean {
        return backingStore.remove(token)?.markLeaked() == true
    }

    /**
     * End the tracking of a `Span` marking its `endTime` if it has not already been closed.
     * This *must* be called in order to ensure tokens can be garbage-collected.
     */
    fun endSpan(token: T, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        backingStore.remove(token)?.span?.end(endTime)
    }

    class SpanBinding(val span: SpanImpl) {
        var autoEndTime: Long = NO_END_TIME

        fun markLeaked(): Boolean {
            // this span has not leaked, ignore and return
            if (span.endTime != NO_END_TIME) {
                return false
            }

            if (autoEndTime != NO_END_TIME) {
                span.end(autoEndTime)
            } else {
                span.end()
            }

            return true
        }
    }
}
