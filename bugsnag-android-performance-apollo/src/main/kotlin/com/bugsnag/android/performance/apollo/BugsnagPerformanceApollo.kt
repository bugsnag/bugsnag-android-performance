package com.bugsnag.android.performance.apollo

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.http.HttpBody
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.interceptor.ApolloInterceptor
import com.apollographql.apollo3.interceptor.ApolloInterceptorChain
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.NetworkRequestAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.encodeAsTraceParent
import com.bugsnag.android.performance.internal.graphql.GraphQlRequest
import com.bugsnag.android.performance.internal.graphql.GraphQlRequestClassifier
import java.net.MalformedURLException
import java.net.URL
import kotlinx.coroutines.flow.Flow
import okio.Buffer

internal class BugsnagPerformanceApolloOperationInterceptor : ApolloInterceptor {
    override fun <D : Operation.Data> intercept(
        request: ApolloRequest<D>,
        chain: ApolloInterceptorChain,
    ): Flow<com.apollographql.apollo3.api.ApolloResponse<D>> {
        val operationName = request.operation.name()
        val operationType = request.operation.operationTypeName()

        Logger.d("Apollo operation: name=$operationName, type=$operationType")

        return chain.proceed(
            request.newBuilder()
                .addHttpHeader(INTERNAL_OPERATION_NAME_HEADER, operationName)
                .addHttpHeader(INTERNAL_OPERATION_TYPE_HEADER, operationType)
                .build(),
        )
    }
}

public class BugsnagPerformanceApollo : HttpInterceptor {
    private val networkSpanOptions = SpanOptions.makeCurrentContext(false).setFirstClass(true)

    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain,
    ): HttpResponse {
        val span = createNetworkSpan(request)
        val requestWithTraceParent = withTraceparentHeader(stripInternalHeaders(request), span)

        return try {
            val response = chain.proceed(requestWithTraceParent)
            if (span != null) {
                NetworkRequestAttributes.setResponseCode(span, response.statusCode)
                response.headers
                    .firstOrNull { it.name.equals("Content-Length", ignoreCase = true) }
                    ?.value
                    ?.toLongOrNull()
                    ?.let { NetworkRequestAttributes.setResponseContentLength(span, it) }
            }
            response
        } finally {
            span?.end()
        }
    }

    override fun dispose(): Unit = Unit

    private fun createNetworkSpan(request: HttpRequest): Span? {
        val url =
            try {
                URL(request.url)
            } catch (ex: MalformedURLException) {
                return null
            }

        val method = request.method.toString()
        val operation = resolveGraphQlOperation(request)
        val span =
            if (operation != null) {
                val spanName =
                    GraphQlRequestClassifier.buildSpanName(operation.first, operation.second)
                BugsnagPerformance.startGraphQlRequestSpan(
                    url,
                    method,
                    spanName,
                    networkSpanOptions,
                )
            } else {
                // Not a GraphQL request — create a standard HTTP network span.
                BugsnagPerformance.startNetworkRequestSpan(
                    url,
                    method,
                    networkSpanOptions,
                )
            }

        setRequestContentLength(span, request)
        return span
    }

    private fun resolveGraphQlOperation(request: HttpRequest): Pair<String, String>? {
        // Fast path: Apollo operation interceptor already extracted metadata via internal headers.
        val internalName =
            request.headers.firstOrNull { it.name == INTERNAL_OPERATION_NAME_HEADER }?.value
        val internalType =
            request.headers.firstOrNull { it.name == INTERNAL_OPERATION_TYPE_HEADER }?.value

        val operation: Pair<String, String>? =
            if (!internalName.isNullOrBlank() && !internalType.isNullOrBlank()) {
                internalType to internalName
            } else {
                // Fallback: use the shared 3-method classifier (content-type, URL, body).
                val contentType =
                    request.headers.firstOrNull {
                        it.name.equals("content-type", ignoreCase = true)
                    }?.value
                val body = request.body?.toUtf8String()
                val gqlRequest = GraphQlRequest(request.url, contentType, body)
                GraphQlRequestClassifier.parseOperation(gqlRequest)?.let { parsed ->
                    parsed.type to parsed.name
                }
            }

        return operation
    }

    private fun setRequestContentLength(
        span: Span?,
        request: HttpRequest,
    ) {
        request.body?.contentLength?.takeIf { it >= 0L }?.let { contentLength ->
            span?.let { NetworkRequestAttributes.setRequestContentLength(it, contentLength) }
        }
    }

    private fun withTraceparentHeader(
        request: HttpRequest,
        span: Span?,
    ): HttpRequest {
        val spanContext: SpanContext? =
            span ?: SpanContext.current.takeUnless { it == SpanContext.invalid }
        if (spanContext == null || !ApolloModule.tracePropagationUrls.any {
                it.matcher(request.url).matches()
            }
        ) {
            return request
        }

        return request.newBuilder()
            .addHeader("traceparent", spanContext.encodeAsTraceParent())
            .build()
    }

    private fun HttpBody.toUtf8String(): String? {
        return try {
            Buffer().use { buffer ->
                writeTo(buffer)
                buffer.readUtf8()
            }
        } catch (ignored: Exception) {
            null
        }
    }

    private fun stripInternalHeaders(request: HttpRequest): HttpRequest {
        val filteredHeaders =
            request.headers.filter {
                it.name != INTERNAL_OPERATION_NAME_HEADER &&
                        it.name != INTERNAL_OPERATION_TYPE_HEADER
            }

        return request.newBuilder()
            .headers(filteredHeaders)
            .build()
    }
}

public fun ApolloClient.Builder.withBugsnagPerformance(): ApolloClient.Builder {
    return addInterceptor(BugsnagPerformanceApolloOperationInterceptor())
        .addHttpInterceptor(BugsnagPerformanceApollo())
}

private fun Operation<*>.operationTypeName(): String {
    return when (this) {
        is Query<*> -> "query"
        is Mutation<*> -> "mutation"
        is Subscription<*> -> "subscription"
        else -> "unknown"
    }
}

private const val INTERNAL_OPERATION_NAME_HEADER = "x-bsg-operation-name"
private const val INTERNAL_OPERATION_TYPE_HEADER = "x-bsg-operation-type"
