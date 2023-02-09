package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.StubDelivery
import org.junit.Assert
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class RetryDeliveryTaskTest {

    @Test
    fun testNoConnectivity() {
        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        val delivery = StubDelivery()
        val retryQueue = mock<RetryQueue>() {
            on { next() } doReturn tracePayload
        }
        val connectivity = mock<Connectivity> {
            on { hasConnection } doReturn false
        }
        val retryDeliveryTask = RetryDeliveryTask(retryQueue, delivery, connectivity)

        Assert.assertFalse(retryDeliveryTask.execute())
        Assert.assertNull(delivery.lastSpanDelivery)
    }
}
