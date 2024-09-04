package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.HasAttributes

public interface MetricSource<T> {
    public fun createStartMetrics(): T
    public fun endMetrics(startMetrics: T, attributes: HasAttributes)
}
