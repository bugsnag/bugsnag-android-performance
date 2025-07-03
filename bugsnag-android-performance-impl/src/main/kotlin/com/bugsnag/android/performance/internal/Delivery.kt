package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public sealed class DeliveryResult {
    public object Success : DeliveryResult() {
        override fun toString(): String = "Success"
    }

    public data class Failed(
        val payload: TracePayload,
        val canRetry: Boolean,
    ) : DeliveryResult() {
        override fun toString(): String = "Failed[canRetry=$canRetry]"
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public fun interface NewProbabilityCallback {
    public fun onNewProbability(newP: Double)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public interface Delivery {
    public var newProbabilityCallback: NewProbabilityCallback?

    public fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
    ): DeliveryResult

    public fun deliver(tracePayload: TracePayload): DeliveryResult

    public fun fetchCurrentProbability()
}
