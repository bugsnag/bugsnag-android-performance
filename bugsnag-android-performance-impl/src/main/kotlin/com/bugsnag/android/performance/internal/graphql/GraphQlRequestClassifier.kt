package com.bugsnag.android.performance.internal.graphql

import androidx.annotation.RestrictTo
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

    /**
     * Determines if a request is likely GraphQL using three strategies:
     * 1. Content-Type header is "application/graphql"
     * 2. URL path contains "/graphql"
     * 3. Request body contains GraphQL operation fields or syntax
     */
    public fun isLikelyGraphQl(request: GraphQlRequest): Boolean {
        return isGraphQlContentType(request.contentType) ||
                isGraphQlUrl(request.url) ||
                GraphQlUrlParser.hasGraphQlGetQuery(request.url, operationTypeRegex) ||
                hasGraphQlBody(request.body)
    }

    /**
     * Extracts the operation type from a GraphQL document.
     * Returns "query", "mutation", or "subscription" (defaults to "query").
     */
    public fun extractOperationType(graphqlDocument: String?): String {
        val normalizedDocument =
            GraphQlBodyParser.normalizeDocument(graphqlDocument) ?: return "query"
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
        val resolvedOperationName =
            if (operationName.isNotEmpty()) {
                operationName
            } else {
                GraphQlBodyParser.normalizeDocument(graphqlDocument)
                    ?.let { normalizedDocument ->
                        operationNameRegex
                            .find(normalizedDocument)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.takeIf { it.isNotBlank() }
                            .orEmpty()
                    }
                    .orEmpty()
            }
        return resolvedOperationName
    }

    /**
     * Overloaded convenience function to extract operation name directly from request body.
     */
    public fun extractOperationName(body: String?): String {
        val operationNameField = GraphQlBodyParser.extractOperationNameField(body)
        val graphqlDocument = GraphQlBodyParser.extractGraphQlDocument(body, operationTypeRegex)
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

        val bodyOperationNameField = GraphQlBodyParser.extractOperationNameField(request.body)
        val bodyGraphqlDocument =
            GraphQlBodyParser.extractGraphQlDocument(request.body, operationTypeRegex)

        val urlParams = GraphQlUrlParser.extractParams(request.url)
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
        val isLikelyGraphQlBody =
            if (body.isNullOrBlank()) {
                false
            } else {
                val trimmedBody = body.trim()
                val operationName = GraphQlBodyParser.extractOperationNameField(trimmedBody)
                val document =
                    GraphQlBodyParser.extractGraphQlDocument(trimmedBody, operationTypeRegex)

                // Classic GraphQL JSON: operationName + a document field (query/mutation/subscription).
                val hasOperationNameAndDocument = !operationName.isNullOrBlank() && document != null

                // A "query"/"mutation"/"subscription" JSON field alone is not enough — search APIs often
                // use {"query":"shoes"}. Require the field value to look like a GraphQL document.
                val hasDocumentThatLooksGraphQl =
                    document != null && looksLikeGraphQlDocument(document)

                // Raw GraphQL document as the entire body (e.g. Content-Type: application/graphql).
                hasOperationNameAndDocument ||
                        hasDocumentThatLooksGraphQl ||
                        looksLikeGraphQlDocument(
                            trimmedBody,
                        )
            }
        return isLikelyGraphQlBody
    }

    private fun looksLikeGraphQlDocument(document: String): Boolean {
        val normalizedDocument = GraphQlBodyParser.normalizeDocument(document)
        val looksLikeGraphQl =
            if (normalizedDocument == null) {
                false
            } else {
                // Anonymous GraphQL selection set: "{ user { id } }" — not JSON and not truncated junk
                // like "{invalid json content" (must include a closing brace).
                operationTypeRegex.containsMatchIn(normalizedDocument) ||
                        (anonymousSelectionSetRegex.containsMatchIn(
                            normalizedDocument,
                        ) &&
                                normalizedDocument.contains(
                                    '}',
                                )
                                )
            }
        return looksLikeGraphQl
    }
}
