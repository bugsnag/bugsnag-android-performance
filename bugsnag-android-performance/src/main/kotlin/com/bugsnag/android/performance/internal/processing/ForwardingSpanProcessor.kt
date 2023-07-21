package com.bugsnag.android.performance.internal.processing

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor

/**
 * SpanProcessor implementation which can be made to forward to another SpanProcessor.
 * ForwardingSpanProcessors initially batch their spans using a [BatchingSpanProcessor] but when
 * [forwardTo] is called, any batched spans are forwarded to the new processor (along with any
 * in-flight spans that end with the `ForwardingSpanProcessor`).
 */
class ForwardingSpanProcessor : SpanProcessor {
    @Volatile
    private var backingProcessor: SpanProcessor = BatchingSpanProcessor()

    fun forwardTo(processor: SpanProcessor) {
        // replace the backingProcessor *first*, any in-flight spans will be relayed to processor
        // instead of the BatchingSpanProcessor
        val oldProcessor = backingProcessor
        backingProcessor = processor

        val batch = (oldProcessor as? BatchingSpanProcessor)?.takeBatch().orEmpty()
        batch.forEach(processor::onEnd)
    }

    /**
     * Discard all Spans targeting this ForwardingSpanProcessor. This is the same as using
     * [forwardTo] with a SpanProcessor that discards all of its Spans.
     */
    fun discard() {
        forwardTo { span ->
            (span as? SpanImpl)?.discard()
        }
    }

    override fun onEnd(span: Span) {
        backingProcessor.onEnd(span)
    }
}
