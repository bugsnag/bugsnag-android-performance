package com.bugsnag.android.performance.internal.processing

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.OnSpanEndCallback
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.internal.ProbabilitySampler
import com.bugsnag.android.performance.internal.Sampler
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.Worker

internal class Tracer(
    @get:VisibleForTesting
    internal val spanEndCallbacks: Array<OnSpanEndCallback>,
) : BatchingSpanProcessor() {
    private var lastBatchSendTime = SystemClock.elapsedRealtime()
    internal var sampler: Sampler = ProbabilitySampler(1.0)
    internal var worker: Worker? = null

    /**
     * Returns the next batch of spans to be sent, or `null` if the batch is not "ready" to be sent.
     * When a batch is empty, an empty collection is returned (instead of `null`). When a batch is
     * not considered "ready" (for example because it does not have enough spans) `null` will
     * be returned instead.
     */
    internal fun collectNextBatch(): Collection<SpanImpl>? {
        val currentBatchAge = SystemClock.elapsedRealtime() - lastBatchSendTime
        // only wait for a batch if the current batch is too small
        if (currentBatchSize < InternalDebug.spanBatchSizeSendTriggerPoint &&
            currentBatchAge < InternalDebug.workerSleepMs
        ) {
            return null
        }

        lastBatchSendTime = SystemClock.elapsedRealtime()
        return sampler.sampled(takeBatch())
    }

    fun forceCurrentBatch() {
        val localWorker = worker ?: return
        // simply age the "last" batch to ensure that `collectNextBatch` will return whatever is
        // in the current batch, regardless of actual age or size

        lastBatchSendTime = 0
        localWorker.wake()
    }

    override fun onEnd(span: Span) {
        if (span !is SpanImpl) return

        if (sampler.shouldKeepSpan(span) && callbacksKeepSpan(span)) {
            span.isSealed = true
            val batchSize = addToBatch(span)
            if (batchSize >= InternalDebug.spanBatchSizeSendTriggerPoint) {
                worker?.wake()
            }
        }
    }

    private fun callbacksKeepSpan(span: SpanImpl): Boolean {
        if (spanEndCallbacks.isNotEmpty()) {
            val startTime = SystemClock.elapsedRealtimeNanos()
            spanEndCallbacks.forEach {
                if (!it.onSpanEnd(span)) {
                    return false
                }
            }
            val duration = SystemClock.elapsedRealtimeNanos() - startTime
            span.attributes["bugsnag.span.callbacks_duration"] = duration
        }
        return true
    }

    override fun toString(): String {
        return "Tracer[$currentBatchSize]"
    }
}
