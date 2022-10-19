package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.Span.Companion.NO_END_TIME
import java.util.concurrent.ConcurrentHashMap

/**
 * Container for [Span]s that are associated with other objects, which have their own
 * lifecycles (`Activity`, `Fragment`, etc.). SpanTracker encapsulates tracking of the `Span`s
 * that *might* be closed automatically *or* manually. As such a `Span` can be
 * [marked as auto ended](markSpanAutomaticEnd) which will leave the `Span` open, but remember
 * the `endTime`. If the `Span` is then [marked as leaked](markSpanLeaked) then its "auto end"
 * time is used to close it.
 */
@JvmInline
internal value class SpanTracker<T>(
    private val backingStore: MutableMap<T, TrackedSpan> = ConcurrentHashMap<T, TrackedSpan>()
) {
    operator fun set(token: T, span: Span) {
        backingStore[token] = TrackedSpan(span)
    }

    operator fun get(token: T): Span? {
        return backingStore[token]?.span
    }

    /**
     * Mark when a `Span` would have been ended automatically. *If* the associated `Span` is later
     * marked as [leaked](markSpanLeaked) then its `endTime` will be set to [autoEndTime]. Otherwise
     * this value will be discarded.
     */
    fun markSpanAutomaticEnd(token: T, autoEndTime: Long = SystemClock.elapsedRealtimeNanos()) {
        backingStore[token]?.autoEndTime = autoEndTime
    }

    /**
     * Attempt to mark a tracked `Span` as "leaked, closing it with its (automatic end)[markSpanAutomaticEnd].
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

    internal class TrackedSpan(val span: Span) {
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
