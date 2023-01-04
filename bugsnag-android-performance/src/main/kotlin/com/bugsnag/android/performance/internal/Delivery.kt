package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span

sealed class DeliveryResult {
    object Success : DeliveryResult()

    data class Failed(
        val payload: TracePayload,
        val canRetry: Boolean
    ) : DeliveryResult()
}

fun interface NewProbabilityCallback {
    fun onNewProbability(newP: Double)
}

interface Delivery {
    fun deliver(
        spans: Collection<Span>,
        resourceAttributes: Attributes,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult

    fun deliver(
        tracePayload: TracePayload,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult

    fun fetchCurrentProbability(newPCallback: NewProbabilityCallback)
}
