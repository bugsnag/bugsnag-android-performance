package com.bugsnag.android.performance.apollo.instrumentation

import com.apollographql.apollo3.api.http.HttpBody
import com.apollographql.apollo3.api.http.HttpMethod
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.bugsnag.android.performance.apollo.BugsnagPerformanceApollo
import okio.Buffer
import okio.BufferedSink

internal suspend fun makeNetworkApolloRequest(
    url: String,
    body: String,
): HttpRequest {
    val interceptor = BugsnagPerformanceApollo()
    val request =
        HttpRequest.Builder(HttpMethod.Post, url)
            .body(StringHttpBody(body))
            .build()

    val chain = CapturingHttpInterceptorChain()
    interceptor.intercept(request, chain)
    return chain.request
}

private class CapturingHttpInterceptorChain : HttpInterceptorChain {
    lateinit var request: HttpRequest

    override suspend fun proceed(request: HttpRequest): HttpResponse {
        this.request = request
        return HttpResponse.Builder(200)
            .addHeader("Content-Length", "2")
            .body(Buffer().writeUtf8("ok"))
            .build()
    }
}

private class StringHttpBody(
    private val body: String,
) : HttpBody {
    override val contentType: String = "application/json"

    override val contentLength: Long = body.toByteArray().size.toLong()

    override fun writeTo(bufferedSink: BufferedSink) {
        bufferedSink.writeUtf8(body)
    }
}

