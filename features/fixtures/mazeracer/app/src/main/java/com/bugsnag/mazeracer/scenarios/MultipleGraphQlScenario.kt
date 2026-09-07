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
 * Issues two GetUser queries, one CreatePost mutation, and one REST GET so GraphQL and
 * network spans coexist in a single flush (Scenario 9).
 */
class MultipleGraphQlScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val client = buildClient()
                val jsonMediaType = "application/json".toMediaType()

                val queryBody =
                    "{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}"
                val mutationBody =
                    "{\"query\": \"mutation CreatePost { user { id } }\", \"operationName\": \"CreatePost\"}"

                try {
                    // 1–2. Identical GraphQL queries
                    repeat(GET_USER_COUNT) {
                        execute(
                            client,
                            Request.Builder()
                                .url(GRAPHQL_URL)
                                .post(queryBody.toRequestBody(jsonMediaType))
                                .build(),
                        )
                    }

                    // 3. Distinct GraphQL mutation
                    execute(
                        client,
                        Request.Builder()
                            .url(GRAPHQL_URL)
                            .post(mutationBody.toRequestBody(jsonMediaType))
                            .build(),
                    )

                    // 4. Non-GraphQL REST GET (network span)
                    execute(
                        client,
                        Request.Builder()
                            .url(REST_URL)
                            .get()
                            .build(),
                    )
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
            // BitBar SecureTunnel intercepts real sockets; short-circuit in-process.
            .addInterceptor(
                Interceptor { chain ->
                    val response = mockResponse(chain.request())
                    bugsnag.responseHeadersEnd(chain.call(), response)
                    response
                },
            )
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
        private const val LOG_TAG = "MultipleGraphQlScenario"
        private const val GRAPHQL_URL = "https://api.example.com/graphql"
        private const val REST_URL = "https://api.example.com/rest/users/123"
        private const val GET_USER_COUNT = 2
        private const val HTTP_OK = 200
    }
}
