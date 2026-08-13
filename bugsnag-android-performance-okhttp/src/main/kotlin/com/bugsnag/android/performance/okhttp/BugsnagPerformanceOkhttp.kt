package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.NetworkRequestAttributes
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.encodeAsTraceParent
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import com.bugsnag.android.performance.internal.graphql.GraphQlRequest
import com.bugsnag.android.performance.internal.graphql.GraphQlRequestClassifier
import com.bugsnag.android.performance.okhttp.OkhttpModule.Companion.tracePropagationUrls
import com.bugsnag.android.performance.okhttp.util.DelegateEventListener
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okio.Buffer
import java.io.IOException

public class BugsnagPerformanceOkhttp(
    delegateEventListener: EventListener? = null,
    private val networkSpanOptions: SpanOptions = SpanOptions.makeCurrentContext(false),
) : DelegateEventListener(delegateEventListener), Interceptor {
    public constructor() : this(null, SpanOptions.makeCurrentContext(false))

    public companion object EventListenerFactory : Factory {
        override fun create(call: Call): EventListener {
            return BugsnagPerformanceOkhttp()
        }

        /**
         * The maximum number of bytes from a request body we will read to
         * classify it as GraphQL.
         */
        private const val MAX_BODY_PEEK_SIZE = 1024 * 1024L // 1MB
    }


    private val spans = SpanTracker()

    override fun callStart(call: Call) {
        super.callStart(call)
        val request = call.request()
        val url = request.url.toUrl()
        val method = request.method

        val bodyText = request.body?.let { requestBody ->
            val contentLength = requestBody.contentLength()
            if (requestBody.isOneShot() || contentLength < 0 || contentLength > MAX_BODY_PEEK_SIZE) {
                return@let null
            }

            try {
                Buffer().apply { requestBody.writeTo(this) }.readUtf8()
            } catch (ignored: Exception) {
                null
            }
        }

        val contentType = request.body?.contentType()?.toString()
            ?: request.header("Content-Type")

        val gqlRequest = GraphQlRequest(url.toString(), contentType, bodyText)
        val operation = GraphQlRequestClassifier.parseOperation(gqlRequest)

        val span = if (operation != null) {
            val spanName = GraphQlRequestClassifier.buildSpanName(operation.type, operation.name)
            BugsnagPerformance.startGraphQlRequestSpan(url, method, spanName, networkSpanOptions)
        } else {
            BugsnagPerformance.startNetworkRequestSpan(url, method, networkSpanOptions)
        }

        if (span != null) {
            val contentLength = request.body?.contentLength()
            if (contentLength != null && contentLength >= 0L) {
                NetworkRequestAttributes.setRequestContentLength(span, contentLength)
            }
            spans.associate(call, span = span as SpanImpl)
        }
    }

    override fun responseHeadersEnd(
        call: Call,
        response: Response,
    ) {
        super.responseHeadersEnd(call, response)
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

    override fun requestBodyEnd(
        call: Call,
        byteCount: Long,
    ) {
        super.requestBodyEnd(call, byteCount)
        val span = spans[call] ?: return

        // we rewrite the http.response_content_length attribute here, since this byteCount
        // will be more reliable than the Content-Length header
        if (byteCount != -1L) {
            NetworkRequestAttributes.setResponseContentLength(span, byteCount)
        }
    }

    override fun callEnd(call: Call) {
        super.callEnd(call)
        spans.endSpan(call)
    }

    override fun callFailed(
        call: Call,
        ioe: IOException,
    ) {
        super.callFailed(call, ioe)
        // remove the span and discard
        spans.discardAllSpans(call)
    }

    override fun canceled(call: Call) {
        super.canceled(call)
        // remove the span and discard
        spans.discardAllSpans(call)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val spanContext: SpanContext? =
            spans[chain.call()]
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
