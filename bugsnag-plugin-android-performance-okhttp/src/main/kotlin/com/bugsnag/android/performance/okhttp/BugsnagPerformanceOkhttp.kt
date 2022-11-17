package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Span
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException

class BugsnagPerformanceOkhttp: EventListener() {
    companion object EventListenerFactory : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return BugsnagPerformanceOkhttp()
        }
    }

    var span: Span? = null

    override fun callStart(call: Call) {
        span = BugsnagPerformance.startNetworkSpan(call.request().url.toUrl(), call.request().method)
        val contentLength = call.request().body?.contentLength()
        if (contentLength != null) {
            span?.setAttribute("http.request_content_length", contentLength)
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        span?.setAttribute("http.status_code", response.code.toLong())
        val contentLength = response.body?.contentLength()
        if (contentLength != null) {
            span?.setAttribute("http.response_content_length", contentLength)
        }
        span?.setAttribute("http.flavor",
        when (response.protocol) {
            Protocol.HTTP_1_0 -> "1.0"
            Protocol.HTTP_1_1 -> "1.1"
            Protocol.HTTP_2 -> "2.0"
            Protocol.QUIC -> "QUIC"
            else -> "unknown"
        })
    }

    override fun callEnd(call: Call) {
        span?.end()
        span = null
    }

    override fun callFailed(call: Call, ioe: IOException) {
        span?.end()
        span = null
    }

    override fun canceled(call: Call) {
        span?.end()
        span = null
    }
}
