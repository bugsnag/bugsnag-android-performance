package com.bugsnag.android.performance.internal.graphql

internal object GraphQlDocuments {
    val operationTypeRegex =
        "^\\s*(query|mutation|subscription)\\b".toRegex(RegexOption.IGNORE_CASE)

    val operationNameRegex =
        "^\\s*(?:query|mutation|subscription)\\s+([_A-Za-z][_0-9A-Za-z]*)\\b"
            .toRegex(RegexOption.IGNORE_CASE)

    private val operationNameFieldRegex =
        "\"operationName\"\\s*:\\s*\"([^\"]*)\"".toRegex()
    private val queryFieldRegex = "\"query\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val mutationFieldRegex = "\"mutation\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val subscriptionFieldRegex =
        "\"subscription\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"".toRegex()
    private val documentFieldRegexes =
        listOf(queryFieldRegex, mutationFieldRegex, subscriptionFieldRegex)

    // Anonymous GraphQL selection set ("{ user { id } }"), not JSON ("{ \"key\": ... }")
    private val anonymousSelectionSetRegex =
        "^\\s*\\{\\s*[_A-Za-z]".toRegex()

    // Removes GraphQL comments from documents
    private val commentRegex = "(?m)^\\s*#.*$".toRegex()

    fun hasGraphQlBody(body: String?): Boolean {
        val trimmedBody = body?.trim().orEmpty()
        val operationName = extractOperationNameField(trimmedBody)
        val document = extractGraphQlDocument(trimmedBody)
        return when {
            body.isNullOrBlank() -> false
            !operationName.isNullOrBlank() && document != null -> true
            document != null && looksLikeGraphQlDocument(document) -> true
            else -> looksLikeGraphQlDocument(trimmedBody)
        }
    }

    fun extractOperationNameField(body: String?): String? {
        return body
            ?.let(operationNameFieldRegex::find)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
    }

    fun extractGraphQlDocument(body: String?): String? {
        val encodedDocument =
            documentFieldRegexes.firstNotNullOfOrNull { regex ->
                body?.let(regex::find)?.groupValues?.getOrNull(1)
            }
        return when {
            body.isNullOrBlank() -> null
            encodedDocument != null -> decodeJsonString(encodedDocument)
            else -> normalize(body)?.takeIf { operationTypeRegex.containsMatchIn(it) }
        }
    }

    fun normalize(graphqlDocument: String?): String? {
        return graphqlDocument
            ?.replace(commentRegex, "")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun looksLikeGraphQlDocument(document: String): Boolean {
        val normalizedDocument = normalize(document)
        val isTypedOperation =
            normalizedDocument != null && operationTypeRegex.containsMatchIn(normalizedDocument)
        val isAnonymousSelection =
            normalizedDocument != null &&
                anonymousSelectionSetRegex.containsMatchIn(normalizedDocument) &&
                normalizedDocument.contains('}')
        return isTypedOperation || isAnonymousSelection
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
