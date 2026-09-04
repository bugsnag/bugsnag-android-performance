package com.bugsnag.android.performance.internal.graphql

import androidx.annotation.RestrictTo
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
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
    // Regex patterns to extract GraphQL fields and document structure
    private val operationNameFieldRegex =
        "\"operationName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    private val queryFieldRegex = "\"query\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val mutationFieldRegex = "\"mutation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val subscriptionFieldRegex =
        "\"subscription\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()

    // Detects operation type at the start of a GraphQL document
    private val operationTypeRegex =
        "^\\s*(query|mutation|subscription)\\b".toRegex(RegexOption.IGNORE_CASE)

    // Extracts the operation name (e.g., "GetUser" from "query GetUser { ... }")
    private val operationNameRegex =
        "^\\s*(?:query|mutation|subscription)\\s+([_A-Za-z][_0-9A-Za-z]*)\\b".toRegex(RegexOption.IGNORE_CASE)

    // Anonymous GraphQL selection set ("{ user { id } }"), not JSON ("{ \"key\": ... }")
    private val anonymousSelectionSetRegex =
        "^\\s*\\{\\s*[_A-Za-z]".toRegex()

    // Matches "/graphql" in URL paths
    private val graphQlPathRegex = "(^|/)graphql/?($|[?#])".toRegex(RegexOption.IGNORE_CASE)

    // Non-empty GraphQL errors array: "errors": [ { ...
    private val nonEmptyErrorsArrayRegex =
        "\"errors\"\\s*:\\s*\\[\\s*\\{".toRegex()

    // Removes GraphQL comments from documents
    private val commentRegex = "(?m)^\\s*#.*$".toRegex()

    /**
     * Determines if a request is likely GraphQL using three strategies:
     * 1. Content-Type header is "application/graphql"
     * 2. URL path contains "/graphql"
     * 3. Request body contains GraphQL operation fields or syntax
     */
    public fun isLikelyGraphQl(request: GraphQlRequest): Boolean {
        return isGraphQlContentType(request.contentType) ||
                isGraphQlUrl(request.url) ||
                hasGraphQlUrlQuery(request.url) ||
                hasGraphQlBody(request.body)
    }

    /**
     * Extracts the operation type from a GraphQL document.
     * Returns "query", "mutation", or "subscription" (defaults to "query").
     */
    public fun extractOperationType(graphqlDocument: String?): String {
        val normalizedDocument = normalizeDocument(graphqlDocument) ?: return "query"
        return operationTypeRegex
            .find(normalizedDocument)
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
        val operationName = operationNameField?.trim().orEmpty()
        if (operationName.isNotEmpty()) {
            return operationName
        }

        val normalizedDocument = normalizeDocument(graphqlDocument) ?: return ""
        return operationNameRegex
            .find(normalizedDocument)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
    }

    /**
     * Overloaded convenience function to extract operation name directly from request body.
     */
    public fun extractOperationName(body: String?): String {
        val operationNameField = extractOperationNameField(body)
        val graphqlDocument = extractGraphQlDocument(body)
        return extractOperationName(operationNameField, graphqlDocument)
    }

    private data class UrlGraphQlParams(
        val operationName: String?,
        val query: String?,
    )

    private fun extractGraphQlParamsFromUrl(url: String): UrlGraphQlParams? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val rawQuery = uri.rawQuery ?: return null

        val params = rawQuery
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val idx = part.indexOf('=')
                val key = if (idx >= 0) part.take(idx) else part
                val value = if (idx >= 0) part.substring(idx + 1) else ""
                decodeUrlComponent(key) to decodeUrlComponent(value)
            }
            .groupBy({ it.first }, { it.second })

        val opName = params["operationName"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val query = params["query"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        if (opName == null && query == null) return null
        return UrlGraphQlParams(operationName = opName, query = query)
    }

    /**
     * Parses a request and returns a [GraphQlOperation] if it's GraphQL, or null otherwise.
     * Combines detection with extraction of operation type and name.
     */
    public fun parseOperation(request: GraphQlRequest): GraphQlOperation? {
        if (!isLikelyGraphQl(request)) {
            return null
        }

        val bodyOperationNameField = extractOperationNameField(request.body)
        val bodyGraphqlDocument = extractGraphQlDocument(request.body)

        val urlParams = extractGraphQlParamsFromUrl(request.url)
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
        if (responseBody.isNullOrBlank()) {
            return false
        }

        val jsonErrors = jsonErrorsArray(responseBody.trim())
        if (jsonErrors != null) {
            return jsonErrors.length() > 0
        }

        return nonEmptyErrorsArrayRegex.containsMatchIn(responseBody)
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

    // Detection Strategy 2: Check if URL path contains "/graphql"
    private fun isGraphQlUrl(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull()
        return when {
            !path.isNullOrEmpty() -> graphQlPathRegex.containsMatchIn(path)
            else -> graphQlPathRegex.containsMatchIn(url)
        }
    }

    // Detection Strategy 3: Check if request body contains GraphQL syntax or operation fields
    private fun hasGraphQlBody(body: String?): Boolean {
        if (body.isNullOrBlank()) {
            return false
        }

        val trimmedBody = body.trim()
        val operationName = extractOperationNameField(trimmedBody)
        val document = extractGraphQlDocument(trimmedBody)

        // Classic GraphQL JSON: operationName + a document field (query/mutation/subscription).
        if (!operationName.isNullOrBlank() && document != null) {
            return true
        }

        // A "query"/"mutation"/"subscription" JSON field alone is not enough — search APIs often
        // use {"query":"shoes"}. Require the field value to look like a GraphQL document.
        if (document != null && looksLikeGraphQlDocument(document)) {
            return true
        }

        // Raw GraphQL document as the entire body (e.g. Content-Type: application/graphql)
        return looksLikeGraphQlDocument(trimmedBody)
    }

    private fun looksLikeGraphQlDocument(document: String): Boolean {
        val normalizedDocument = normalizeDocument(document) ?: return false
        if (operationTypeRegex.containsMatchIn(normalizedDocument)) {
            return true
        }
        // Anonymous GraphQL selection set: "{ user { id } }" — not JSON and not truncated junk
        // like "{invalid json content" (must include a closing brace).
        return anonymousSelectionSetRegex.containsMatchIn(normalizedDocument) &&
                normalizedDocument.contains('}')
    }

    // Detection Strategy 4: Check URL query parameters for GraphQL-over-GET
    private fun hasGraphQlUrlQuery(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val rawQuery = uri.rawQuery ?: return false

        // Parse query params safely (supports repeated keys)
        val params = rawQuery
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val idx = part.indexOf('=')
                val key = if (idx >= 0) part.take(idx) else part
                val value = if (idx >= 0) part.substring(idx + 1) else ""
                decodeUrlComponent(key) to decodeUrlComponent(value)
            }
            .groupBy({ it.first }, { it.second })

        val queryValue = params["query"]?.firstOrNull()?.trim().orEmpty()
        val opNameValue = params["operationName"]?.firstOrNull()?.trim().orEmpty()

        // Strong signal: explicit operationName with a query document
        if (queryValue.isNotEmpty() && opNameValue.isNotEmpty()) {
            return true
        }

        // Accept raw GraphQL operation text in `query`
        val normalizedQuery = normalizeDocument(queryValue)
        if (normalizedQuery != null && operationTypeRegex.containsMatchIn(normalizedQuery)) {
            return true
        }

        // Fallback: has known GraphQL GET keys + non-empty query
        return queryValue.isNotEmpty() &&
                (params.containsKey("variables") || params.containsKey("extensions"))
    }

    private fun decodeUrlComponent(value: String): String {
        return runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
    }

    // Extract the "operationName" field from JSON body
    private fun extractOperationNameField(body: String?): String? {
        return body
            ?.let(operationNameFieldRegex::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    // Extract the GraphQL document (query/mutation/subscription) from JSON body
    private fun extractGraphQlDocument(body: String?): String? {
        if (body.isNullOrBlank()) {
            return null
        }

        // Try to extract from "query" field
        val queryDocument = queryFieldRegex.find(body)?.groupValues?.getOrNull(1)
        if (queryDocument != null) {
            return decodeJsonString(queryDocument)
        }

        // Try to extract from "mutation" field
        val mutationDocument = mutationFieldRegex.find(body)?.groupValues?.getOrNull(1)
        if (mutationDocument != null) {
            return decodeJsonString(mutationDocument)
        }

        // Try to extract from "subscription" field
        val subscriptionDocument = subscriptionFieldRegex.find(body)?.groupValues?.getOrNull(1)
        if (subscriptionDocument != null) {
            return decodeJsonString(subscriptionDocument)
        }

        // If body is raw GraphQL (not JSON-wrapped), normalize and return it
        val normalizedBody = normalizeDocument(body)
        return normalizedBody?.takeIf { operationTypeRegex.containsMatchIn(it) }
    }

    // Remove comments and trim whitespace from GraphQL document
    private fun normalizeDocument(graphqlDocument: String?): String? {
        if (graphqlDocument.isNullOrBlank()) {
            return null
        }

        return graphqlDocument
            .replace(commentRegex, "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    // Decode JSON escape sequences (e.g., \" → ", \n → newline)
    private fun decodeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
