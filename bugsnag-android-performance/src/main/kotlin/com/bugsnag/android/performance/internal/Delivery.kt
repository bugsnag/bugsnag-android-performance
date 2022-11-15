package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span

enum class DeliveryResult {
    SUCCESS,
    FAIL_RETRIABLE,
    FAIL_PERMANENT
}

interface Delivery {
    fun deliver(spans: Collection<Span>, resourceAttributes: Attributes): DeliveryResult
}
