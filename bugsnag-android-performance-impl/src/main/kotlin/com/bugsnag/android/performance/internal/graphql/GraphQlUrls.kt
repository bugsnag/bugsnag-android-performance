package com.bugsnag.android.performance.internal.graphql

import java.net.URI

internal object GraphQlUrls {
    data class Params(
        val operationName: String?,
        val query: String?,
    )

    // Matches "/graphql" in URL paths
    private val graphQlPathRegex = "(^|/)graphql/?($|[?#])".toRegex(RegexOption.IGNORE_CASE)

    fun isGraphQlUrl(url: String): Boolean {
        val path = runCatching { URI(url).path }.getOrNull()
        return graphQlPathRegex.containsMatchIn(path?.takeIf { it.isNotEmpty() } ?: url)
    }

    fun hasGraphQlUrlQuery(url: String): Boolean {
        val params = queryParams(url) ?: return false
        val queryValue = params["query"]?.firstOrNull()?.trim().orEmpty()
        val opNameValue = params["operationName"]?.firstOrNull()?.trim().orEmpty()
        val normalizedQuery = GraphQlDocuments.normalize(queryValue)
        return when {
            queryValue.isEmpty() -> false
            opNameValue.isNotEmpty() -> true
            normalizedQuery != null &&
                GraphQlDocuments.operationTypeRegex.containsMatchIn(normalizedQuery) -> true
            else -> params.containsKey("variables") || params.containsKey("extensions")
        }
    }

    fun extractGraphQlParams(url: String): Params? {
        val params = queryParams(url)
        val opName = params?.get("operationName")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        val query = params?.get("query")?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
        return if (opName == null && query == null) {
            null
        } else {
            Params(operationName = opName, query = query)
        }
    }

    private fun queryParams(url: String): Map<String, List<String>>? {
        val rawQuery = runCatching { URI(url) }.getOrNull()?.rawQuery
        return rawQuery?.let(::parseQueryParams)
    }

    private fun parseQueryParams(rawQuery: String): Map<String, List<String>> {
        return rawQuery
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) {
                    null
                } else {
                    val idx = part.indexOf('=')
                    val key = if (idx >= 0) part.take(idx) else part
                    val value = if (idx >= 0) part.substring(idx + 1) else ""
                    decodeUrlComponent(key) to decodeUrlComponent(value)
                }
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun decodeUrlComponent(value: String): String {
        return runCatching { java.net.URLDecoder.decode(value, Charsets.UTF_8.name()) }
            .getOrDefault(value)
    }
}
