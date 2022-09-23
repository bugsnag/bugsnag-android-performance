package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.OtelValidator.assertTraceData
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DeliveryTest {
    @Test
    fun testEncodedSpanChain() {
        val span = SpanImpl(
            "test span",
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            testSpanProcessor,
        ).let { first ->
            first.end(1L)
            SpanImpl(
                "second span",
                SpanKind.INTERNAL,
                10L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0xbaddecaf,
                testSpanProcessor,
            ).also {
                it.end(11L)
                it.previous = first
            }
        }

        val delivery = Delivery("")
        val content = delivery.encodeSpanPayload(span)

        assertTraceData(content)
    }
}
