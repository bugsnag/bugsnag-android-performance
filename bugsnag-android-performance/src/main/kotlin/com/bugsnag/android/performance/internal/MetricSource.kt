package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

internal interface MetricSource<T> {
    fun createStartMetrics(): T
    fun endMetrics(startMetrics: T, span: Span)
}
