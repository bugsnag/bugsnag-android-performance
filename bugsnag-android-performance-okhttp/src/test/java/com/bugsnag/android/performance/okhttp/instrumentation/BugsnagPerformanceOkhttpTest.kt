package com.bugsnag.android.performance.okhttp.instrumentation

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.BugsnagPerformanceImpl
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanProcessor
import com.bugsnag.android.performance.okhttp.OkhttpModule
import com.bugsnag.android.performance.okhttp.withBugsnagPerformance
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

@RunWith(RobolectricTestRunner::class)
class BugsnagPerformanceOkhttpTest {
    private lateinit var spanProcessor: CollectingSpanProcessor
    private lateinit var originalSpanProcessor: SpanProcessor

    @Before
    fun setUp() {
        OkhttpModule.tracePropagationUrls = listOf(".*".toPattern())
        originalSpanProcessor = BugsnagPerformanceImpl.spanFactory.spanProcessor
        spanProcessor = CollectingSpanProcessor()
        BugsnagPerformanceImpl.spanFactory.spanProcessor = spanProcessor
    }

    @After
    fun tearDown() {
        OkhttpModule.tracePropagationUrls = emptyList()
        BugsnagPerformanceImpl.spanFactory.spanProcessor = originalSpanProcessor
    }

    @Test
    fun testGetTraceParentHeader() {
        val request = Request.Builder()
        val mockResponse = MockResponse().setBody("hello, world!")
        val result = makeNetworkOkhttpRequest(request, mockResponse)
        val validHeader = result.headers["traceparent"]
        val matchPattern = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]".toRegex()

        assertNotNull(validHeader)
        assertTrue(validHeader!!.matches(matchPattern))
    }

    @Test
    fun testGraphQlRequestIsInterceptedWithTraceParent() {
        // GraphQL requests should be classified and still receive traceparent propagation.
        val graphqlBody =
            """{"operationName":"GetUser","query":"query GetUser { user { id } }"}"""

        val request =
            Request.Builder()
                .post(graphqlBody.toRequestBody("application/json".toMediaType()))
        val mockResponse = MockResponse().setBody("ok")

        val result = makeNetworkOkhttpRequest(request, mockResponse)
        val traceparent = result.headers["traceparent"]
        val matchPattern = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]".toRegex()

        // Trace propagation should still work for GraphQL requests.
        assertNotNull(traceparent)
        assertTrue(traceparent!!.matches(matchPattern))
    }

    @Test
    fun testNonGraphQlRequestReceivesTraceParent() {
        val plainBody =
            """{"message":"hello from test"}"""

        val request =
            Request.Builder()
                .post(plainBody.toRequestBody("application/json".toMediaType()))
        val mockResponse = MockResponse().setBody("ok")

        val result = makeNetworkOkhttpRequest(request, mockResponse)
        val traceparent = result.headers["traceparent"]
        val matchPattern = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]".toRegex()

        assertNotNull(traceparent)
        assertTrue(traceparent!!.matches(matchPattern))
    }

    @Test
    fun testGraphQlRequestRecordsRequestAndResponseContentLengthIndependently() {
        val graphqlBody =
            """{"operationName":"GetUser","query":"query GetUser { user { id } }"}"""
        val responseBody = """{"data":{"user":{"id":"123"}}}"""

        executeRequest(
            Request.Builder().post(graphqlBody.toRequestBody("application/json".toMediaType())),
            MockResponse().setBody(responseBody),
            "/graphql",
        )

        val span = spanProcessor.singleSpan()
        val requestUrl = URL(span.attributes["http.url"] as String)

        assertEquals("[GraphQL] [${requestUrl.authority}${requestUrl.path}] query:GetUser", span.name)
        assertEquals("graphql", span.attributes["bugsnag.span.category"])
        assertEquals(graphqlBody.toByteArray().size.toLong(), span.attributes["http.request_content_length"])
        assertEquals(responseBody.toByteArray().size.toLong(), span.attributes["http.response_content_length"])
        assertTrue(span.attributes["http.request_content_length"] != span.attributes["http.response_content_length"])
    }

    @Test
    fun testApplicationGraphQlContentTypeCreatesGraphQlSpanOnNonGraphQlPath() {
        val graphqlBody = "query GetUserProfile { user { id name } }"
        val responseBody = """{"data":{"user":{"id":"123","name":"Ada"}}}"""

        executeRequest(
            Request.Builder()
                .post(graphqlBody.toRequestBody("application/graphql".toMediaType())),
            MockResponse().setBody(responseBody),
            "/data",
        )

        val span = spanProcessor.singleSpan()
        val requestUrl = URL(span.attributes["http.url"] as String)

        assertEquals(
            "[GraphQL] [${requestUrl.authority}${requestUrl.path}] query:GetUserProfile",
            span.name,
        )
        assertEquals("graphql", span.attributes["bugsnag.span.category"])
        assertEquals("POST", span.attributes["http.method"])
        assertEquals("query", span.attributes["graphql.operation.type"])
        assertEquals("GetUserProfile", span.attributes["graphql.operation.name"])
        assertEquals(graphqlBody.toByteArray().size.toLong(), span.attributes["http.request_content_length"])
        assertEquals(responseBody.toByteArray().size.toLong(), span.attributes["http.response_content_length"])
    }

    @Test
    fun testPlainTextPostRequestRecordsResponseContentLengthFromResponseBody() {
        val requestBody = "hello from test"
        val responseBody = "created"

        executeRequest(
            Request.Builder().post(requestBody.toRequestBody("text/plain".toMediaType())),
            MockResponse().setBody(responseBody),
        )

        val span = spanProcessor.singleSpan()

        assertEquals("[HTTP/POST]", span.name)
        assertEquals("network", span.attributes["bugsnag.span.category"])
        assertEquals(requestBody.toByteArray().size.toLong(), span.attributes["http.request_content_length"])
        assertEquals(responseBody.toByteArray().size.toLong(), span.attributes["http.response_content_length"])
    }

    private fun executeRequest(
        request: Request.Builder,
        response: MockResponse,
        path: String = "/test",
    ) {
        val server = MockWebServer().apply {
            enqueue(response)
            start()
        }

        try {
            val client = OkHttpClient.Builder().withBugsnagPerformance().build()
            client.newCall(request.url(server.url(path)).build()).execute().use { httpResponse ->
                httpResponse.body?.string()
            }
        } finally {
            server.shutdown()
        }
    }

    private class CollectingSpanProcessor : SpanProcessor {
        private val spans = ConcurrentLinkedQueue<SpanImpl>()

        fun singleSpan(): SpanImpl {
            assertEquals(1, spans.size)
            return spans.single()
        }

        override fun onEnd(span: Span) {
            spans.add(span as SpanImpl)
        }
    }
}
