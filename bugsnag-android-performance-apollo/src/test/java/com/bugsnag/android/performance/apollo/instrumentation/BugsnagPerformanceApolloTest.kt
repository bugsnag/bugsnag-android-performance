package com.bugsnag.android.performance.apollo.instrumentation

import com.bugsnag.android.performance.apollo.ApolloModule
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BugsnagPerformanceApolloTest {
    @Before
    fun setUp() {
        ApolloModule.tracePropagationUrls = listOf(".*".toPattern())
    }

    @After
    fun tearDown() {
        ApolloModule.tracePropagationUrls = emptyList()
    }

    @Test
    fun testGetTraceParentHeader() =
        runTest {
            val request =
                makeNetworkApolloRequest(
                    url = "https://graphql.example.com/graphql",
                    body = "{\"operationName\":\"GetUser\",\"query\":\"query GetUser { user { id } }\"}",
                )
            val traceparent = request.headers.firstOrNull { it.name == "traceparent" }?.value
            val matchPattern = "00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]".toRegex()

            assertNotNull(traceparent)
            assertTrue(traceparent!!.matches(matchPattern))
        }

    @Test
    fun testDoesNotSetTraceParentForNonMatchingUrl() =
        runTest {
            ApolloModule.tracePropagationUrls = listOf("^https://api\\.example\\.com/.*".toPattern())

            val request =
                makeNetworkApolloRequest(
                    url = "https://graphql.example.com/graphql",
                    body = "{\"operationName\":\"GetUser\",\"query\":\"query GetUser { user { id } }\"}",
                )

            val traceparent = request.headers.firstOrNull { it.name == "traceparent" }?.value
            assertNull(traceparent)
        }
}

