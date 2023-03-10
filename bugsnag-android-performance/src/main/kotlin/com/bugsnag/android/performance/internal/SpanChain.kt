@file:JvmName("SpanChains")

package com.bugsnag.android.performance.internal

/**
 * SpanChain is a type alias of [SpanImpl] to be used when a "list" of spans is expected instead of
 * single span objects.
 */
internal typealias SpanChain = SpanImpl

/**
 * *Warning O(n) operation*
 *
 * Returns the number of `SpanImpl` objects in this `SpanChain`, by counting each  link in the chain.
 * This should only be called on `SpanChain`s that are stable.
 *
 * @see toList
 */
internal val SpanChain.size: Int
    get() {
        var count = 0
        var span: SpanImpl? = this

        while (span != null) {
            count++
            span = span.next
        }

        return count
    }

/**
 * Find the head of this `SpanChain`, traversing up the chain until a `SpanImpl` with no "previous"
 * node is found. This method should only be invoked on stable `SpanChain`s, that is: those
 * only accessible be a single thread.
 */
internal fun SpanChain.tail(): SpanImpl {
    var span = this

    // this awkward little dance of keeping `span.next` local avoids extra null enforcement, and
    // slightly side-steps some possible races
    var previous = span.next

    while (previous != null) {
        span = previous
        previous = span.next
    }

    return span
}

internal inline fun SpanChain.forEach(action: (SpanImpl) -> Unit) {
    var span: SpanImpl? = this

    while (span != null) {
        action(span)
        span = span.next
    }
}

internal inline fun SpanChain.find(predicate: (SpanImpl) -> Boolean): SpanImpl? {
    var span: SpanImpl? = this
    while (span != null) {
        if (predicate(span)) {
            return span
        }

        span = span.next
    }

    return null
}

/**
 * Filter this span chain (starting at this `SpanChain`) using the given [predicate] and return the
 * new "tail" of the filtered chain. Unlike normal `filter` operations this is *destructive* once
 * called the `SpanImpl`s in the chain should *only* be accessed by the returned value.
 *
 * @see toList
 */
internal inline fun SpanChain.filter(predicate: (SpanImpl) -> Boolean): SpanImpl? {
    val root = find(predicate) ?: return null

    var span: SpanImpl? = root
    var tail = root
    while (span != null) {
        if (!predicate(span)) {
            tail.next = span.next
            span.next?.let { tail = it }
        }

        span = span.next
    }

    return root
}

internal fun SpanChain.toList(): List<SpanImpl> {
    val list = ArrayList<SpanImpl>()
    forEach(list::add)
    return list
}

/**
 * Unrolls this [SpanChain] into the given [output] collection. This is *destructive* and the
 * [SpanChain] links will be broken as part of this process (turning each link back into a "normal"
 * [SpanImpl]).
 */
internal fun <C : MutableCollection<SpanImpl>> SpanChain.unlinkTo(output: C): C {
    var s: SpanImpl? = this
    while (s != null) {
        val next = s.next
        s.next = null
        output.add(s)
        s = next
    }
    return output
}

internal fun SpanChain?.isNotEmpty() = this != null

internal fun SpanChain?.isEmpty() = this == null
