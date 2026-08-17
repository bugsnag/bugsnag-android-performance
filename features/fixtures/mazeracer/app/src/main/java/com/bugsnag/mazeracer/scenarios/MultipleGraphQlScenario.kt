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

class MultipleGraphQlScenario(
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

                val queryBody =
                    "{\"query\": \"query GetUser { user { id } }\", \"operationName\": \"GetUser\"}"
                // 1. query GetUser
                val req1 = Request.Builder()
                    .url("$baseUrl/graphql")
                    .post(queryBody.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req1).execute().use { it.body?.string() }

                // 2. query GetUser
                val req2 = Request.Builder()
                    .url("$baseUrl/graphql")
                    .post(queryBody.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req2).execute().use { it.body?.string() }

                // 3. mutation CreatePost
                val req3 = Request.Builder()
                    .url("$baseUrl/graphql")
                    .post(queryBody.toRequestBody(jsonMediaType))
                    .build()
                client.newCall(req3).execute().use { it.body?.string() }

                // 4. GET rest call
                val req4 = Request.Builder()
                    .url("$baseUrl/rest/users/123")
                    .get()
                    .build()
                client.newCall(req4).execute().use { it.body?.string() }
            }
        }
    }
}
