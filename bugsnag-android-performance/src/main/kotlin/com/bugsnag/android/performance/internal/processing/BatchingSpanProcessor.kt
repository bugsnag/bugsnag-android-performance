package com.bugsnag.android.performance.internal.processing

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanChain
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import com.bugsnag.android.performance.internal.Worker
import com.bugsnag.android.performance.internal.unlinkTo
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

internal open class BatchingSpanProcessor : SpanProcessor {

    private val batch = AtomicReference<SpanChain?>(null)

    /**
     * The estimated number of spans in [batch]. This is not expected to be exact but will
     * consistently track the number of spans. Specifically: the batchSize may trail the actual
     * number of items in the [batch] as the batch is mutated *before* the `batchSize`. This
     * may cause spurious [Worker.wake] calls when the batch is currently being collected and
     * added to at the same time, but this is safe and handled by [Worker.wake].
     */
    private val _batchSize = AtomicInteger(0)

    /**
     * The estimated number of spans in [batch]. This value may have changed by the time it is
     * returned from this property.
     */
    val currentBatchSize: Int get() = _batchSize.get()

    /**
     * Takes the current batch of spans to be sent (which may be empty). After calling this the
     * current batch will be empty (all of its spans having been returned).
     */
    fun takeBatch(): Collection<SpanImpl> {
        val nextBatchChain = batch.getAndSet(null) ?: return emptyList()
        // unlinkTo separates the chain, allowing any free-floating Spans eligible for GC
        val batch = nextBatchChain.unlinkTo(ArrayList(currentBatchSize))
        // reduce the tracked batchSize by the number of Spans in this batch
        _batchSize.addAndGet(-batch.size)
        // reverse the batch in place, since the linked list is stored "backwards"
        batch.reverse()

        return batch
    }

    /**
     * Add the given span to the batch and return the new (estimated) batch size.
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

    override fun onEnd(span: Span) {
        if (span !is SpanImpl) return
        addToBatch(span)
    }
}
