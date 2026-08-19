package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
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
                val client = buildClient(spec.firstClass)
                val server = Parser.startMockServerIfNeeded(spec)
                val request = buildRequest(spec, server)

                try {
                    execute(client, request)
                } catch (exception: java.io.IOException) {
                    Log.e(LOG_TAG, "Request failed", exception)
                } finally {
                    server?.close()
                }
            }
        }
    }

    private fun buildClient(firstClass: Boolean?): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (firstClass == null) {
            builder.eventListenerFactory(BugsnagPerformanceOkhttp.EventListenerFactory)
        } else {
            val spanOptions = SpanOptions.makeCurrentContext(false).setFirstClass(firstClass)
            builder.eventListener(BugsnagPerformanceOkhttp(networkSpanOptions = spanOptions))
        }
        return builder.build()
    }

    private fun buildRequest(
        spec: RequestSpec,
        server: OneShotHttpServer?,
    ): Request {
        val resolvedUrl = Parser.resolveUrl(spec.url, server)
        return Request.Builder()
            .url(resolvedUrl)
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

    private data class RequestSpec(
        val url: String,
        val contentType: String,
        val body: String,
        val firstClass: Boolean? = null,
        val mockResponseStatus: Int? = null,
        val mockResponseBody: String? = null,
    )

    private data class OneShotHttpServer(
        val port: Int,
        private val shutdown: () -> Unit,
    ) {
        fun close() = shutdown()
    }

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

        fun startMockServerIfNeeded(spec: RequestSpec): OneShotHttpServer? {
            val status = spec.mockResponseStatus ?: return null
            return startOneShotHttpServer(
                statusCode = status,
                responseBody = spec.mockResponseBody ?: "{}",
            )
        }

        fun resolveUrl(
            originalUrl: String,
            server: OneShotHttpServer?,
        ): String {
            if (server == null) {
                return originalUrl
            }

            val parsed = originalUrl.toHttpUrlOrNull()
            return if (parsed != null) {
                parsed.newBuilder()
                    .host("localhost")
                    .port(server.port)
                    .scheme("http")
                    .build()
                    .toString()
            } else {
                "http://localhost:${server.port}/graphql"
            }
        }

        private fun startOneShotHttpServer(
            statusCode: Int,
            responseBody: String,
        ): OneShotHttpServer {
            val serverSocket = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            val bodyBytes = responseBody.toByteArray(Charsets.UTF_8)
            val serverThread =
                thread(start = true, name = "graphql-mock-server") {
                    serverSocket.use { socket ->
                        socket.accept().use { client ->
                            val input = BufferedInputStream(client.getInputStream())
                            readUntilHeadersEnd(input)

                            val output = client.getOutputStream()
                            val statusLine = "HTTP/1.1 $statusCode ${reasonPhrase(statusCode)}\r\n"
                            output.write(statusLine.toByteArray(Charsets.US_ASCII))
                            output.write("Content-Type: application/json\r\n".toByteArray(Charsets.US_ASCII))
                            output.write(
                                "Content-Length: ${bodyBytes.size}\r\n".toByteArray(
                                    Charsets.US_ASCII,
                                ),
                            )
                            output.write("Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                            output.write(bodyBytes)
                            output.flush()
                        }
                    }
                }

            return OneShotHttpServer(serverSocket.localPort) {
                runCatching { serverSocket.close() }
                runCatching { serverThread.join(SERVER_JOIN_TIMEOUT_MS) }
            }
        }

        private fun readUntilHeadersEnd(input: BufferedInputStream) {
            var state = 0
            while (state < HEADERS_END_STATE) {
                val byte = input.read()
                if (byte == -1) {
                    return
                }
                state =
                    when {
                        state == 0 && byte == '\r'.code -> 1
                        state == 1 && byte == '\n'.code -> 2
                        state == 2 && byte == '\r'.code -> HEADERS_PRE_END_STATE
                        state == HEADERS_PRE_END_STATE && byte == '\n'.code -> HEADERS_END_STATE
                        else -> 0
                    }
            }
        }

        private fun reasonPhrase(statusCode: Int): String {
            return when (statusCode) {
                HTTP_OK -> "OK"
                HTTP_UNAUTHORIZED -> "Unauthorized"
                HTTP_INTERNAL_SERVER_ERROR -> "Internal Server Error"
                else -> "Status"
            }
        }
    }

    private companion object {
        private const val LOG_TAG = "GraphQlContentTypeScenario"

        private const val METADATA_DELIMITER = "|||"
        private const val RESPONSE_BODY_INDEX = 4
        private const val FOURTH_PART_INDEX = 3

        private const val MIN_METADATA_PARTS = 3
        private const val MAX_METADATA_PARTS = 5

        private const val HEADERS_PRE_END_STATE = 3
        private const val HEADERS_END_STATE = 4
        private const val SERVER_JOIN_TIMEOUT_MS = 500L

        private const val HTTP_OK = 200
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
