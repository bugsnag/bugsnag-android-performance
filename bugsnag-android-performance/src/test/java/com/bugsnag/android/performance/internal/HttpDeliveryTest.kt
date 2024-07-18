package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.lastValue
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.util.Date

@RunWith(RobolectricTestRunner::class)
class HttpDeliveryTest {
    val spanFactory = TestSpanFactory()
    val spanProcessor = CollectingSpanProcessor()

    @Test
    fun noConnectivity() {
        val connectivity = mock<Connectivity> {
            on { connectivityStatus } doReturn ConnectivityStatus(
                false,
                ConnectionMetering.DISCONNECTED,
                NetworkType.CELL,
                null,
            )
        }

        val delivery = HttpDelivery(
            "http://localhost",
            "0123456789abcdef0123456789abcdef",
            connectivity,
        )

        val spans = spanFactory.newSpans(5, spanProcessor)
        val result = delivery.deliver(spans, Attributes())

        assertTrue(result is DeliveryResult.Failed)
        val failed = (result as DeliveryResult.Failed)
        assertTrue(failed.canRetry)
    }

    @Test
    fun headersFromSpans() {
        val connectivity = mock<Connectivity> {
            on { connectivityStatus } doReturn ConnectivityStatus(
                true,
                ConnectionMetering.POTENTIALLY_METERED,
                NetworkType.CELL,
                null,
            )
        }

        val connection = mock<HttpURLConnection> {
            on { responseCode } doReturn 200
            on { outputStream } doReturn ByteArrayOutputStream()
        }

        val delivery = object : HttpDelivery(
            "http://localhost",
            "0123456789abcdef0123456789abcdef",
            connectivity,
        ) {
            override fun openConnection(): HttpURLConnection = connection
        }

        val spans = spanFactory.newSpans(5, spanProcessor)
        val result = delivery.deliver(spans, Attributes())

        assertTrue(result is DeliveryResult.Success)

        verify(connection).setFixedLengthStreamingMode(any())
        verify(connection).setRequestProperty(eq("Content-Type"), eq("application/json"))
        verify(connection).setRequestProperty(eq("Content-Encoding"), eq("gzip"))
        verify(connection).setRequestProperty(eq("Bugsnag-Span-Sampling"), eq("1.0:5"))
        verify(connection).setRequestProperty(eq("Bugsnag-Sent-At"), any())
        verify(connection).setRequestProperty(
            eq("Bugsnag-Api-Key"),
            eq("0123456789abcdef0123456789abcdef"),
        )
    }

    @Test
    fun headersFromRetry() {
        val connectivity = mock<Connectivity> {
            on { connectivityStatus } doReturn ConnectivityStatus(
                true,
                ConnectionMetering.POTENTIALLY_METERED,
                NetworkType.CELL,
                null,
            )
        }

        val connection = mock<HttpURLConnection> {
            on { responseCode } doReturn 200
            on { outputStream } doReturn ByteArrayOutputStream()
        }

        val delivery = object : HttpDelivery(
            "http://localhost",
            "0123456789abcdef0123456789abcdef",
            connectivity,
        ) {
            override fun openConnection(): HttpURLConnection = connection
        }

        // Set our own Bugsnag-Sent-At header as 10 seconds ago, as it should be overwritten by
        // the HttpDelivery.deliver
        val datestamp = DateUtils.toIso8601(Date(System.currentTimeMillis() - 10_000L))

        val result = delivery.deliver(
            TracePayload.createTracePayload(
                "0123456789abcdef0123456789abcdef",
                """{"message": "Hello World"}""".toByteArray(),
                hashMapOf(
                    "Bugsnag-Sent-At" to datestamp,
                ),
            ),
        )

        assertTrue(result is DeliveryResult.Success)

        val sentAtCaptor = ArgumentCaptor.forClass(String::class.java)

        verify(connection).setFixedLengthStreamingMode(any())
        verify(connection).setRequestProperty(eq("Content-Type"), eq("application/json"))

        // We expect setRequestProperty to be called twice for this specific request
        // 1) for the value specified in createTracePayload
        // 2) for the overwrite in HttpDelivery
        verify(connection, times(2)).setRequestProperty(
            eq("Bugsnag-Sent-At"),
            sentAtCaptor.capture(),
        )

        verify(connection).setRequestProperty(
            eq("Bugsnag-Api-Key"),
            eq("0123456789abcdef0123456789abcdef"),
        )

        assertNotNull(sentAtCaptor.lastValue)
        assertNotEquals(datestamp, sentAtCaptor.lastValue)
    }
}
