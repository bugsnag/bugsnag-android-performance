package com.bugsnag.android.performance.apollo

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.ApolloRequest
import com.apollographql.apollo3.api.Mutation
import com.apollographql.apollo3.api.Operation
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.Subscription
import com.apollographql.apollo3.api.http.HttpHeader
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
    private val networkSpanOptions = SpanOptions.makeCurrentContext(false)

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
        val url = try {
            URL(request.url)
        } catch (ex: MalformedURLException) {
            return null
        }

        val span = BugsnagPerformance.startNetworkRequestSpan(url, request.method.toString(), networkSpanOptions)
        request.body
            ?.contentLength
            ?.takeIf { it >= 0L }
            ?.let { contentLength ->
                span?.let { NetworkRequestAttributes.setRequestContentLength(it, contentLength) }
            }

        extractGraphQlOperation(request)?.let { operation ->
            span?.setAttribute("graphql.operation.type", operation.type)
            operation.name?.let { span?.setAttribute("graphql.operation.name", it) }
        }

        return span
    }

    private fun withTraceparentHeader(
        request: HttpRequest,
        span: Span?,
    ): HttpRequest {
        val spanContext: SpanContext? = span ?: SpanContext.current.takeUnless { it == SpanContext.invalid }
        if (spanContext == null || !ApolloModule.tracePropagationUrls.any { it.matcher(request.url).matches() }) {
            return request
        }

        return request.newBuilder()
            .addHeader("traceparent", spanContext.encodeAsTraceParent())
            .build()
    }

    private data class GraphQlOperation(
        val type: String,
        val name: String?,
    )

    private fun extractGraphQlOperation(request: HttpRequest): GraphQlOperation? {
        val operationName = request.headers.firstOrNull { it.name == INTERNAL_OPERATION_NAME_HEADER }?.value
        val operationType = request.headers.firstOrNull { it.name == INTERNAL_OPERATION_TYPE_HEADER }?.value
        if (!operationName.isNullOrBlank() && !operationType.isNullOrBlank()) {
            return GraphQlOperation(operationType, operationName)
        }

        return extractGraphQlOperation(request.body?.toUtf8String())
    }

    private fun extractGraphQlOperation(body: String?): GraphQlOperation? {
        if (body.isNullOrBlank()) {
            return null
        }

        val operationName = OPERATION_NAME_REGEX.find(body)?.groupValues?.getOrNull(1)
        val query = QUERY_REGEX.find(body)?.groupValues?.getOrNull(1)?.let(::decodeJsonString)
        val queryMatch = query?.let { OPERATION_TYPE_REGEX.find(it) }
        val operationType = queryMatch?.groupValues?.getOrNull(1)?.lowercase() ?: return null
        val operation = queryMatch.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }

        return GraphQlOperation(operationType, operationName ?: operation)
    }

    private fun decodeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
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

    private companion object {
        val OPERATION_NAME_REGEX = "\"operationName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val QUERY_REGEX = "\"query\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
        val OPERATION_TYPE_REGEX =
            "\\b(query|mutation|subscription)\\b(?:\\s+([_A-Za-z][_0-9A-Za-z]*))?".toRegex()
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

