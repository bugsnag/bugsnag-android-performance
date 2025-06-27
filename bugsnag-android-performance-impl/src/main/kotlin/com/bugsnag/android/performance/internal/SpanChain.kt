@file:JvmName("SpanChains")

package com.bugsnag.android.performance.internal

/**
 * SpanChain is a type alias of [SpanImpl] to be used when a "list" of spans is expected instead of
 * single span objects.
 */
internal typealias SpanChain = SpanImpl

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
