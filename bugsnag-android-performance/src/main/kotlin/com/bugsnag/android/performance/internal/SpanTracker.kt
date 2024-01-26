package com.bugsnag.android.performance.internal

import android.os.SystemClock
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl.Companion.NO_END_TIME
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Default size of the SpanTracker table, this must be a power-of-two value
 */
private const val DEFAULT_SPAN_TRACKER_TABLE_SIZE = 8

/**
 * The maximum depth allowed in a SpanBinding linked-list / chain, adding
 * to a linked-list this long will cause the binding table to grow in an attempt
 * to reduce collisions
 */
private const val MAX_BINDING_LIST_DEPTH = 3

/**
 * Container for [Span]s that are associated with other objects, which have their own
 * lifecycles (`Activity`, `Fragment`, etc.). SpanTracker encapsulates tracking of the `Span`s
 * that *might* be closed automatically *or* manually. As such a `Span` can be
 * [marked as auto ended](markSpanAutomaticEnd) which will leave the `Span` open, but remember
 * the `endTime`. If the `Span` is then [marked as leaked](markSpanLeaked) then its "auto end"
 * time is used to close it.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SpanTracker {

    /*
     * Implementation:
     * SpanTracker is effectively a dual-key WeakHashMap which correctly disposes of Spans that
     * are "lost" due to garbage collection. Instead of a Map.Entry each Span is tracked against
     * a SpanBinding, which holds a WeakReference to the bound "token" (the object the Span is
     * associated with, typically an Activity or Fragment).
     *
     * We cannot reasonably use a WeakHashMap by itself as there is no way to know when keys (tokens)
     * are "lost" due to being garbage collected. SpanTracker will correctly end, mark as leaked,
     * or discard the associated Span objects when the token is leaked. This avoids the Span
     * objects "leaking" within the SpanContext hierarchy, since we assume that once the token
     * is lost there is no remaining way for the user to end() the Span.
     *
     * The "bindings" table is structured similar to HashMap or similar classes, but using
     * SpanBinding in place of a Map.Entry as we have a more complex key structure. For example
     * a table might look something like:
     *
     * [ ] binding -> collision-binding -> collision-binding -> null
     * [ ] null
     * [ ] null
     * [ ] binding -> null
     * [ ] binding -> binding -> null
     * [ ] null
     * [ ] null
     * [ ] null
     *
     * The linked-list represented by the SpanBinding.next property is *only* used to handle table
     * space collisions (when multiple bindings need to occupy the same slot in the bindings table).
     * They do *not* represent a full-table linked list (as LinkedHashMap would).
     */

    private var bindings: Array<SpanBinding?> = arrayOfNulls(DEFAULT_SPAN_TRACKER_TABLE_SIZE)
    private val referenceQueue = ReferenceQueue<Any>()
    private val lock = ReentrantReadWriteLock()

    /**
     * Sweep the old entries from the table, closing any associated spans in the process.
     *
     * This must only be called by a thread that already holds the write lock.
     */
    private fun sweepStaleEntriesUnderWriteLock() {
        val table = bindings
        val tableSize = table.size

        var binding: SpanBinding? = referenceQueue.poll() as? SpanBinding
        while (binding != null) {
            // make sure we don't leak the "lost" Span
            binding.sweep()

            val tableIndex = indexForHash(binding.hash, tableSize)
            table.unlinkBindingWhere(tableIndex) { it === binding }

            binding = referenceQueue.poll() as? SpanBinding
        }
    }

    public operator fun get(token: Any, subToken: Enum<*>? = null): SpanImpl? {
        return lock.read {
            val hash = hashCodeFor(token, subToken)
            val index = indexForHash(hash, bindings.size)

            return@read bindings[index]?.entryFor(token, subToken)?.span
        }
    }

    /**
     * Track a `SpanImpl` against a specified [token] object, and an optional [subToken] Enum, returning
     * the actual `Span` being tracked. This function may discard [span] if the given [token] is
     * already being tracked, if this is the case the tracked span will be returned.
     */
    public fun associate(token: Any, subToken: Enum<*>? = null, span: SpanImpl): SpanImpl {
        return lock.write {
            sweepStaleEntriesUnderWriteLock()

            val hash = hashCodeFor(token, subToken)
            var index = indexForHash(hash, bindings.size)

            val existingBinding = bindings[index]

            // the slot is available so association is simple
            if (existingBinding == null) {
                bindings[index] = SpanBinding(token, referenceQueue, hash, subToken, span)
                return@write span
            } else {
                val bindingForToken = existingBinding.entryFor(token, subToken)
                if (bindingForToken != null) {
                    // this binding already exists, so we return the existing Span
                    return@write bindingForToken.span
                }

                // we've collided with an existing SpanBinding for something else
                if (existingBinding.depth >= MAX_BINDING_LIST_DEPTH) {
                    // the depth of the chain is too long, so we expand the table
                    bindings = expandBindingsTable()

                    // update the index for the new bindings table
                    index = indexForHash(hash, bindings.size)
                }

                // add the new binding as the start of the existing chain
                val newBinding = SpanBinding(token, referenceQueue, hash, subToken, span)
                newBinding.next = bindings[index]
                bindings[index] = newBinding

                return@write span
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
    public inline fun associate(
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

    public fun removeAssociation(token: Any?, subToken: Enum<*>? = null): SpanImpl? {
        if (token == null) {
            return null
        }

        return lock.write {
            sweepStaleEntriesUnderWriteLock()

            val hash = hashCodeFor(token, subToken)
            val index = indexForHash(hash, bindings.size)

            val removed = bindings.unlinkBindingWhere(index) {
                it.get() === token && it.subToken == subToken
            }

            return@write removed?.span
        }
    }

    /**
     * Mark when a `Span` would have been ended automatically. *If* the associated `Span` is later
     * marked as [leaked](markSpanLeaked) then its `endTime` will be set to the time that this
     * function was last called. Otherwise this value will be discarded.
     */
    public fun markSpanAutomaticEnd(token: Any, subToken: Enum<*>? = null) {
        lock.read {
            val hash = hashCodeFor(token, subToken)
            val index = indexForHash(hash, bindings.size)

            val binding = bindings[index]?.entryFor(token, subToken)
            binding?.autoEndTime = SystemClock.elapsedRealtimeNanos()
        }
    }

    /**
     * Attempt to mark a tracked `Span` as "leaked", closing it with its (automatic end)[markSpanAutomaticEnd].
     * Returns `true` if the `Span` was marked as leaked, or `false` if the `Span` was already
     * considered to be closed (or was not tracked).
     */
    public fun markSpanLeaked(token: Any, subToken: Enum<*>? = null): Boolean {
        return lock.write {
            sweepStaleEntriesUnderWriteLock()

            val hash = hashCodeFor(token, subToken)
            val index = indexForHash(hash, bindings.size)

            val binding = bindings.unlinkBindingWhere(index) {
                it.get() === token && it.subToken == subToken
            }

            return@write binding?.markLeaked() == true
        }
    }

    /**
     * End the tracking of a `Span` marking its `endTime` if it has not already been closed.
     * This *must* be called in order to ensure tokens can be garbage-collected.
     */
    public fun endSpan(
        token: Any,
        subToken: Enum<*>? = null,
        endTime: Long = SystemClock.elapsedRealtimeNanos(),
    ) {
        lock.write {
            sweepStaleEntriesUnderWriteLock()

            val hash = hashCodeFor(token, subToken)
            val index = indexForHash(hash, bindings.size)

            val removed = bindings.unlinkBindingWhere(index) {
                it.get() === token && it.subToken == subToken
            }

            removed?.span?.end(endTime)
        }
    }

    /**
     * End all spans associated with the given [token] or any `subToken`s. This will attempt to
     * use autoEndTimes where they are available, but will otherwise fallback to using [endTime]
     * for each of the spans that get closed.
     */
    public fun endAllSpans(token: Any, endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        lock.write {
            sweepStaleEntriesUnderWriteLock()

            // we do a sweep of the entire binding table
            // this is typically quite small as it isn't common for more than a few
            // root tokens to be active at a time, leading to the table mostly being
            // subTokens of the same roots
            val table = bindings
            // this loop walks "down" the bindings table
            for (index in table.indices) {
                var currentBinding: SpanBinding? = table[index] ?: continue

                var newHead: SpanBinding? = null
                var previous: SpanBinding? = null

                // this loop walks "sideways" through the linked-list of bindings on this "row"
                while (currentBinding != null) {
                    if (currentBinding.get() === token) {
                        // end the span at an appropriate time
                        currentBinding.markLeaked(endTime)
                        // unlink currentBinding from the list
                        previous?.next = currentBinding.next
                    } else if (newHead == null) {
                        newHead = currentBinding
                        previous = currentBinding
                    } else {
                        previous = currentBinding
                    }

                    // move to the next binding (if there is one)
                    currentBinding = currentBinding.next
                }

                table[index] = newHead
            }
        }
    }

    /**
     * Discard & remove all spans associated with the given [token]. This will result in any
     * associated spans being discarded, any of their child spans that have already been closed
     * will have no valid parent span and will also be discarded by the server-side.
     */
    public fun discardAllSpans(token: Any) {
        lock.write {
            sweepStaleEntriesUnderWriteLock()

            // we do a sweep of the end binding table
            // this is typically quite small as it isn't common for more than a few
            // root tokens to be active at a time, leading to the table mostly being
            // subTokens of the same roots
            val bindingsTable = bindings
            for (index in bindingsTable.indices) {
                var removed: SpanBinding?
                do {
                    removed = bindingsTable.unlinkBindingWhere(index) { it.get() === token }
                        ?: break

                    removed.span.discard()
                } while (removed != null)
            }
        }
    }

    private fun indexForHash(hash: Int, tableSize: Int): Int {
        return hash and (tableSize - 1)
    }

    private fun hashCodeFor(token: Any, subToken: Enum<*>?): Int {
        return System.identityHashCode(token) xor subToken.hashCode()
    }

    private fun expandBindingsTable(): Array<SpanBinding?> {
        val newBindingsTable = arrayOfNulls<SpanBinding>(expandedTableSize(bindings.size))
        val newTableSize = newBindingsTable.size

        for (index in bindings.indices) {
            var binding = bindings[index]
            while (binding != null) {
                val next = binding.next
                val newBindingIndex = indexForHash(binding.hash, newTableSize)

                // build a new chain for collisions in the expanded table
                binding.next = newBindingsTable[newBindingIndex]
                newBindingsTable[newBindingIndex] = binding

                // move onto the next binding to be added to the expanded table
                binding = next
            }
        }

        return newBindingsTable
    }

    /**
     * Remove a *single* SpanBinding at a given [bindingIndex] from the linked-list where the given
     * predicate matches that SpanBinding, returning the removed SpanBinding (if one was removed).
     */
    @Suppress("ReturnCount") // 1 extra return for an early-exit
    private inline fun Array<SpanBinding?>.unlinkBindingWhere(
        bindingIndex: Int,
        predicate: (SpanBinding) -> Boolean,
    ): SpanBinding? {
        val head = this[bindingIndex] ?: return null

        if (predicate(head)) {
            this[bindingIndex] = head.next
            return head
        } else {
            var node = head.next
            var previous = head
            while (node != null) {
                if (predicate(node)) {
                    previous.next = node.next
                    break
                } else {
                    previous = node
                    node = node.next
                }
            }

            return node
        }
    }

    // WARNING: do not put anything other than 2 here!
    // The table size *must* remain power-of-two as it grows, otherwise indexForHash will break
    private fun expandedTableSize(currentTableSize: Int) = currentTableSize * 2

    private class SpanBinding(
        boundObject: Any,
        referenceQueue: ReferenceQueue<in Any>,
        @JvmField
        val hash: Int,
        @JvmField
        val subToken: Enum<*>? = null,
        @JvmField
        var span: SpanImpl,
    ) : WeakReference<Any>(boundObject, referenceQueue) {
        @JvmField
        var autoEndTime: Long = NO_END_TIME

        @JvmField
        var next: SpanBinding? = null

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

        /**
         * Called when the token that this `SpanBinding` is bound to has been garbage collected.
         * This method will ensure that the `Span` is either closed at it's [autoEndTime] or
         * (discarded)[SpanImpl.discard] (depending which is more appropriate).
         */
        fun sweep() {
            if (autoEndTime == NO_END_TIME) {
                span.discard()
            } else {
                span.end(autoEndTime)
            }
        }

        override fun toString(): String {
            return "Binding[$span, token=${get()}, subToken=${subToken}]"
        }

        // Linked-list utility functions follow ----------------------------------------------------

        val depth: Int
            get() {
                var d = 1
                var node = next
                while (node != null) {
                    d++
                    node = node.next
                }

                return d
            }

        fun entryFor(token: Any, subToken: Enum<*>?): SpanBinding? {
            var link: SpanBinding? = this
            while (link != null) {
                if (link.get() === token && link.subToken == subToken) {
                    return link
                }
                link = link.next
            }

            return null
        }
    }
}
