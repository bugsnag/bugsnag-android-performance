package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import java.util.LinkedList
import java.util.concurrent.TimeUnit

class RetryDelivery(private val dropOlderThanMs: Long, private val delivery: Delivery): Delivery {
    private val retries = LinkedList<Span>()

    override fun deliver(spans: Collection<Span>, resourceAttributes: Attributes): DeliveryResult {
        val minimumEndTime = SystemClock.elapsedRealtimeNanos() - TimeUnit.MILLISECONDS.toNanos(dropOlderThanMs)
        val toDeliver = retries.filterTo(ArrayList<Span>()) { it.endTime >= minimumEndTime }
        retries.clear()
        spans.forEach {
            if (it.endTime >= minimumEndTime) {
                toDeliver.add(it)
            }
        }

        val result = delivery.deliver(toDeliver, resourceAttributes)
        if (result == DeliveryResult.FAIL_RETRIABLE) {
            retries.addAll(toDeliver)
        }
        return result
    }
}
