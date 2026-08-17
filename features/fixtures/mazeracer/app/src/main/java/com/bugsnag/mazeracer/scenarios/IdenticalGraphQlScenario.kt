package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.thread

class IdenticalGraphQlScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val baseUrl = scenarioMetadata.removeSuffix("/")
                val client = OkHttpClient.Builder()
                    .eventListenerFactory(BugsnagPerformanceOkhttp.EventListenerFactory)
                    .build()

                val jsonMediaType = "application/json".toMediaType()
                val body = "{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}"

                repeat(ITERATION_COUNT) {
                    val request = Request.Builder()
                        .url("$baseUrl/graphql")
                        .post(body.toRequestBody(jsonMediaType))
                        .build()
                    client.newCall(request).execute().use { it.body?.string() }
                }
            }
        }
    }

    private companion object {
        private const val ITERATION_COUNT = 3
    }
}
