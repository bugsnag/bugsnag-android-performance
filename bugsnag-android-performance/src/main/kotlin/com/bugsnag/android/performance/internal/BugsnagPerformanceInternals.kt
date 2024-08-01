package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanContext

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object BugsnagPerformanceInternals {
    public var currentSpanContextStack: SpanContextStack by SpanContext.Storage::contextStack
    public val spanFactory: SpanFactory get() = BugsnagPerformance.instrumentedAppState.spanFactory
}
