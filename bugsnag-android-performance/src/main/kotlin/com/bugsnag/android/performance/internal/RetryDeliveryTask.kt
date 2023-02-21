package com.bugsnag.android.performance.internal

internal class RetryDeliveryTask(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery,
    private val connectivity: Connectivity
) : AbstractTask() {
    override fun execute(): Boolean {
        if (!connectivity.hasConnection) {
            return false;
        }

        val nextPayload = retryQueue.next() ?: return false
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
