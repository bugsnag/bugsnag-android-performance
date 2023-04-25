package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.StubDelivery
import com.bugsnag.android.performance.test.endedSpans
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RetryDeliveryTest {

    @Test
    @Suppress("LongMethod")
    fun testSuccess() {
        val attributes = Attributes()
        val stub = StubDelivery()
        val retryQueue = mock<RetryQueue>()
        val retry = RetryDelivery(retryQueue, stub)

        // No callback
        stub.reset(DeliveryResult.Success)
        retry.deliver(
            endedSpans(
                SpanImpl(
                    "test span",
                    SpanCategory.CUSTOM,
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    0L,
                    NoopSpanProcessor,
                    false,
                ),
            ),
            attributes,
        )
        assertEquals(1, stub.lastSpanDelivery?.size)

        // No new probability value
        stub.reset(DeliveryResult.Success)
        retry.deliver(
            endedSpans(
                SpanImpl(
                    "test span",
                    SpanCategory.CUSTOM,
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    0L,
                    NoopSpanProcessor,
                    false,
                ),
            ),
            attributes,
        )
        assertEquals(1, stub.lastSpanDelivery?.size)

        // Callback and new probability value
        stub.reset(DeliveryResult.Success)
        retry.deliver(
            endedSpans(
                SpanImpl(
                    "test span",
                    SpanCategory.CUSTOM,
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    0L,
                    NoopSpanProcessor,
                    false,
                ),
            ),
            attributes,
        )
        assertEquals(1, stub.lastSpanDelivery?.size)
    }

    @Test
    fun testFailRetry() {
        val attributes = Attributes()
        val stub = StubDelivery()
        val retryQueue = mock<RetryQueue>()
        val retry = RetryDelivery(retryQueue, stub)

        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        stub.reset(DeliveryResult.Failed(tracePayload, true))
        retry.deliver(
            endedSpans(
                SpanImpl(
                    "test span",
                    SpanCategory.CUSTOM,
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    0L,
                    NoopSpanProcessor,
                    false,
                ),
            ),
            attributes,
        )
        assertEquals(1, stub.lastSpanDelivery?.size)
        verify(retryQueue).add(same(tracePayload))
    }

    @Test
    fun testFailPermanent() {
        val attributes = Attributes()
        val stub = StubDelivery()
        val retryQueue = mock<RetryQueue>()
        val retry = RetryDelivery(retryQueue, stub)

        val tracePayload =
            TracePayload.createTracePayload("fake-api-key", byteArrayOf(), timestamp = 0L)

        stub.reset(DeliveryResult.Failed(tracePayload, false))
        retry.deliver(
            endedSpans(
                SpanImpl(
                    "test span",
                    SpanCategory.CUSTOM,
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    0L,
                    NoopSpanProcessor,
                    false,
                ),
            ),
            attributes,
        )
        assertEquals(1, stub.lastSpanDelivery?.size)

        // this delivery should not be added to the retry queue
        verifyNoInteractions(retryQueue)
    }
}
