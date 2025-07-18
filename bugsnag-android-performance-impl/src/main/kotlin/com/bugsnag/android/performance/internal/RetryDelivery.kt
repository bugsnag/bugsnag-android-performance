package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class RetryDelivery(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery,
) : Delivery by delivery {
    override fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
    ): DeliveryResult {
        if (spans.isEmpty()) {
            return DeliveryResult.Success
        }

        val result = delivery.deliver(spans, resourceAttributes)
        if (result is DeliveryResult.Failed && result.canRetry) {
            Logger.d("Delivery failed - will schedule for retry")
            retryQueue.add(result.payload)
        }
        return result
    }

    override fun toString(): String = "RetryDelivery($delivery)"
}
