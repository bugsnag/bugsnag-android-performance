package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext
import java.util.Deque

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

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public object BugsnagPerformanceInternals {
    public var currentSpanContextStack: Deque<SpanContext> by SpanContext.Storage::contextStack
    public val spanFactory: SpanFactory get() = BugsnagPerformance.instrumentedAppState.spanFactory
}
