package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanProcessor
import java.util.concurrent.atomic.AtomicReference

internal class Tracer(private val delivery: Delivery) : Runnable, SpanProcessor {
    /*
     * The "head" of the linked-list of `Span`s current queued for delivery. The `Span`s are
     * actually stored in reverse order to keep the list logic simple. The last `Span` queued
     * for delivery is stored here (or `null` if the list is empty). Combined with the time
     * it takes to deliver a a payload this has a natural batching effect, while we avoid locks
     * when enqueuing new `Span`s for delivery.
     */
    private var tail = AtomicReference<SpanImpl?>()

    @Volatile
    private var running = true

    fun stop() {
        running = false
    }

    override fun run() {
        while (running) {
            val listHead = tail.getAndSet(null) ?: continue
            delivery.deliverSpanChain(listHead)
        }
    }

    override fun onEnd(span: Span) {
        if (span !is SpanImpl) return
        while (true) {
            val oldHead = tail.get()
            span.previous = oldHead
            if (tail.compareAndSet(oldHead, span)) break
        }
    }
}
