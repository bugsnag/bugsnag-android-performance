package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.DeliveryResult
import com.bugsnag.android.performance.internal.NewProbabilityCallback
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class StubDelivery : Delivery {
    var nextResult: DeliveryResult = DeliveryResult.SUCCESS
    var lastAttempt: Collection<Span>? = null

    private val lock = ReentrantLock(false)
    private val deliveryCondition = lock.newCondition()

    fun awaitDelivery(timeout: Long = 60_000L) {
        lock.withLock {
            if (lastAttempt == null) {
                deliveryCondition.await(timeout, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun reset(nextResult: DeliveryResult) {
        this.nextResult = nextResult
        lastAttempt = null
    }

    override fun deliver(
        spans: Collection<Span>,
        resourceAttributes: Attributes,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult {
        lock.withLock {
            lastAttempt = spans
            deliveryCondition.signalAll()
            return nextResult
        }
    }

    override fun fetchCurrentProbability(newPCallback: NewProbabilityCallback) = Unit
}
