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

/**
 * Sends three identical GraphQL GetUser requests in one flush (Scenario 15).
 */
class IdenticalGraphQlScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val graphqlUrl = scenarioMetadata.trim().ifBlank { DEFAULT_GRAPHQL_URL }
                val client = buildClient()
                val jsonMediaType = "application/json".toMediaType()
                val body =
                    "{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}"

                try {
                    repeat(ITERATION_COUNT) {
                        client
                            .newCall(
                                Request.Builder()
                                    .url(graphqlUrl)
                                    .post(body.toRequestBody(jsonMediaType))
                                    .build(),
                            )
                            .execute()
                            .use { response ->
                                Log.i(LOG_TAG, "GetUser request ${it + 1} status=${response.code}")
                            }
                    }
                } catch (exception: java.io.IOException) {
                    Log.e(LOG_TAG, "Request failed", exception)
                }
            }
        }
    }

    private fun buildClient(): OkHttpClient {
        val spanOptions = SpanOptions.makeCurrentContext(false).setFirstClass(true)
        val bugsnag = BugsnagPerformanceOkhttp(networkSpanOptions = spanOptions)
        return OkHttpClient.Builder()
            .eventListener(bugsnag)
            .addInterceptor(bugsnag)
            .addInterceptor(
                Interceptor { chain ->
                    val response = mockResponse(chain.request())
                    bugsnag.responseHeadersEnd(chain.call(), response)
                    response
                },
            )
            .build()
    }

    private fun mockResponse(request: Request): Response {
        val responseBody = "{}".toResponseBody("application/json".toMediaType())
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_OK)
            .message("OK")
            .header("Content-Type", "application/json")
            .header("Content-Length", responseBody.contentLength().toString())
            .body(responseBody)
            .build()
    }

    private companion object {
        private const val LOG_TAG = "IdenticalGraphQlScenario"
        private const val DEFAULT_GRAPHQL_URL = "https://api.example.com/graphql"
        private const val ITERATION_COUNT = 3
        private const val HTTP_OK = 200
    }
}
