package com.bugsnag.android.performance.internal.graphql

internal object GraphQlBodyParser {
    private val operationNameFieldRegex =
        "\"operationName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    private val queryFieldRegex = "\"query\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val mutationFieldRegex = "\"mutation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val subscriptionFieldRegex =
        "\"subscription\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val commentRegex = "(?m)^\\s*#.*$".toRegex()

    fun extractOperationNameField(body: String?): String? {
        return body
            ?.let(operationNameFieldRegex::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    fun extractGraphQlDocument(
        body: String?,
        operationTypeRegex: Regex,
    ): String? {
        val graphqlDocument =
            if (body.isNullOrBlank()) {
                null
            } else {
                sequenceOf(queryFieldRegex, mutationFieldRegex, subscriptionFieldRegex)
                    .firstNotNullOfOrNull { regex ->
                        regex.find(body)?.groupValues?.getOrNull(1)?.let(::decodeJsonString)
                    } ?: normalizeDocument(body)?.takeIf { operationTypeRegex.containsMatchIn(it) }
            }
        return graphqlDocument
    }

    fun normalizeDocument(graphqlDocument: String?): String? {
        if (graphqlDocument.isNullOrBlank()) return null
        return graphqlDocument
            .replace(commentRegex, "")
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    private fun decodeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\\", "\\")
    }
}
