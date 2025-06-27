package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.processing.Tracer

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class SendBatchTask(
    @get:VisibleForTesting
    public val delivery: Delivery,
    private val tracer: Tracer,
    private val resourceAttributes: Attributes,
) : AbstractTask() {
    override fun execute(): Boolean {
        val nextBatch = tracer.collectNextBatch() ?: return false
        if (nextBatch.isNotEmpty()) {
            Logger.d("Sending a batch of ${nextBatch.size} spans from $tracer to $delivery")
        }
        val result = delivery.deliver(nextBatch, resourceAttributes)
        return nextBatch.isNotEmpty() && result is DeliveryResult.Success
    }

    override fun toString(): String = "SendBatch[$delivery]"
}
