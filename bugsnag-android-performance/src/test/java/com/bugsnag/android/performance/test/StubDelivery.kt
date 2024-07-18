package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.Attributes
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.DeliveryResult
import com.bugsnag.android.performance.internal.NewProbabilityCallback
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.TracePayload
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class StubDelivery : Delivery {
    var nextResult: DeliveryResult = DeliveryResult.Success
    var lastSpanDelivery: Collection<SpanImpl>? = null

    private val lock = ReentrantLock(false)
    private val deliveryCondition = lock.newCondition()
    override var newProbabilityCallback: NewProbabilityCallback?
        set(_) = Unit
        get() = null

    fun awaitDelivery(timeout: Long = 60_000L) {
        lock.withLock {
            if (lastSpanDelivery == null) {
                deliveryCondition.await(timeout, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun reset(nextResult: DeliveryResult) {
        this.nextResult = nextResult
        lastSpanDelivery = null
    }

    override fun deliver(spans: Collection<SpanImpl>, resourceAttributes: Attributes): DeliveryResult {
        lock.withLock {
            lastSpanDelivery = spans
            deliveryCondition.signalAll()
            return nextResult
        }
    }

    override fun deliver(tracePayload: TracePayload): DeliveryResult = nextResult

    override fun fetchCurrentProbability() = Unit
}
