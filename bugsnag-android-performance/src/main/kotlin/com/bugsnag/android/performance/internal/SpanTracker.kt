package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl.Companion.NO_END_TIME
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Container for [Span]s that are associated with other objects, which have their own
 * lifecycles (`Activity`, `Fragment`, etc.). SpanTracker encapsulates tracking of the `Span`s
 * that *might* be closed automatically *or* manually. As such a `Span` can be
 * [marked as auto ended](markSpanAutomaticEnd) which will leave the `Span` open, but remember
 * the `endTime`. If the `Span` is then [marked as leaked](markSpanLeaked) then its "auto end"
 * time is used to close it.
 */
class SpanTracker {
    private val backingStore: MutableMap<Any, SpanBinding> = WeakHashMap()
    private val lock = ReentrantReadWriteLock()

    operator fun get(token: Any): SpanImpl? {
        return lock.read { backingStore[token]?.span }
    }

    /**
     * Track a `SpanImpl` against a specified [token] object, returning the actual `Span` being
     * tracked. This function may discard [span] if the given [token] is already being tracked,
     * if this is the case the tracked span will be returned.
     */
    fun associate(token: Any, span: SpanImpl): SpanImpl {
        lock.write {
            val existingSpan = backingStore[token]
            if (existingSpan != null) {
                return existingSpan.span
            } else {
                backingStore[token] = SpanBinding(span)
                return span
            }
        }
    }

    /**
     * Ensure that the specified `token` is tracked by this `SpanTracker` creating a new `Span`
     * if required (by calling [createSpan]). If the `token` already has an associated `Span`
     * then that is returned, otherwise [createSpan] is used.
     *
     * Note: in race scenarios the [createSpan] may be invoked and the resulting `Span` discarded,
     * the currently tracked `Span` will however always be returned.
     */
    inline fun associate(token: Any, createSpan: () -> SpanImpl): SpanImpl {
        var existingSpan = this[token]
        if (existingSpan == null) {
            existingSpan = associate(token, createSpan())
        }

        return existingSpan
    }

    /**
     * Mark when a `Span` would have been ended automatically. *If* the associated `Span` is later
     * marked as [leaked](markSpanLeaked) then its `endTime` will be set to [autoEndTime]. Otherwise
     * this value will be discarded.
     */
    fun markSpanAutomaticEnd(token: Any) {
        backingStore[token]?.autoEndTime = SystemClock.elapsedRealtimeNanos()
    }

    /**
     * Attempt to mark a tracked `Span` as "leaked", closing it with its (automatic end)[markSpanAutomaticEnd].
     * Returns `true` if the `Span` was marked as leaked, or `false` if the `Span` was already
     * considered to be closed (or was not tracked).
     */
    fun markSpanLeaked(token: Any): Boolean {
        return lock.write { backingStore.remove(token)?.markLeaked() == true }
    }

    /**
     * End the tracking of a `Span` marking its `endTime` if it has not already been closed.
     * This *must* be called in order to ensure tokens can be garbage-collected.
     */
    fun endSpan(token: Any, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        lock.write { backingStore.remove(token)?.span?.end(endTime) }
    }

    private class SpanBinding(val span: SpanImpl) {
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
