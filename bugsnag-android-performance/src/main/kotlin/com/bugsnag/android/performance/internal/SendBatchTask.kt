package com.bugsnag.android.performance.internal

import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.processing.Tracer

internal class SendBatchTask(
    @get:VisibleForTesting
    val delivery: Delivery,
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
