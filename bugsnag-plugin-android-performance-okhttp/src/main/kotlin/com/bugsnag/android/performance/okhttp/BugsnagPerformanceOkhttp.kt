package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.NetworkRequestAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.SpanImpl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.internal.headersContentLength
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class BugsnagPerformanceOkhttp : EventListener() {
    companion object EventListenerFactory : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return BugsnagPerformanceOkhttp()
        }
    }

    private val networkSpanOptions = SpanOptions.DEFAULTS.makeCurrentContext(false)

    private val spans = ConcurrentHashMap<Call, Span>()

    override fun callStart(call: Call) {
        val url = call.request().url.toUrl()
        val span = BugsnagPerformance.startNetworkRequestSpan(
            url,
            call.request().method,
            networkSpanOptions,
        )

        val contentLength = call.request().body?.contentLength()
        if (contentLength != null) {
            NetworkRequestAttributes.setRequestContentLength(span, contentLength)
        }

        spans[call] = span
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val span = spans[call] ?: return
        NetworkRequestAttributes.setResponseCode(span, response.code)
        val contentLength = response.headersContentLength()
        if (contentLength != -1L) {
            NetworkRequestAttributes.setResponseContentLength(span, contentLength)
        }
        NetworkRequestAttributes.setHttpFlavor(
            span,
            when (response.protocol) {
                Protocol.HTTP_1_0 -> "1.0"
                Protocol.HTTP_1_1 -> "1.1"
                Protocol.HTTP_2 -> "2.0"
                Protocol.QUIC -> "QUIC"
                else -> "unknown"
            },
        )
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        val span = spans[call] ?: return

        // we rewrite the http.response_content_length attribute here, since this byteCount
        // will be more reliable than the Content-Length header
        if (byteCount != -1L) {
            NetworkRequestAttributes.setResponseContentLength(span, byteCount)
        }
    }

    override fun callEnd(call: Call) {
        spans.remove(call)?.end()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // remove the span and discard
        (spans.remove(call) as? SpanImpl)?.discard()
    }

    override fun canceled(call: Call) {
        // remove the span and discard
        (spans.remove(call) as? SpanImpl)?.discard()
    }
}
