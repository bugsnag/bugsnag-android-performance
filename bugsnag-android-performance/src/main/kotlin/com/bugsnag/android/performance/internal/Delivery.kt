package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span

enum class DeliveryResult {
    SUCCESS,
    FAIL_RETRIABLE,
    FAIL_PERMANENT
}

typealias NewProbabilityCallback = ((newP: Double)->Unit)

interface Delivery {
    fun deliver(
        spans: Collection<Span>,
        resourceAttributes: Attributes,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult

    fun deliverInitialPRequest(newProbabilityCallback: NewProbabilityCallback?)
}
