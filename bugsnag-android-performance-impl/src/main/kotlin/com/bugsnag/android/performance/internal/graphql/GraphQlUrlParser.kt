package com.bugsnag.android.performance.internal.graphql

import java.net.URI

internal data class UrlGraphQlParams(
    val operationName: String?,
    val query: String?,
)

internal object GraphQlUrlParser {
    fun extractParams(url: String): UrlGraphQlParams? {
        val rawQuery = runCatching { URI(url) }.getOrNull()?.rawQuery
        val params = rawQuery?.let(::parseQueryParams).orEmpty()
        val opName = params["operationName"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val query = params["query"]?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        return if (opName == null && query == null) null else UrlGraphQlParams(opName, query)
    }

    fun parseQueryParamsFromUrl(url: String): Map<String, List<String>> {
        val rawQuery = runCatching { URI(url) }.getOrNull()?.rawQuery
        return rawQuery?.let(::parseQueryParams).orEmpty()
    }

    fun hasGraphQlGetQuery(
        url: String,
        operationTypeRegex: Regex,
    ): Boolean {
        val params = parseQueryParamsFromUrl(url)
        val queryValue = params["query"]?.firstOrNull()?.trim().orEmpty()
        val opNameValue = params["operationName"]?.firstOrNull()?.trim().orEmpty()
        val normalizedQuery = GraphQlBodyParser.normalizeDocument(queryValue)
        // Strong signal: explicit operationName with a query document.
        val hasOperationNameAndQuery = queryValue.isNotEmpty() && opNameValue.isNotEmpty()
        // Accept raw GraphQL operation text in `query`.
        val hasTypedQuery = normalizedQuery?.let(operationTypeRegex::containsMatchIn) ?: false
        // Fallback: has known GraphQL GET keys + non-empty query.
        val hasGraphQlGetKeys =
            queryValue.isNotEmpty() &&
                (params.containsKey("variables") || params.containsKey("extensions"))

        return hasOperationNameAndQuery || hasTypedQuery || hasGraphQlGetKeys
    }

    private fun parseQueryParams(rawQuery: String): Map<String, List<String>> {
        return rawQuery
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val idx = part.indexOf('=')
                val key = if (idx >= 0) part.take(idx) else part
                val value = if (idx >= 0) part.substring(idx + 1) else ""
                decodeUrlComponent(key) to decodeUrlComponent(value)
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun decodeUrlComponent(value: String): String {
        return runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
    }
}
