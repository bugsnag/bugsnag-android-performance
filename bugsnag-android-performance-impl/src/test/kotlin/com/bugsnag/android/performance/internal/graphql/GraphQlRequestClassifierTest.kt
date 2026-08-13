package com.bugsnag.android.performance.internal.graphql

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GraphQlRequestClassifierTest {
    @Test
    fun isLikelyGraphQlReturnsTrueForGraphQlContentType() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/rest/users",
                contentType = "application/graphql; charset=utf-8",
                body = "{\"foo\":\"bar\"}",
            )

        assertTrue(GraphQlRequestClassifier.isLikelyGraphQl(request))
    }

    @Test
    fun isLikelyGraphQlReturnsTrueForGraphQlUrlPath() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/api/v1/graphql",
                contentType = "text/plain",
                body = "{\"foo\":\"bar\"}",
            )

        assertTrue(GraphQlRequestClassifier.isLikelyGraphQl(request))
    }

    @Test
    fun isLikelyGraphQlReturnsTrueForBodyInspection() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/rest/users",
                contentType = "text/plain",
                body = "{\"query\":\"query GetUser { user { id } }\"}",
            )

        assertTrue(GraphQlRequestClassifier.isLikelyGraphQl(request))
    }

    @Test
    fun isLikelyGraphQlReturnsFalseForPlainJsonPostToNonGraphQlEndpoint() {
        // application/json alone is not a sufficient GraphQL signal — every REST JSON POST
        // uses this content type. The URL and body must provide additional GraphQL signals.
        val request =
            GraphQlRequest(
                url = "https://jsonplaceholder.typicode.com/posts",
                contentType = "application/json",
                body = "{\"title\":\"Performance Example\",\"body\":\"Testing\",\"userId\":1}",
            )

        assertFalse(GraphQlRequestClassifier.isLikelyGraphQl(request))
        assertNull(GraphQlRequestClassifier.parseOperation(request))
    }

    @Test
    fun isLikelyGraphQlReturnsTrueForGraphQlJsonBodyAtAnyEndpoint() {
        // application/json + body with "query"/"operationName" keys → correctly identified
        val request =
            GraphQlRequest(
                url = "https://api.example.com/api",
                contentType = "application/json",
                body = "{\"operationName\":\"GetUser\",\"query\":\"query GetUser { user { id } }\",\"variables\":{}}",
            )

        assertTrue(GraphQlRequestClassifier.isLikelyGraphQl(request))
    }

    @Test
    fun isLikelyGraphQlReturnsFalseForNonGraphQlRequest() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/rest/users",
                contentType = "application/xml",
                body = "{\"userId\":\"123\",\"action\":\"get\"}",
            )

        assertFalse(GraphQlRequestClassifier.isLikelyGraphQl(request))
    }

    @Test
    fun extractOperationTypeDefaultsToQueryWhenNotParsable() {
        assertEquals("query", GraphQlRequestClassifier.extractOperationType("{ user { id } }"))
        assertEquals("query", GraphQlRequestClassifier.extractOperationType(null))
    }

    @Test
    fun extractOperationTypeParsesKnownOperationTypes() {
        assertEquals("query", GraphQlRequestClassifier.extractOperationType("query GetUser { user { id } }"))
        assertEquals(
            "mutation",
            GraphQlRequestClassifier.extractOperationType("mutation CreatePost { createPost { id } }"),
        )
        assertEquals(
            "subscription",
            GraphQlRequestClassifier.extractOperationType("subscription OnMessage { message { id } }"),
        )
    }

    @Test
    fun extractOperationNameRespectsPriorityOrder() {
        val graphqlDocument = "query GetUserFromDocument { user { id } }"

        assertEquals(
            "GetUserFromJson",
            GraphQlRequestClassifier.extractOperationName("GetUserFromJson", graphqlDocument),
        )

        assertEquals(
            "GetUserFromDocument",
            GraphQlRequestClassifier.extractOperationName("", graphqlDocument),
        )

        assertEquals("", GraphQlRequestClassifier.extractOperationName("", "query { user { id } }"))
    }

    @Test
    fun extractOperationNameFromBodyUsesOperationNameFieldFirst() {
        val body =
            "{\"operationName\":\"GetUser\",\"query\":\"query OtherName { user { id } }\"}"

        assertEquals("GetUser", GraphQlRequestClassifier.extractOperationName(body))
    }

    @Test
    fun parseOperationUsesDefaultQueryForLikelyGraphQlWhenBodyIsUnparsable() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/graphql",
                contentType = "application/json",
                body = "{\"foo\":\"bar\"}",
            )

        val operation = GraphQlRequestClassifier.parseOperation(request)

        assertNotNull(operation)
        assertEquals("query", operation?.type)
        assertEquals("", operation?.name)
    }

    @Test
    fun parseOperationReturnsNullWhenRequestIsNotLikelyGraphQl() {
        val request =
            GraphQlRequest(
                url = "https://api.example.com/rest/users",
                contentType = "text/html",
                body = "{\"foo\":\"bar\"}",
            )

        assertNull(GraphQlRequestClassifier.parseOperation(request))
    }

    @Test
    fun buildSpanNameUsesAnonymousFormatWhenOperationNameMissing() {
        assertEquals("query", GraphQlRequestClassifier.buildSpanName("query", ""))
        assertEquals("mutation:CreatePost", GraphQlRequestClassifier.buildSpanName("mutation", "CreatePost"))
    }
}


