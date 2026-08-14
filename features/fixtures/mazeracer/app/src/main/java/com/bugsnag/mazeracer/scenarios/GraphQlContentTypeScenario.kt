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
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
                val mockServer = requestSpec.mockResponseStatus?.let {
                    startOneShotHttpServer(
                        statusCode = it,
                        responseBody = requestSpec.mockResponseBody ?: "{}",
                    )
                }
                val resolvedUrl =
                    if (mockServer != null) {
                        val original = requestSpec.url.toHttpUrlOrNull()
                        if (original != null) {
                            original
                                .newBuilder()
                                .host("localhost")
                                .port(mockServer.port)
                                .scheme("http")
                                .build()
                                .toString()
                        } else {
                            "http://localhost:${mockServer.port}/graphql"
                        }
                    } else {
                        requestSpec.url
                    }
                val request =
                    Request.Builder()
                        .url(resolvedUrl)
                        .post(requestSpec.body.toRequestBody(requestSpec.contentType.toMediaType()))
                        .build()

                try {
                    client.newCall(request).execute().use { response ->
                        val size = response.body.byteString().size.toString()
                        Log.i(
                            "GraphQlContentTypeScenario",
                            "Read $size bytes from ${request.url} with status=${response.code}",
                        )
                    }
                } catch (exception: Exception) {
                    Log.e("GraphQlContentTypeScenario", "Request failed", exception)
                } finally {
                    mockServer?.close()
                }
            }
        }
    }

    private fun parseRequestSpec(): RequestSpec {
        if (scenarioMetadata.isBlank()) {
            return DEFAULT_REQUEST_SPEC
        }

        val parts = scenarioMetadata.split(METADATA_DELIMITER, limit = 5)
        require(parts.size in 3..5) {
            "Expected scenarioMetadata format <url>$METADATA_DELIMITER<contentType>$METADATA_DELIMITER<body>[$METADATA_DELIMITER<firstClass>|$METADATA_DELIMITER<httpStatus>$METADATA_DELIMITER<responseBody>]"
        }

        val fourth = parts.getOrNull(3)?.trim()
        val firstClass = fourth?.toBooleanStrictOrNull()
        val mockResponseStatus =
            if (firstClass == null && fourth != null) {
                fourth.toIntOrNull() ?: error("Expected optional httpStatus to be an integer")
            } else {
                null
            }
        val mockResponseBody = parts.getOrNull(4)

        return RequestSpec(
            url = parts[0].trim(),
            contentType = parts[1].trim(),
            body = parts[2],
            firstClass = firstClass,
            mockResponseStatus = mockResponseStatus,
            mockResponseBody = mockResponseBody,
        )
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
        fun close() {
            shutdown()
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
                        output.write("HTTP/1.1 $statusCode ${reasonPhrase(statusCode)}\r\n".toByteArray(Charsets.US_ASCII))
                        output.write("Content-Type: application/json\r\n".toByteArray(Charsets.US_ASCII))
                        output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray(Charsets.US_ASCII))
                        output.write("Connection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        output.write(bodyBytes)
                        output.flush()
                    }
                }
            }

        return OneShotHttpServer(serverSocket.localPort) {
            runCatching { serverSocket.close() }
            runCatching { serverThread.join(500) }
        }
    }

    private fun readUntilHeadersEnd(input: BufferedInputStream) {
        var state = 0
        while (state < 4) {
            val byte = input.read()
            if (byte == -1) {
                return
            }
            state =
                when {
                    state == 0 && byte == '\r'.code -> 1
                    state == 1 && byte == '\n'.code -> 2
                    state == 2 && byte == '\r'.code -> 3
                    state == 3 && byte == '\n'.code -> 4
                    else -> 0
                }
        }
    }

    private fun reasonPhrase(statusCode: Int): String {
        return when (statusCode) {
            200 -> "OK"
            401 -> "Unauthorized"
            500 -> "Internal Server Error"
            else -> "Status"
        }
    }

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
