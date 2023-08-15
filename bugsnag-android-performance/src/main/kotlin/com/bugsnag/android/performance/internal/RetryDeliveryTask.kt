package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger

internal class RetryDeliveryTask(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery,
    private val connectivity: Connectivity
) : AbstractTask() {
    override fun execute(): Boolean {
        if (!connectivity.shouldAttemptDelivery()) {
            Logger.d("Skipping RetryDeliveryTask - no connectivity.")
            return false;
        }

        val nextPayload = retryQueue.next() ?: return false
        Logger.d("Attempting retry from queue ${nextPayload.timestamp}")
        val result = delivery.deliver(nextPayload)

        // if it was delivered, or can never be delivered - delete it
        if (result is DeliveryResult.Success ||
            (result is DeliveryResult.Failed && !result.canRetry)
        ) {
            retryQueue.remove(nextPayload.timestamp)
        }
        return true
    }

    override fun toString(): String = "RetryDeliveryTask"
}
