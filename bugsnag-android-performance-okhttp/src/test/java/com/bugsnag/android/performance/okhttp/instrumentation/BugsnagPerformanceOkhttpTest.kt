package com.bugsnag.android.performance.okhttp.instrumentation

import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.okhttp.OkhttpModule
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugsnagPerformanceOkhttpTest {
    private val logger = RecordingLogger()

    @Before
    fun setUp() {
        OkhttpModule.tracePropagationUrls = listOf(".*".toPattern())
        Logger.delegate = logger
    }

    @After
    fun tearDown() {
        OkhttpModule.tracePropagationUrls = emptyList()
        Logger.delegate = null
        logger.debugMessages.clear()
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
    fun testGraphQlPayloadIsLoggedAtDebugLevel() {
        val graphqlBody =
            """{"operationName":"GetUser","query":"query GetUser { user { id } }"}"""

        val request =
            Request.Builder()
                .post(graphqlBody.toRequestBody("application/json".toMediaType()))
        val mockResponse = MockResponse().setBody("ok")

        makeNetworkOkhttpRequest(request, mockResponse)

        assertTrue(
            logger.debugMessages.any {
                it.contains("Intercepted GraphQL payload") &&
                    it.contains("opType=query") &&
                    it.contains("opName=GetUser")
            },
        )
    }

    @Test
    fun testNonGraphQlPayloadIsNotLoggedAsGraphQl() {
        val plainBody =
            """{"message":"hello from test"}"""

        val request =
            Request.Builder()
                .post(plainBody.toRequestBody("application/json".toMediaType()))
        val mockResponse = MockResponse().setBody("ok")

        makeNetworkOkhttpRequest(request, mockResponse)

        assertFalse(
            logger.debugMessages.any { it.contains("Intercepted GraphQL payload") },
        )
    }

    private class RecordingLogger : Logger {
        val debugMessages = mutableListOf<String>()

        override fun e(msg: String): Unit = Unit

        override fun e(
            msg: String,
            throwable: Throwable,
        ): Unit = Unit

        override fun w(msg: String): Unit = Unit

        override fun w(
            msg: String,
            throwable: Throwable,
        ): Unit = Unit

        override fun i(msg: String): Unit = Unit

        override fun i(
            msg: String,
            throwable: Throwable,
        ): Unit = Unit

        override fun d(msg: String) {
            debugMessages.add(msg)
        }

        override fun d(
            msg: String,
            throwable: Throwable,
        ) {
            debugMessages.add(msg)
        }
    }
}
