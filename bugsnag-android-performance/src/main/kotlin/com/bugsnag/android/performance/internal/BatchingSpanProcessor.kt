package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Abstraction of a `SpanProcessor` that collects its [Span]s in batches.
 */
open class BatchingSpanProcessor : SpanProcessor {
    private val batch = AtomicReference<SpanChain?>(null)

    /**
     * The estimated number of spans in [batch]. This is not expected to be exact but will
     * consistently track the number of spans. Specifically: the batchSize may trail the actual
     * number of items in the [batch] as the batch is mutated *before* the `batchSize`. This
     * may cause spurious [Worker.wake] calls when the batch is currently being collected and
     * added to at the same time, but this is safe and handled by [Worker.wake].
     */
    private val _batchSize = AtomicInteger(0)

    protected val batchSize: Int get() = _batchSize.get()

    /**
     * Add the given [SpanImpl] to the current batch, and return the estimated number of spans in
     * the batch. The estimate is a "best guess" as the exact number may have changed by the time
     * the method returns (when other threads are also adding to the batch).
     *
     * @param span the span to add to the current batch
     * @return the estimated number of spans in the current batch after [span] was added
     */
    fun addToBatch(span: SpanImpl): Int {
        while (true) {
            val localBatch = batch.get()
            span.next = localBatch
            if (batch.compareAndSet(localBatch, span)) {
                break
            }
        }

        return _batchSize.incrementAndGet()
    }

    /**
     * Collect the current batch (starting a new batch at the same time) and return the batch
     * as a `Collection`.
     */
    fun collectBatch(): Collection<SpanImpl> {
        val nextBatchChain = batch.getAndSet(null)
        return if (nextBatchChain != null) {
            // unlinkTo separates the chain, making any free-floating Spans eligible for GC
            val nextBatch = nextBatchChain.unlinkTo(ArrayList())
            // reduce the tracked batchSize by the number of Spans in this batch
            _batchSize.addAndGet(-nextBatch.size)
            nextBatch.reverse()

            nextBatch
        } else emptyList()
    }

    /**
     * Discard the current batch without returning it.
     *
     * @see collectBatch
     */
    fun discardBatch() {
        batch.set(null)
    }

    /**
     * If the given [Span] is the expected [SpanImpl] type then add it to the current batch.
     *
     * @see addToBatch
     */
    override fun onEnd(span: Span) {
        if (span !is SpanImpl) return
        addToBatch(span)
    }
}
