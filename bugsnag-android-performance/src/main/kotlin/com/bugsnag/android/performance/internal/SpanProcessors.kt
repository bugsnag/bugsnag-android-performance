package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanProcessor
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

internal abstract class AbstractBatchingSpanProcessor : SpanProcessor {
    /*
     * The "head" of the linked-list of `Span`s current queued for delivery. The `Span`s are
     * actually stored in reverse order to keep the list logic simple. The last `Span` queued
     * for delivery is stored here (or `null` if the list is empty). Combined with the time
     * it takes to deliver a a payload this has a natural batching effect, while we avoid locks
     * when enqueuing new `Span`s for delivery.
     */
    @JvmField
    protected var tail = AtomicReference<Span?>()

    /**
     * Takes all of the `Span`s currently added to this `AbstractSpanProcessor` removing
     * them from the batch before returning. This can be thought of as a combined "get" and
     * "remove" operation.
     */
    protected fun takeSpanChain(): Span? {
        return tail.getAndSet(null)
    }

    /**
     * Put a single `Span`, or chain of `Span` objects into the current batch held by this
     * `AbstractBatchingSpanProcessor`, to be later returned by [takeSpanChain].
     */
    protected open fun putSpanChain(newSpanTail: Span) {
        while (true) {
            val oldTail = tail.get()
            newSpanTail.previous = oldTail
            if (tail.compareAndSet(oldTail, newSpanTail)) break
        }
    }

    override fun onEnd(span: Span) {
        putSpanChain(span)
    }
}

internal class BootstrapSpanProcessor : AbstractBatchingSpanProcessor() {
    // this is an invalid Span that we use to "poison" the bootstrap queue - forcing all incoming
    // spans to instead be sent to the delegate - this avoids accidentally leaving spans in the
    // bootstrap queue once the delegate has been set
    private val poisonSpan = Span("", SpanKind.INTERNAL, 0L, UUID(0L, 0L), 0L, this)

    private var delegate = AtomicReference<SpanProcessor>(null)

    /**
     * Drain all of the `Span`s in this `BootstrapSpanProcessor` to the given SpanProcessor, and
     * continue to drain any outstanding `Span` object associated with this `BootstrapSpanProcessor`
     * to the given [SpanProcessor].
     *
     * Once invoked this `BootstrapSpanProcessor` will stop storing *any* `Span` objects, and all
     * inbound Spans will be forwarded to [delegate].
     *
     * This can only be called once and any subsequent calls are no-ops and will silently have no
     * effect.
     */
    fun drainTo(delegate: SpanProcessor) {
        // the delegate can only be set once, after that this method is a no-op
        if (this.delegate.compareAndSet(null, delegate)) {
            // drain our Span chain into the new delegate
            // this is in a loop to handle cases where a Span is being added to the chain
            // between the delegate change, and us capturing the Span
            val drainedChain = tail.getAndSet(poisonSpan)
            if (drainedChain === poisonSpan || drainedChain == null) {
                return
            }

            // we unwrap the Span chain we have drained to ensure that whatever delegate
            // we are targeting doesn't have to deal with the side-effect of the Spans being
            // a chained structure
            var nextSpan = drainedChain
            while (nextSpan != null) {
                val span = nextSpan
                nextSpan = span.previous

                // unlink the span from the chain - make sure the delegate can safely read/change it
                span.previous = null
                delegate.onEnd(span)
            }
        }
    }

    override fun putSpanChain(newSpanTail: Span) {
        var pending = true
        while (pending) {
            val oldTail = tail.get()
            if (oldTail === poisonSpan) {
                // a delegate must have been set on another thread
                // we stop trying to enqueue the Span on the bootstrap queue, and send it
                // for processing on the delegate
                delegate.get()?.onEnd(newSpanTail)
                // exit the loop - the span has been delivered
                pending = false
            } else {
                // try and enqueue the new Span in the "normal" way
                newSpanTail.previous = oldTail
                if (tail.compareAndSet(oldTail, newSpanTail)) {
                    // exit the loop - the span has been added to the bootstrap chain
                    pending = false
                }
            }
        }
    }

    override fun onEnd(span: Span) {
        val capturedDelegate = delegate.get()
        if (capturedDelegate != null) {
            // if there is a delegate - then all inbound Spans go there instead of being
            // added to the bootstrap queue
            capturedDelegate.onEnd(span)
        } else {
            putSpanChain(span)
        }
    }
}

internal class Tracer(
    private val delivery: Delivery,
    private val serviceName: String
) : AbstractBatchingSpanProcessor(), Runnable {
    private val resourceAttributes = Attributes().also { attrs ->
        attrs["service.name"] = serviceName
        attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
        attrs["telemetry.sdk.version"] = "0.0.0"
    }

    @Volatile
    private var running = true

    fun stop() {
        running = false
    }

    override fun run() {
        while (running) {
            val listHead = takeSpanChain() ?: continue
            delivery.deliverSpanChain(listHead, resourceAttributes)
        }
    }
}
