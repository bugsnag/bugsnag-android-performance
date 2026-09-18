package com.bugsnag.android.performance.internal.graphql

import androidx.annotation.RestrictTo
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Represents a request that may be GraphQL.
 * Used as input for [GraphQlRequestClassifier.isLikelyGraphQl].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class GraphQlRequest(
    val url: String,
    val contentType: String?,
    val body: String?,
)

/**
 * Represents a parsed GraphQL operation extracted from a request.
 * Includes operation type (query/mutation/subscription) and operation name.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class GraphQlOperation(
    val type: String,
    val name: String,
)

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object GraphQlRequestClassifier {
    // Non-empty GraphQL errors array: "errors": [ { ...
    private val nonEmptyErrorsArrayRegex =
        "\"errors\"\\s*:\\s*\\[\\s*\\{".toRegex()

    /**
     * Determines if a request is likely GraphQL using four strategies:
     * 1. Content-Type header is "application/graphql"
     * 2. URL path contains "/graphql"
     * 3. URL query parameters contain GraphQL-over-GET fields (`query`, `operationName`)
     * 4. Request body contains GraphQL operation fields or syntax
     */
    public fun isLikelyGraphQl(request: GraphQlRequest): Boolean {
        return isGraphQlContentType(request.contentType) ||
            GraphQlUrls.isGraphQlUrl(request.url) ||
            GraphQlUrls.hasGraphQlUrlQuery(request.url) ||
            GraphQlDocuments.hasGraphQlBody(request.body)
    }

    /**
     * Extracts the operation type from a GraphQL document.
     * Returns "query", "mutation", or "subscription" (defaults to "query").
     */
    public fun extractOperationType(graphqlDocument: String?): String {
        val normalizedDocument = GraphQlDocuments.normalize(graphqlDocument)
        return GraphQlDocuments.operationTypeRegex
            .find(normalizedDocument.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase(Locale.US)
            ?: "query"
    }

    /**
     * Extracts the operation name from a GraphQL request.
     * First checks the "operationName" JSON field, then looks for the name in the document itself.
     * Returns empty string if not found.
     */
    public fun extractOperationName(
        operationNameField: String?,
        graphqlDocument: String?,
    ): String {
        val fromField = operationNameField?.trim().orEmpty()
        val fromDocument =
            GraphQlDocuments.operationNameRegex
                .find(GraphQlDocuments.normalize(graphqlDocument).orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.takeIf { it.isNotBlank() }
                .orEmpty()
        return fromField.ifEmpty { fromDocument }
    }

    /**
     * Overloaded convenience function to extract operation name directly from request body.
     */
    public fun extractOperationName(body: String?): String {
        val operationNameField = GraphQlDocuments.extractOperationNameField(body)
        val graphqlDocument = GraphQlDocuments.extractGraphQlDocument(body)
        return extractOperationName(operationNameField, graphqlDocument)
    }

    /**
     * Parses a request and returns a [GraphQlOperation] if it's GraphQL, or null otherwise.
     * Combines detection with extraction of operation type and name.
     */
    public fun parseOperation(request: GraphQlRequest): GraphQlOperation? {
        if (!isLikelyGraphQl(request)) {
            return null
        }

        val bodyOperationNameField = GraphQlDocuments.extractOperationNameField(request.body)
        val bodyGraphqlDocument = GraphQlDocuments.extractGraphQlDocument(request.body)

        val urlParams = GraphQlUrls.extractGraphQlParams(request.url)
        val operationNameField = bodyOperationNameField ?: urlParams?.operationName
        val graphqlDocument = bodyGraphqlDocument ?: urlParams?.query

        val operationType = extractOperationType(graphqlDocument)
        val operationName = extractOperationName(operationNameField, graphqlDocument)

        return GraphQlOperation(operationType, operationName)
    }

    /**
     * Builds the GraphQL operation suffix used in span names.
     * Format: `<type>` or `<type>:<name>`
     * Example: `query:GetUser`
     */
    public fun buildSpanName(
        operationType: String,
        operationName: String,
    ): String {
        val normalizedType = operationType.ifBlank { "query" }.lowercase(Locale.US)
        val normalizedName = operationName.trim()

        return if (normalizedName.isEmpty()) {
            normalizedType
        } else {
            "$normalizedType:$normalizedName"
        }
    }

    /**
     * Returns true when a GraphQL JSON response contains a non-empty top-level `"errors"` array
     * (GraphQL application-level failure, even when HTTP status is 200).
     */
    public fun hasNonEmptyErrorsArray(responseBody: String?): Boolean {
        val jsonErrors = responseBody?.trim()?.takeUnless { it.isBlank() }?.let(::jsonErrorsArray)
        return when {
            responseBody.isNullOrBlank() -> false
            jsonErrors != null -> jsonErrors.length() > 0
            else -> nonEmptyErrorsArrayRegex.containsMatchIn(responseBody)
        }
    }

    private fun jsonErrorsArray(responseBody: String): JSONArray? {
        return try {
            val json = JSONObject(responseBody)
            if (json.has("errors")) json.optJSONArray("errors") else null
        } catch (_: Exception) {
            null
        }
    }

    // Detection Strategy 1: Check Content-Type header
    private fun isGraphQlContentType(contentType: String?): Boolean {
        val normalized =
            contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.US)
                ?: return false
        // Only "application/graphql" is a definitive GraphQL content-type signal.
        // "application/json" is too broad — all REST JSON POSTs use it.
        return normalized == "application/graphql"
    }
}
