package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes

internal class RetryDelivery(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery
) : Delivery by delivery {
    override fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes
    ): DeliveryResult {
        if (spans.isEmpty()) {
            return DeliveryResult.Success
        }

        val result = delivery.deliver(spans, resourceAttributes)
        if (result is DeliveryResult.Failed && result.canRetry) {
            retryQueue.add(result.payload)
        }
        return result
    }

    override fun toString(): String = "RetryDelivery($delivery)"
}
