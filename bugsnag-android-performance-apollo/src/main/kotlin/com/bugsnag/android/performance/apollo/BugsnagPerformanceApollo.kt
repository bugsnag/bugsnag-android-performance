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
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.graphql.GraphQlOperation
import com.bugsnag.android.performance.internal.graphql.GraphQlRequest
import com.bugsnag.android.performance.internal.graphql.GraphQlRequestClassifier
import com.bugsnag.android.performance.internal.graphql.GraphQlSpanStatus
import kotlinx.coroutines.flow.Flow
import okio.Buffer
import java.net.MalformedURLException
import java.net.URL

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
                applyGraphQlStatus(span, response)
                response.headers
                    .firstOrNull { it.name.equals("Content-Length", ignoreCase = true) }
                    ?.value
                    ?.toLongOrNull()
                    ?.let { NetworkRequestAttributes.setResponseContentLength(span, it) }
            }
            response
        } catch (ignored: Exception) {
            (span as? SpanImpl)?.let(GraphQlSpanStatus::applyFailure)
            throw ignored
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

        val operation = resolveGraphQlOperation(request)
        val span =
            if (operation != null) {
                val spanName = GraphQlRequestClassifier.buildSpanName(operation.type, operation.name)
                BugsnagPerformance.startGraphQlRequestSpan(
                    url,
                    request.method.toString(),
                    spanName,
                    networkSpanOptions,
                )
            } else {
                BugsnagPerformance.startNetworkRequestSpan(
                    url,
                    request.method.toString(),
                    networkSpanOptions,
                )
            }

        setRequestContentLength(span, request)
        return span
    }

    private fun resolveGraphQlOperation(request: HttpRequest): GraphQlOperation? {
        val internalName =
            request.headers.firstOrNull { it.name == INTERNAL_OPERATION_NAME_HEADER }?.value
        val internalType =
            request.headers.firstOrNull { it.name == INTERNAL_OPERATION_TYPE_HEADER }?.value
        if (!internalName.isNullOrBlank() && !internalType.isNullOrBlank()) {
            return GraphQlOperation(internalType, internalName)
        }

        val contentType =
            request.headers.firstOrNull {
                it.name.equals("content-type", ignoreCase = true)
            }?.value
        val gqlRequest = GraphQlRequest(request.url, contentType, request.body?.toUtf8String())
        return GraphQlRequestClassifier.parseOperation(gqlRequest)
    }

    private fun setRequestContentLength(
        span: Span?,
        request: HttpRequest,
    ) {
        val contentLength = request.body?.contentLength
        if (span != null && contentLength != null && contentLength >= 0L) {
            NetworkRequestAttributes.setRequestContentLength(span, contentLength)
        }
    }

    private fun applyGraphQlStatus(
        span: Span,
        response: HttpResponse,
    ) {
        val spanImpl = span as? SpanImpl ?: return
        if (spanImpl.category != SpanCategory.GRAPHQL) {
            return
        }

        GraphQlSpanStatus.applyHttpStatus(spanImpl, response.statusCode)
        GraphQlSpanStatus.applyResponseBody(spanImpl, peekResponseBody(response))
    }

    private fun peekResponseBody(response: HttpResponse): String? {
        val body = response.body ?: return null
        return try {
            val peekSource = body.peek()
            peekSource.request(MAX_RESPONSE_BODY_INSPECTION_BYTES)
            peekSource.readUtf8(minOf(peekSource.buffer.size, MAX_RESPONSE_BODY_INSPECTION_BYTES))
        } catch (_: Exception) {
            null
        }
    }

    private fun withTraceparentHeader(
        request: HttpRequest,
        span: Span?,
    ): HttpRequest {
        val spanContext: SpanContext? =
            span ?: SpanContext.current.takeUnless { it == SpanContext.invalid }
        if (spanContext == null ||
            !ApolloModule.tracePropagationUrls.any {
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
private const val MAX_RESPONSE_BODY_INSPECTION_BYTES = 64 * 1024L
