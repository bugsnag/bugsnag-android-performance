package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Span

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface MetricSource<T> {
    public fun createStartMetrics(): T
    public fun endMetrics(startMetrics: T, span: Span)
}