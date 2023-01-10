package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span

sealed class DeliveryResult {
    object Success : DeliveryResult() {
        override fun toString(): String = "Success"
    }

    data class Failed(
        val payload: TracePayload,
        val canRetry: Boolean
    ) : DeliveryResult() {
        override fun toString(): String = "Failed[canRetry=${canRetry}]"
    }
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
