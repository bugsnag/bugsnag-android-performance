package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GraphQlSpanNamingTest {
    private val spanFactory =
        SpanFactory(
            spanProcessor = NoopSpanProcessor.INSTANCE,
            spanAttributeSource = {},
        )

    private val baseOptions = SpanOptions.startTime(1L)

    @Test
    fun graphQlSpanNameIncludesHostAndPath() {
        val span =
            spanFactory.createGraphQlSpan(
                "https://api.example.com/graphql?source=android#ignored",
                "POST",
                "query:GetUserProfile",
                baseOptions,
            )

        assertNotNull(span)
        assertEquals(
            "GraphQL api.example.com/graphql - query:GetUserProfile",
            span?.name,
        )
    }

    @Test
    fun graphQlSpanNameUsesCallbackModifiedUrl() {
        spanFactory.networkRequestCallback =
            NetworkRequestInstrumentationCallback { reqInfo ->
                reqInfo.url = "https://sanitized.example.com/private/graphql?token=secret"
            }

        val span =
            spanFactory.createGraphQlSpan(
                "https://api.example.com/graphql?token=secret",
                "POST",
                "query:GetUserProfile",
                baseOptions,
            )

        assertNotNull(span)
        assertEquals(
            "GraphQL sanitized.example.com/private/graphql - query:GetUserProfile",
            span?.name,
        )
        assertEquals(
            "https://sanitized.example.com/private/graphql?token=secret",
            span?.attributes?.get("http.url"),
        )
    }

    @Test
    fun graphQlSpanNameNormalizesLegacyNamePrefixes() {
        val span =
            spanFactory.createGraphQlSpan(
                "https://api.example.com/graphql",
                "POST",
                "GraphQL - query:GetUserProfile",
                baseOptions,
            )

        assertNotNull(span)
        assertEquals(
            "GraphQL api.example.com/graphql - query:GetUserProfile",
            span?.name,
        )
    }
}

