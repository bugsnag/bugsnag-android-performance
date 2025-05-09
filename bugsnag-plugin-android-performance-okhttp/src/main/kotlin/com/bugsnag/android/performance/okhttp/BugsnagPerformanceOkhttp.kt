package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.NetworkRequestAttributes
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.encodeAsTraceParent
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.okhttp.OkhttpModule.Companion.tracePropagationUrls
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException

public class BugsnagPerformanceOkhttp : EventListener(), Interceptor {
    public companion object EventListenerFactory : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return BugsnagPerformanceOkhttp()
        }
    }

    private val networkSpanOptions = SpanOptions.makeCurrentContext(false)

    private val spans = SpanTracker()

    override fun callStart(call: Call) {
        val url = call.request().url.toUrl()
        val span = BugsnagPerformance.startNetworkRequestSpan(
            url,
            call.request().method,
            networkSpanOptions,
        )

        if (span != null) {
            val contentLength = call.request().body?.contentLength()
            if (contentLength != null) {
                NetworkRequestAttributes.setRequestContentLength(span, contentLength)
            }
            spans.associate(call, span = span as SpanImpl)
        }
    }

    override fun responseHeadersEnd(call: Call, response: Response) {
        val span = spans[call] ?: return
        NetworkRequestAttributes.setResponseCode(span, response.code)
        val contentLength = response.headers["Content-Length"]?.toLongOrNull() ?: -1L
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
        spans.endSpan(call)
    }

    override fun callFailed(call: Call, ioe: IOException) {
        // remove the span and discard
        spans.discardAllSpans(call)
    }

    override fun canceled(call: Call) {
        // remove the span and discard
        spans.discardAllSpans(call)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val spanContext: SpanContext? = spans[chain.call()]
            ?: SpanContext.current.takeUnless { it == SpanContext.invalid }
        val url = chain.request().url.toString()
        if (spanContext == null || !tracePropagationUrls.any { it.matcher(url).matches() }) {
            return chain.proceed(chain.request())
        }
        return chain.proceed(
            chain.request()
                .newBuilder()
                .header("traceparent", spanContext.encodeAsTraceParent())
                .build(),
        )
    }

}

public fun OkHttpClient.Builder.withBugsnagPerformance(): OkHttpClient.Builder {
    val performanceInstrumentation = BugsnagPerformanceOkhttp()
    return eventListener(performanceInstrumentation)
        .addInterceptor(performanceInstrumentation)
}
