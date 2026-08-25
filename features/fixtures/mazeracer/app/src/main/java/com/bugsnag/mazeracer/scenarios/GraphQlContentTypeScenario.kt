package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.concurrent.thread

class GraphQlContentTypeScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val spec = Parser.parseRequestSpec(scenarioMetadata)
                val client = buildClient(spec)
                val request = buildRequest(spec)

                try {
                    execute(client, request)
                } catch (exception: java.io.IOException) {
                    Log.e(LOG_TAG, "Request failed", exception)
                }
            }
        }
    }

    private fun buildClient(spec: RequestSpec): OkHttpClient {
        // Default first_class=true so GraphQL spans match iOS/QA assertions.
        val spanOptions =
            SpanOptions.makeCurrentContext(false).setFirstClass(spec.firstClass ?: true)
        val bugsnag = BugsnagPerformanceOkhttp(networkSpanOptions = spanOptions)
        val builder =
            OkHttpClient.Builder()
                .eventListener(bugsnag)

        // BitBar's SecureTunnel intercepts localhost/LAN sockets and returns HTTP 500 (or
        // fails the call, discarding the span). Short-circuit in-process so status codes are
        // deterministic and no real network hop is required.
        val mockStatus = spec.mockResponseStatus
        if (mockStatus != null) {
            val mockBody = spec.mockResponseBody ?: "{}"
            builder.addInterceptor(
                Interceptor { chain ->
                    val response = mockResponse(chain.request(), mockStatus, mockBody)
                    // EventListener.responseHeadersEnd is skipped for short-circuit interceptors;
                    // invoke it so http.status_code / length / flavor are still recorded.
                    bugsnag.responseHeadersEnd(chain.call(), response)
                    response
                },
            )
        }

        return builder.build()
    }

    private fun buildRequest(spec: RequestSpec): Request {
        return Request.Builder()
            .url(spec.url)
            .post(spec.body.toRequestBody(spec.contentType.toMediaType()))
            .build()
    }

    private fun execute(
        client: OkHttpClient,
        request: Request,
    ) {
        client.newCall(request).execute().use { response ->
            val size = response.body.byteString().size.toString()
            Log.i(LOG_TAG, "Read $size bytes from ${request.url} with status=${response.code}")
        }
    }

    private fun mockResponse(
        request: Request,
        statusCode: Int,
        body: String,
    ): Response {
        val mediaType = "application/json".toMediaType()
        val responseBody = body.toResponseBody(mediaType)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(statusCode)
            .message(reasonPhrase(statusCode))
            .header("Content-Type", "application/json")
            .header("Content-Length", responseBody.contentLength().toString())
            .body(responseBody)
            .build()
    }

    private fun reasonPhrase(statusCode: Int): String {
        return when (statusCode) {
            HTTP_OK -> "OK"
            HTTP_BAD_REQUEST -> "Bad Request"
            HTTP_UNAUTHORIZED -> "Unauthorized"
            HTTP_INTERNAL_SERVER_ERROR -> "Internal Server Error"
            else -> "Status"
        }
    }

    private data class RequestSpec(
        val url: String,
        val contentType: String,
        val body: String,
        val firstClass: Boolean? = null,
        val mockResponseStatus: Int? = null,
        val mockResponseBody: String? = null,
    )

    private object Parser {
        fun parseRequestSpec(scenarioMetadata: String): RequestSpec {
            if (scenarioMetadata.isBlank()) {
                return DEFAULT_REQUEST_SPEC
            }

            val parts = scenarioMetadata.split(METADATA_DELIMITER, limit = MAX_METADATA_PARTS)
            require(parts.size in MIN_METADATA_PARTS..MAX_METADATA_PARTS) {
                "Expected scenarioMetadata format <url>$METADATA_DELIMITER<contentType>" +
                        "$METADATA_DELIMITER<body>[$METADATA_DELIMITER<firstClass>|" +
                        "$METADATA_DELIMITER<httpStatus>$METADATA_DELIMITER<responseBody>]"
            }

            val fourth = parts.getOrNull(FOURTH_PART_INDEX)?.trim()
            val firstClass = fourth?.toBooleanStrictOrNull()
            val mockResponseStatus =
                if (firstClass == null && fourth != null) {
                    fourth.toIntOrNull() ?: error("Expected optional httpStatus to be an integer")
                } else {
                    null
                }

            val mockResponseBody = parts.getOrNull(RESPONSE_BODY_INDEX)

            return RequestSpec(
                url = parts[0].trim(),
                contentType = parts[1].trim(),
                body = parts[2],
                firstClass = firstClass,
                mockResponseStatus = mockResponseStatus,
                mockResponseBody = mockResponseBody,
            )
        }
    }

    private companion object {
        private const val LOG_TAG = "GraphQlContentTypeScenario"

        private const val METADATA_DELIMITER = "|||"
        private const val RESPONSE_BODY_INDEX = 4
        private const val FOURTH_PART_INDEX = 3

        private const val MIN_METADATA_PARTS = 3
        private const val MAX_METADATA_PARTS = 5

        private const val HTTP_OK = 200
        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_INTERNAL_SERVER_ERROR = 500

        private val DEFAULT_REQUEST_SPEC =
            RequestSpec(
                url = "https://postman-echo.com/post",
                contentType = "application/graphql",
                body = "query GetUserProfile { user { id name } }",
            )
    }
}
