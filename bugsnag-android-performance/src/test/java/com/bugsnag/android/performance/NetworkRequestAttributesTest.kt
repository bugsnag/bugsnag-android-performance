package com.bugsnag.android.performance

import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class NetworkRequestAttributesTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setup() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun setResponseCode() {
        val expectedCodes = intArrayOf(100, 200, 400, 404, 500)
        for (code in expectedCodes) {
            val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
            NetworkRequestAttributes.setResponseCode(span, code)

            // Integer attributes are stored as Long, not Int
            val statusCodeAttr = span.attributes["http.status_code"] as Long

            assertEquals(code, statusCodeAttr.toInt())
        }
    }

    @Test
    fun setRequestContentLength() {
        val requestBodySize = 1024L
        val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
        NetworkRequestAttributes.setRequestContentLength(span, requestBodySize)

        val requestLength = span.attributes["http.request_content_length"] as Long

        assertEquals(requestBodySize, requestLength)
    }

    @Test
    fun setUncompressedRequestContentLength() {
        val requestBodySize = 1024L
        val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
        NetworkRequestAttributes.setUncompressedRequestContentLength(span, requestBodySize)

        val requestLength = span.attributes["http.request_content_length_uncompressed"] as Long

        assertEquals(requestBodySize, requestLength)
    }

    @Test
    fun setResponseContentLength() {
        val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
        NetworkRequestAttributes.setResponseContentLength(span, Long.MAX_VALUE)

        val responseLength = span.attributes["http.response_content_length"] as Long

        assertEquals(Long.MAX_VALUE, responseLength)
    }

    @Test
    fun setUncompressedResponseContentLength() {
        val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
        NetworkRequestAttributes.setUncompressedResponseContentLength(span, Long.MAX_VALUE)

        val responseLength = span.attributes["http.response_content_length_uncompressed"] as Long

        assertEquals(Long.MAX_VALUE, responseLength)
    }

    @Test
    fun setHttpFlavor() {
        val span = spanFactory.openSpan(NoopSpanProcessor.INSTANCE)
        NetworkRequestAttributes.setHttpFlavor(span, "1.1")
        val flavor = span.attributes["http.flavor"] as String
        assertEquals("1.1", flavor)
    }

    /**
     * NetworkRequestAttributes should not cause exceptions when a non `SpanImpl` object is specified.
     */
    @Test
    fun supportsMockSpans() {
        val span = mock<Span>()
        NetworkRequestAttributes.setResponseCode(span, 200)
        NetworkRequestAttributes.setRequestContentLength(span, 64)
        NetworkRequestAttributes.setResponseContentLength(span, Long.MAX_VALUE)
        NetworkRequestAttributes.setHttpFlavor(span, "2.0")
    }
}
