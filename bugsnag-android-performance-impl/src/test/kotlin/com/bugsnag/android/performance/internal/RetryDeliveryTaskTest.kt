package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.connectivity.ConnectionMetering
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.connectivity.ConnectivityStatus
import com.bugsnag.android.performance.internal.connectivity.NetworkType
import com.bugsnag.android.performance.test.StubDelivery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class RetryDeliveryTaskTest {
    @Before
    fun configureLogging() {
        Logger.delegate = NoopLogger
    }

    @Test
    fun testNoConnectivity() {
        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        val delivery = StubDelivery()
        val retryQueue =
            mock<RetryQueue> {
                on { next() } doReturn tracePayload
            }
        val connectivity =
            mock<Connectivity> {
                on { connectivityStatus } doReturn
                    ConnectivityStatus(
                        false,
                        ConnectionMetering.DISCONNECTED,
                        NetworkType.CELL,
                        null,
                    )
            }
        val retryDeliveryTask = RetryDeliveryTask(retryQueue, delivery, connectivity)

        assertFalse(retryDeliveryTask.execute())
        assertNull(delivery.lastSpanDelivery)
    }

    @Test
    fun testRetrySuccessDelivery() {
        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        val delivery = StubDelivery()

        val retryQueue =
            mock<RetryQueue> {
                on { next() } doReturn tracePayload
            }
        val connectivity =
            mock<Connectivity> {
                on { connectivityStatus } doReturn
                    ConnectivityStatus(
                        true,
                        ConnectionMetering.UNMETERED,
                        NetworkType.WIFI,
                        null,
                    )
            }

        val retryDeliveryTask = RetryDeliveryTask(retryQueue, delivery, connectivity)

        delivery.nextResult = DeliveryResult.Success
        assertTrue(retryDeliveryTask.execute())

        verify(retryQueue).remove(tracePayload.timestamp)
    }

    @Test
    fun testRetryNoRetry() {
        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        val delivery = StubDelivery()

        val retryQueue =
            mock<RetryQueue> {
                on { next() } doReturn tracePayload
            }
        val connectivity =
            mock<Connectivity> {
                on { connectivityStatus } doReturn
                    ConnectivityStatus(
                        true,
                        ConnectionMetering.UNMETERED,
                        NetworkType.WIFI,
                        null,
                    )
            }

        val retryDeliveryTask = RetryDeliveryTask(retryQueue, delivery, connectivity)

        delivery.nextResult = DeliveryResult.Failed(tracePayload, false)
        assertFalse(retryDeliveryTask.execute())

        verify(retryQueue).remove(tracePayload.timestamp)
    }

    @Test
    fun testRetryFailedCanRetry() {
        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        val delivery = StubDelivery()

        val retryQueue =
            mock<RetryQueue> {
                on { next() } doReturn tracePayload
            }
        val connectivity =
            mock<Connectivity> {
                on { connectivityStatus } doReturn
                    ConnectivityStatus(
                        true,
                        ConnectionMetering.UNMETERED,
                        NetworkType.WIFI,
                        null,
                    )
            }

        val retryDeliveryTask = RetryDeliveryTask(retryQueue, delivery, connectivity)

        delivery.nextResult = DeliveryResult.Failed(tracePayload, true)
        assertFalse(retryDeliveryTask.execute())

        verify(retryQueue, never()).remove(any())
    }
}
