package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanImpl

/**
 * A container object for the well-known attributes for network request spans (started
 * with [BugsnagPerformance.startNetworkRequestSpan]).
 */
object NetworkRequestAttributes {
    /**
     * Set the `"http.status_code"` attribute on the given [Span].
     *
     * @param span the `Span` measuring the HTTP request
     * @param statusCode the HTTP response code to record on the `Span`
     */
    @JvmStatic
    fun setResponseCode(span: Span, statusCode: Int) {
        (span as? SpanImpl)?.setAttribute("http.status_code", statusCode)
    }

    /**
     * Set the `"http.request_content_length"` attribute on the given [Span].
     *
     * @param span the `Span` measuring the HTTP request
     * @param contentLength the number of bytes in the request body
     */
    @JvmStatic
    fun setRequestContentLength(span: Span, contentLength: Long) {
        (span as? SpanImpl)?.setAttribute("http.request_content_length", contentLength)
    }

    /**
     * Set the `"http.response_content_length"` attribute on the given [Span].
     *
     * @param span the `Span` measuring the HTTP request
     * @param contentLength the number of bytes in the response body
     */
    @JvmStatic
    fun setResponseContentLength(span: Span, contentLength: Long) {
        (span as? SpanImpl)?.setAttribute("http.response_content_length", contentLength)
    }

    /**
     * Set the `"http.flavor"` attribute on the given [Span]. Typically one of:
     * - "1.0" for HTTP/1.0
     * - "1.1" for HTTP/1.1
     * - "2.0" for HTTP/2.0
     *
     * @param span the `Span` measuring the HTTP request
     * @param httpFlavor the HTTP flavor
     */
    @JvmStatic
    fun setHttpFlavor(span: Span, httpFlavor: String) {
        (span as? SpanImpl)?.setAttribute("http.flavor", httpFlavor)
    }
}
