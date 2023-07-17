package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext

/**
 * Test that no [SpanImpl] objects within the current [SpanContext] match the given [predicate].
 * Returns `true` if [predicate] only returned `false`, otherwise `false`.
 */
internal inline fun SpanContext.Storage.noSpansMatch(predicate: (SpanImpl) -> Boolean): Boolean {
    return contextStack.none { it is SpanImpl && predicate(it) }
}

internal inline fun SpanContext.Storage.findSpan(predicate: (SpanImpl) -> Boolean): SpanImpl? {
    return contextStack.find { it is SpanImpl && predicate(it) } as? SpanImpl
}

object BugsnagPerformanceInternals {
    var currentSpanContextStack by SpanContext.Storage::contextStack
    val spanFactory get() = BugsnagPerformance.instrumentedAppState.spanFactory
}
