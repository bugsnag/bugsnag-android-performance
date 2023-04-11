package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Span
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class Tracer : SpanProcessor {

    private val batch = AtomicReference<SpanChain?>(null)

    /**
     * The estimated number of spans in [batch]. This is not expected to be exact but will
     * consistently track the number of spans. Specifically: the batchSize may trail the actual
     * number of items in the [batch] as the batch is mutated *before* the `batchSize`. This
     * may cause spurious [Worker.wake] calls when the batch is currently being collected and
     * added to at the same time, but this is safe and handled by [Worker.wake].
     */
    private val batchSize = AtomicInteger(0)

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
        val expectedSize = batchSize.get()
        val currentBatchAge = SystemClock.elapsedRealtime() - lastBatchSendTime
        // only wait for a batch if the current batch is too small
        if (expectedSize < InternalDebug.spanBatchSizeSendTriggerPoint &&
            currentBatchAge < InternalDebug.workerSleepMs
        ) {
            return null
        }

        lastBatchSendTime = SystemClock.elapsedRealtime()
        val nextBatchChain = batch.getAndSet(null)
        return if (nextBatchChain != null) {
            // unlinkTo separates the chain, allowing any free-floating Spans eligible for GC
            val nextBatch = nextBatchChain.unlinkTo(ArrayList())
            // reduce the tracked batchSize by the number of Spans in this batch
            batchSize.addAndGet(-nextBatch.size)
            nextBatch.reverse()

            sampler.sampled(nextBatch)
        } else emptyList()
    }

    private fun addToBatch(span: SpanImpl) {
        while (true) {
            val localBatch = batch.get()
            span.next = localBatch
            if (batch.compareAndSet(localBatch, span)) {
                break
            }
        }

        if (batchSize.incrementAndGet() >= InternalDebug.spanBatchSizeSendTriggerPoint) {
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
