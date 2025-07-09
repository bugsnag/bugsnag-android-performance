package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.connectivity.shouldAttemptDelivery

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class RetryDeliveryTask(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery,
    private val connectivity: Connectivity,
) : AbstractTask() {
    override fun execute(): Boolean {
        if (!connectivity.shouldAttemptDelivery()) {
            Logger.d("Skipping RetryDeliveryTask - no connectivity.")
            return false
        }

        val nextPayload = retryQueue.next() ?: return false
        val result = delivery.deliver(nextPayload)

        // if it was delivered, or can never be delivered - delete it
        if (result is DeliveryResult.Success ||
            (result is DeliveryResult.Failed && !result.canRetry)
        ) {
            retryQueue.remove(nextPayload.timestamp)
        }

        return result is DeliveryResult.Success
    }

    override fun toString(): String = "RetryDeliveryTask"
}
