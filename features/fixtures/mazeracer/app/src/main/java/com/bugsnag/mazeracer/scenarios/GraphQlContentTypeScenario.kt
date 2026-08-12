package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread

class GraphQlContentTypeScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val requestSpec = parseRequestSpec()
                val clientBuilder = OkHttpClient.Builder()
                val firstClass = requestSpec.firstClass
                if (firstClass == null) {
                    clientBuilder.eventListenerFactory(BugsnagPerformanceOkhttp.EventListenerFactory)
                } else {
                    val spanOptions = SpanOptions.makeCurrentContext(false).setFirstClass(firstClass)
                    clientBuilder.eventListener(BugsnagPerformanceOkhttp(networkSpanOptions = spanOptions))
                }
                val client = clientBuilder.build()
                val request =
                    Request.Builder()
                        .url(requestSpec.url)
                        .post(requestSpec.body.toRequestBody(requestSpec.contentType.toMediaType()))
                        .build()

                client.newCall(request).execute().use { response ->
                    val size = response.body.byteString().size.toString()
                    Log.i(
                        "GraphQlContentTypeScenario",
                        "Read $size bytes from ${request.url} with status=${response.code}",
                    )
                }
            }
        }
    }

    private fun parseRequestSpec(): RequestSpec {
        if (scenarioMetadata.isBlank()) {
            return DEFAULT_REQUEST_SPEC
        }

        val parts = scenarioMetadata.split(METADATA_DELIMITER, limit = 4)
        require(parts.size in 3..4) {
            "Expected scenarioMetadata format <url>$METADATA_DELIMITER<contentType>$METADATA_DELIMITER<body>[$METADATA_DELIMITER<firstClass>]"
        }

        val firstClass =
            if (parts.size == 4) {
                parts[3].trim().toBooleanStrictOrNull()
                    ?: error("Expected optional firstClass to be 'true' or 'false'")
            } else {
                null
            }

        return RequestSpec(
            url = parts[0].trim(),
            contentType = parts[1].trim(),
            body = parts[2],
            firstClass = firstClass,
        )
    }

    private data class RequestSpec(
        val url: String,
        val contentType: String,
        val body: String,
        val firstClass: Boolean? = null,
    )

    private companion object {
        private const val METADATA_DELIMITER = "|||"

        private val DEFAULT_REQUEST_SPEC =
            RequestSpec(
                url = "https://postman-echo.com/post",
                contentType = "application/graphql",
                body = "query GetUserProfile { user { id name } }",
            )
    }
}
