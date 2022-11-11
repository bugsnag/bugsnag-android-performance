@file:JvmName("SpanChains")

package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

/**
 * SpanChain is a type alias of [Span] to be used when a "list" of spans is expected instead of
 * single span objects.
 */
internal typealias SpanChain = Span

/**
 * *Warning O(n) operation*
 *
 * Returns the number of `Span` objects in this `SpanChain`, by counting each  link in the chain.
 * This should only be called on `SpanChain`s that are stable.
 *
 * @see toList
 */
internal val SpanChain.size: Int
    get() {
        var count = 0
        var span: Span? = this

        while (span != null) {
            count++
            span = span.previous
        }

        return count
    }

/**
 * Find the head of this `SpanChain`, traversing up the chain until a `Span` with no "previous"
 * node is found. This method should only be invoked on stable `SpanChain`s, that is: those
 * only accessible be a single thread.
 */
internal fun SpanChain.head(): Span {
    var span = this

    // this awkward little dance of keeping `span.previous` local avoids extra null enforcement, and
    // slightly side-steps some possible races
    var previous = span.previous

    while (previous != null) {
        span = previous
        previous = span.previous
    }

    return span
}

internal inline fun SpanChain.forEach(action: (Span) -> Unit) {
    var span: Span? = this

    while (span != null) {
        action(span)
        span = span.previous
    }
}

internal inline fun SpanChain.find(predicate: (Span) -> Boolean): Span? {
    var span: Span? = this
    while (span != null) {
        if (predicate(span)) {
            return span
        }

        span = span.previous
    }

    return null
}

/**
 * Filter this span chain (starting at this `SpanChain`) using the given [predicate] and return the
 * new "tail" of the filtered chain. Unlike normal `filter` operations this is *destructive* once
 * called the `Span`s in the chain should *only* be accessed by the returned value.
 *
 * @see toList
 */
internal inline fun SpanChain.filter(predicate: (Span) -> Boolean): Span? {
    val root = find(predicate) ?: return null

    var span: Span? = root
    var tail = root
    while (span != null) {
        if (!predicate(span)) {
            tail.previous = span.previous
            span.previous?.let { tail = it }
        }

        span = span.previous
    }

    return root
}

internal fun SpanChain.toList(): List<Span> {
    val list = ArrayList<Span>()
    forEach(list::add)
    return list
}

internal fun SpanChain?.isNotEmpty() = this != null

internal fun SpanChain?.isEmpty() = this == null

/**
 * Join 2 `SpanChain` chains together, the returned `SpanChain` is the *tail* of the joined chain.
 * This function (like most chain functions) is not thread-safe and should only be used on
 * `SpanChain` chains where both are stable.
 */
@JvmName("concat")
internal operator fun SpanChain?.plus(other: SpanChain): SpanChain {
    if (this == null) return other
    require(other != this) { "cannot join a Span chain to itself" }

    val head = head()
    if (head.previous != null) {
        throw ConcurrentModificationException()
    }

    head.previous = other

    return this
}
