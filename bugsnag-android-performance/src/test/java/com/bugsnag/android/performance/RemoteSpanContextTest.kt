package com.bugsnag.android.performance

import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.UUID

internal class RemoteSpanContextTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setUp() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun testEncodeSpanContext() {
        val spanContext = RemoteSpanContext(
            traceId = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
            spanId = 0x1234567890abcdefL,
        )

        val traceParent = spanContext.encodeAsTraceParent()
        assertEquals("00-1234567890abcdef1234567890abcdef-1234567890abcdef-01", traceParent)
    }

    @Test
    fun testUnsampledSpanContext() {
        val span = spanFactory.newSpan(
            traceId = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
            spanId = 0x1234567890abcdefL,
            processor = NoopSpanProcessor,
        )

        // make sure the span is unsampled by forcing probability to 0
        span.samplingProbability = 0.0

        val traceParent = span.encodeAsTraceParent()
        assertEquals("00-1234567890abcdef1234567890abcdef-1234567890abcdef-00", traceParent)
    }

    @Test
    fun encodeAndDecode() {
        val spanContext = RemoteSpanContext(
            traceId = UUID.fromString("12345678-90ab-cdef-1234-567890abcdef"),
            spanId = 0x1234567890abcdefL,
        )

        val traceParent = spanContext.encodeAsTraceParent()
        val decoded = RemoteSpanContext.parseTraceParent(traceParent)

        assertEquals(spanContext, decoded)
    }

    @Test
    fun testNegativeIdEncoding() {
        val spanContext = RemoteSpanContext(
            traceId = UUID(-1L, -1L),
            spanId = -1L,
        )

        val traceParent = spanContext.encodeAsTraceParent()
        assertEquals("00-ffffffffffffffffffffffffffffffff-ffffffffffffffff-01", traceParent)
    }

    @Test
    fun invalidEncoding_UppercaseHex() {
        val spanContext = RemoteSpanContext.parseTraceParentOrNull(
            "00-FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF-FFFFFFFFFFFFFFFF-01",
        )
        assertNull(spanContext)
    }

    @Test
    fun invalidEncoding_NonHex() {
        val invalidTraceParent = "00-1234567890abcdef1234567890abcdef-1234567890abcdez-01"
        assertNull(RemoteSpanContext.parseTraceParentOrNull(invalidTraceParent))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidEncoding_ThrowsException() {
        val invalidTraceParent = "01-1234567890abcdef1234567890abcdef-1234567890abcdef-01"
        RemoteSpanContext.parseTraceParent(invalidTraceParent)
    }
}
