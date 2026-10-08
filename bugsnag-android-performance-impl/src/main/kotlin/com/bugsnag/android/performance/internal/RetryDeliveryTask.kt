package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.connectivity.shouldAttemptDelivery

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class RetryDeliveryTask(
    private val retryQueue: RetryQueue,
    private val delivery: Delivery,
    private val connectivity: Connectivity,
    private val onSuccess: (() -> Unit)? = null,
) : AbstractTask() {
    override fun execute(): Boolean {
        if (!connectivity.shouldAttemptDelivery()) {
            worker?.suggestIdleWaitMs(NO_CONNECTIVITY_BACKOFF_MS)
            return false
        }

        val nextPayload = retryQueue.next() ?: return false
        val result = delivery.deliver(nextPayload)

        if (result is DeliveryResult.Failed) {
            result.retryAfterMs?.let { worker?.suggestIdleWaitMs(it) }
        }

        // if it was delivered, or can never be delivered - delete it
        if (result is DeliveryResult.Success ||
            (result is DeliveryResult.Failed && !result.canRetry)
        ) {
            retryQueue.remove(nextPayload.timestamp)
        }

        if (result is DeliveryResult.Success) {
            onSuccess?.invoke()
        }

        return result is DeliveryResult.Success
    }

    override fun toString(): String = "RetryDeliveryTask"

    private companion object {
        private const val NO_CONNECTIVITY_BACKOFF_MS = 60_000L
    }
}
