package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanProcessor
import org.json.JSONObject
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

internal class Tracer : AbstractBatchingSpanProcessor(), Runnable {
    private lateinit var delivery: Delivery
    private lateinit var serviceName: String

    private val resourceAttributes = Attributes()

    private var runner: Thread? = null

    @Volatile
    private var running = false

    override fun run() {
        while (running) {
            val listHead = takeSpanChain() ?: continue
            delivery.deliverSpanChain(listHead, resourceAttributes)
        }
    }

    @Synchronized
    fun start(delivery: Delivery, serviceName: String) {
        if (running) return
        running = true

        this.delivery = delivery
        this.serviceName = serviceName

        resourceAttributes["service.name"] = serviceName
        resourceAttributes["telemetry.sdk.name"] = "bugsnag.performance.android"
        resourceAttributes["telemetry.sdk.version"] = "0.0.0"

        runner = Thread(this, "Bugsnag Tracer").apply {
            isDaemon = true
            start()
        }
    }
}
