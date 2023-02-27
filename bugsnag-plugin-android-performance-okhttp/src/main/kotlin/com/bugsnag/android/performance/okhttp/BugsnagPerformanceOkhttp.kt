package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.internal.SpanImpl
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class BugsnagPerformanceOkhttp : EventListener() {
    companion object EventListenerFactory : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return BugsnagPerformanceOkhttp()
        }
    }

    private val spans = ConcurrentHashMap<Call, SpanImpl>()

    override fun callStart(call: Call) {
        val url = call.request().url.toUrl()
        val span = BugsnagPerformance.startNetworkRequestSpan(url, call.request().method)
            as SpanImpl

        val contentLength = call.request().body?.contentLength()
        if (contentLength != null) {
            span.setAttribute("http.request_content_length", contentLength)
        }

        spans[call] = span
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val span = spans[call] ?: return
        span.setAttribute("http.status_code", response.code.toLong())
        val contentLength = response.body?.contentLength()
        if (contentLength != null) {
            span.setAttribute("http.response_content_length", contentLength)
        }
        span.setAttribute(
            "http.flavor",
            when (response.protocol) {
                Protocol.HTTP_1_0 -> "1.0"
                Protocol.HTTP_1_1 -> "1.1"
                Protocol.HTTP_2 -> "2.0"
                Protocol.QUIC -> "QUIC"
                else -> "unknown"
            }
        )
    }

    override fun callEnd(call: Call) {
        spans.remove(call)?.end()
    }

    override fun callFailed(call: Call, ioe: IOException) {
        spans.remove(call)?.end()
    }

    override fun canceled(call: Call) {
        spans.remove(call)?.end()
    }
}
