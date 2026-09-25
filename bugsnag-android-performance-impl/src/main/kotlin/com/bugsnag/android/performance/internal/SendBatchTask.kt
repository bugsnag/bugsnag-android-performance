package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.internal.processing.Tracer

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SendBatchTask(
    @get:VisibleForTesting
    public val delivery: Delivery,
    private val tracer: Tracer,
    private val resourceAttributes: Attributes,
    private val onSuccess: (() -> Unit)? = null,
) : AbstractTask() {
    override fun execute(): Boolean {
        val nextBatch = tracer.collectNextBatch() ?: return false
        if (nextBatch.isEmpty()) {
            return false
        }

        val result = delivery.deliver(nextBatch, resourceAttributes)
        if (result is DeliveryResult.Failed) {
            result.retryAfterMs?.let { worker?.suggestIdleWaitMs(it) }
        }
        if (result is DeliveryResult.Success) {
            onSuccess?.invoke()
        }
        return result is DeliveryResult.Success
    }

    override fun toString(): String = "SendBatch[$delivery]"
}
