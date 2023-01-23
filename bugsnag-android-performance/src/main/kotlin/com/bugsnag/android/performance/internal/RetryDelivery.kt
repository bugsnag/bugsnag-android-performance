package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes

internal class RetryDelivery(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery
) : Delivery {
    override fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult {
        if (spans.isEmpty()) {
            return DeliveryResult.Success
        }

        val result = delivery.deliver(spans, resourceAttributes, newProbabilityCallback)
        if (result is DeliveryResult.Failed && result.canRetry) {
            retryQueue.add(result.payload)
        }
        return result
    }

    override fun deliver(
        tracePayload: TracePayload,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult {
        return delivery.deliver(tracePayload, newProbabilityCallback)
    }

    override fun toString(): String = "RetryDelivery($delivery)"

    override fun fetchCurrentProbability(newPCallback: NewProbabilityCallback) {
        delivery.fetchCurrentProbability(newPCallback)
    }
}
