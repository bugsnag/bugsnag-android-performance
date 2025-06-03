package com.bugsnag.android.performance.internal

internal sealed class DeliveryResult {
    object Success : DeliveryResult() {
        override fun toString(): String = "Success"
    }

    data class Failed(
        val payload: TracePayload,
        val canRetry: Boolean,
    ) : DeliveryResult() {
        override fun toString(): String = "Failed[canRetry=$canRetry]"
    }
}

internal fun interface NewProbabilityCallback {
    fun onNewProbability(newP: Double)
}

internal interface Delivery {
    var newProbabilityCallback: NewProbabilityCallback?

    fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
    ): DeliveryResult

    fun deliver(tracePayload: TracePayload): DeliveryResult

    fun fetchCurrentProbability()
}
