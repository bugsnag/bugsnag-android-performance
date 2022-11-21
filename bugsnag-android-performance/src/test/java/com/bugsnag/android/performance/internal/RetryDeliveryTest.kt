package com.bugsnag.android.performance.internal

import android.os.SystemClock
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.endedSpans
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import java.util.concurrent.TimeUnit

class StubDelivery : Delivery {
    var nextResult: DeliveryResult = DeliveryResult.SUCCESS
    var lastAttempt: Collection<Span> = listOf()

    fun reset(nextResult: DeliveryResult) {
        this.nextResult = nextResult
        lastAttempt = listOf()
    }

    override fun deliver(
        spans: Collection<Span>,
        resourceAttributes: Attributes,
        newProbabilityCallback: NewProbabilityCallback?
    ): DeliveryResult {
        lastAttempt = spans
        return nextResult
    }
}

@RunWith(RobolectricTestRunner::class)
class RetryDeliveryTest {

    @Test
    fun testSuccess() {
        val attributes = Attributes()
        val maxAgeMs = 1_000_000_000L
        val initialProbability = 1.0
        val expectedProbability = 0.5
        val stub = StubDelivery()
        val retry = RetryDelivery(maxAgeMs, stub)

        // No callback
        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        // No new probability value
        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        // Callback and new probability value
        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)
    }

    @Test
    fun testFailRetriable() {
        val attributes = Attributes()
        val maxAgeMs = 1_000_000_000L
        val stub = StubDelivery()
        val retry = RetryDelivery(maxAgeMs, stub)
        val callbackProbability = 1.0

        stub.reset(DeliveryResult.FAIL_RETRIABLE)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        stub.reset(DeliveryResult.FAIL_RETRIABLE)
        retry.deliver(listOf(), attributes, null)
        assertEquals(1, stub.lastAttempt.size)

        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(
            endedSpans(
                Span(
                    "test span 2",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("6ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(2, stub.lastAttempt.size)

        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(listOf(), attributes, null)
        assertEquals(0, stub.lastAttempt.size)
    }

    @Test
    fun testFailTimeout() {
        val attributes = Attributes()
        val maxAgeMs = 1L
        val stub = StubDelivery()
        val retry = RetryDelivery(maxAgeMs, stub)
        val startTime = TimeUnit.NANOSECONDS.toMillis(SystemClock.elapsedRealtimeNanos())

        stub.reset(DeliveryResult.FAIL_RETRIABLE)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        SystemClock.setCurrentTimeMillis(startTime + 1000)

        stub.reset(DeliveryResult.FAIL_RETRIABLE)
        retry.deliver(listOf(), attributes, null)
        assertEquals(0, stub.lastAttempt.size)

        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(
            endedSpans(
                Span(
                    "test span 2",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("6ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        stub.reset(DeliveryResult.SUCCESS)
        retry.deliver(listOf(), attributes, null)
        assertEquals(0, stub.lastAttempt.size)
    }

    @Test
    fun testFailPermanent() {
        val attributes = Attributes()
        val maxAgeMs = 1_000_000_000L
        val stub = StubDelivery()
        val retry = RetryDelivery(maxAgeMs, stub)

        stub.reset(DeliveryResult.FAIL_PERMANENT)
        retry.deliver(
            endedSpans(
                Span(
                    "test span",
                    SpanKind.INTERNAL,
                    0L,
                    UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                    0xdecafbad,
                    testSpanProcessor,
                )
            ),
            attributes,
            null
        )
        assertEquals(1, stub.lastAttempt.size)

        retry.deliver(listOf(), attributes, null)
        assertEquals(0, stub.lastAttempt.size)
    }
}
