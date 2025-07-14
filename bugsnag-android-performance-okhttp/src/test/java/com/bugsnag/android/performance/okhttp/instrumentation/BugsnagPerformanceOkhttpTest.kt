package com.bugsnag.android.performance.okhttp.instrumentation

import com.bugsnag.android.performance.okhttp.OkhttpModule
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugsnagPerformanceOkhttpTest {
    @Before
    fun setUp() {
        OkhttpModule.tracePropagationUrls = listOf(".*".toPattern())
    }

    @After
    fun tearDown() {
        OkhttpModule.tracePropagationUrls = emptyList()
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
}
