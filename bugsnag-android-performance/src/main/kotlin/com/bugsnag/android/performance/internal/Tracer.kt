package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span

class Tracer : SpanProcessor {

    @Suppress("DoubleMutabilityForCollection") // we swap out this ArrayList when we flush batches
    private var batch = ArrayList<SpanImpl>()

    private var lastBatchSendTime = SystemClock.elapsedRealtime()

    internal var sampler: Sampler = ProbabilitySampler(1.0)

    internal var worker: Worker? = null

    internal fun collectNextBatch(): Collection<SpanImpl>? = synchronized(this) {
        val batchSize = batch.size
        val currentBatchAge = SystemClock.elapsedRealtime() - lastBatchSendTime
        // only wait for a batch if the current batch is too small
        if (batchSize < InternalDebug.spanBatchSizeSendTriggerPoint &&
            currentBatchAge < InternalDebug.workerSleepMs
        ) {
            return null
        }

        val nextBatch = batch
        batch = ArrayList()
        lastBatchSendTime = SystemClock.elapsedRealtime()

        return sampler.sampled(nextBatch)
    }

    private fun addToBatch(span: SpanImpl) {
        val batchSize = synchronized(this) {
            batch.add(span)
            batch.size
        }

        if (batchSize >= InternalDebug.spanBatchSizeSendTriggerPoint) {
            worker?.wake()
        }
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
            addToBatch(span)
        }
    }
}
