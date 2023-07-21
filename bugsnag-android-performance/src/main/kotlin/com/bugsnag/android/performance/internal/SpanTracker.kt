package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl.Companion.NO_END_TIME
import java.util.IdentityHashMap
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
    private val backingStore: MutableMap<Any, MutableMap<Enum<*>?, SpanBinding>> = WeakHashMap()
    private val lock = ReentrantReadWriteLock()

    operator fun get(token: Any, subToken: Enum<*>? = null): SpanImpl? {
        return lock.read { backingStore[token]?.get(subToken)?.span }
    }

    /**
     * Track a `SpanImpl` against a specified [token] object, and an optional [subToken] Enum, returning
     * the actual `Span` being tracked. This function may discard [span] if the given [token] is
     * already being tracked, if this is the case the tracked span will be returned.
     */
    fun associate(token: Any, subToken: Enum<*>? = null, span: SpanImpl): SpanImpl {
        lock.write {
            val trackedSpans = backingStore[token] ?: IdentityHashMap()
            val existingSpan = trackedSpans[subToken]
            if (existingSpan != null) {
                return existingSpan.span
            } else {
                trackedSpans[subToken] = SpanBinding(span)
                backingStore[token] = trackedSpans
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
    inline fun associate(
        token: Any,
        subToken: Enum<*>? = null,
        createSpan: () -> SpanImpl,
    ): SpanImpl {
        var associatedSpan = this[token, subToken]
        if (associatedSpan == null) {
            associatedSpan = associate(token, subToken, createSpan())
        }

        return associatedSpan
    }

    fun removeAssociation(tag: Any?, subToken: Enum<*>? = null): SpanImpl? {
        return lock.write {
            val associatedSpans = backingStore[tag]
            val span = associatedSpans?.remove(subToken)?.span

            if (associatedSpans?.isEmpty() == true) {
                backingStore.remove(tag)
            }

            span
        }
    }

    fun removeAllAssociations(tag: Any?): Collection<SpanImpl> {
        return lock.write {
            val associatedSpans = backingStore.remove(tag)
            associatedSpans.orEmpty().values.map { it.span }
        }
    }

    /**
     * Mark when a `Span` would have been ended automatically. *If* the associated `Span` is later
     * marked as [leaked](markSpanLeaked) then its `endTime` will be set to the time that this
     * function was last called. Otherwise this value will be discarded.
     */
    fun markSpanAutomaticEnd(token: Any, subToken: Enum<*>? = null) {
        backingStore[token]?.get(subToken)?.autoEndTime = SystemClock.elapsedRealtimeNanos()
    }

    /**
     * Attempt to mark a tracked `Span` as "leaked", closing it with its (automatic end)[markSpanAutomaticEnd].
     * Returns `true` if the `Span` was marked as leaked, or `false` if the `Span` was already
     * considered to be closed (or was not tracked).
     */
    fun markSpanLeaked(token: Any, subToken: Enum<*>? = null): Boolean {
        return lock.write {
            val associatedSpans = backingStore[token]
            val leaked = associatedSpans?.remove(subToken)?.markLeaked() == true

            if (associatedSpans?.isEmpty() == true) {
                backingStore.remove(token)
            }

            leaked
        }
    }

    /**
     * End the tracking of a `Span` marking its `endTime` if it has not already been closed.
     * This *must* be called in order to ensure tokens can be garbage-collected.
     */
    fun endSpan(
        token: Any,
        subToken: Enum<*>? = null,
        endTime: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        lock.write {
            val associatedSpans = backingStore[token]
            associatedSpans?.remove(subToken)?.span?.end(endTime)

            if (associatedSpans?.isEmpty() == true) {
                backingStore.remove(token)
            }
        }
    }

    /**
     * End all spans associated with the given `token` or any `subToken`s. This will attempt to
     * use autoEndTimes where they are available, but will otherwise fallback to using [endTime]
     * for each of the spans that get closed.
     */
    fun endAllSpans(token: Any, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        lock.write {
            val associatedSpans = backingStore[token]
            associatedSpans?.values?.forEach { it.markLeaked(endTime) }

            backingStore.remove(token)
        }
    }

    private class SpanBinding(val span: SpanImpl) {
        var autoEndTime: Long = NO_END_TIME

        fun markLeaked(fallbackEndTime: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
            // this span has not leaked, ignore and return
            if (span.endTime != NO_END_TIME) {
                return false
            }

            if (autoEndTime != NO_END_TIME) {
                span.end(autoEndTime)
            } else {
                span.end(fallbackEndTime)
            }

            return true
        }
    }
}
