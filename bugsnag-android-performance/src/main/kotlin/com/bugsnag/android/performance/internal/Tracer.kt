package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span

class Tracer : BatchingSpanProcessor() {

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
        val expectedSize = batchSize
        val currentBatchAge = SystemClock.elapsedRealtime() - lastBatchSendTime
        // only wait for a batch if the current batch is too small
        if (expectedSize < InternalDebug.spanBatchSizeSendTriggerPoint &&
            currentBatchAge < InternalDebug.workerSleepMs
        ) {
            return null
        }

        lastBatchSendTime = SystemClock.elapsedRealtime()
        return sampler.sampled(collectBatch())
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

        if (sampler.shouldKeepSpan(span)) {
            if (addToBatch(span) >= InternalDebug.spanBatchSizeSendTriggerPoint) {
                worker?.wake()
            }
        }
    }
}
