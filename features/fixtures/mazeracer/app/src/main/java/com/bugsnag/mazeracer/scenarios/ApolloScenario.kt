package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.apollo.withBugsnagPerformance
import com.bugsnag.mazeracer.Scenario
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.encodeUtf8
import kotlin.concurrent.thread

/**
 * Exercises GraphQL span creation via Apollo (Scenario 13).
 *
 * Classification uses Apollo operation metadata (internal headers), not HTTP body parsing.
 * HTTP is short-circuited in-process so BitBar does not require a real network hop.
 */
class ApolloScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val serverUrl = scenarioMetadata.trim().ifBlank { DEFAULT_GRAPHQL_URL }
                val client =
                    ApolloClient.Builder()
                        .serverUrl(serverUrl)
                        .withBugsnagPerformance()
                        .addHttpInterceptor(MockGraphQlResponseInterceptor())
                        .build()

                val operation =
                    object : Query<Query.Data> {
                        override fun id(): String = "test-id"

                        override fun document(): String = "query TestQuery { test }"

                        override fun name(): String = "TestQuery"

                        override fun serializeVariables(
                            writer: JsonWriter,
                            customScalarAdapters: CustomScalarAdapters,
                        ) {
                            // No variables for this mock operation.
                        }

                        override fun adapter(): Adapter<Query.Data> =
                            object : Adapter<Query.Data> {
                                override fun fromJson(
                                    reader: JsonReader,
                                    customScalarAdapters: CustomScalarAdapters,
                                ): Query.Data =
                                    object : Query.Data {}

                                override fun toJson(
                                    writer: JsonWriter,
                                    customScalarAdapters: CustomScalarAdapters,
                                    value: Query.Data,
                                ) {
                                    // No serialization needed for this mock test query.
                                }
                            }

                        override fun rootField(): com.apollographql.apollo3.api.CompiledField =
                            com.apollographql.apollo3.api.CompiledField.Builder(
                                "data",
                                com.apollographql.apollo3.api.ObjectType.Builder("Data").build(),
                            ).build()
                    }

                try {
                    runBlocking {
                        client.query(operation).execute()
                    }
                } catch (e: Exception) {
                    Log.w(LOG_TAG, "Apollo request failed: ${e.message}", e)
                }
            }
        }
    }

    private class MockGraphQlResponseInterceptor : HttpInterceptor {
        override suspend fun intercept(
            request: HttpRequest,
            chain: HttpInterceptorChain,
        ): HttpResponse {
            val body = MOCK_RESPONSE_BODY
            return HttpResponse.Builder(HTTP_OK)
                .addHeader("Content-Type", "application/json")
                .addHeader("Content-Length", body.length.toString())
                .body(body.encodeUtf8())
                .build()
        }

        override fun dispose() = Unit
    }

    private companion object {
        private const val LOG_TAG = "ApolloScenario"
        private const val DEFAULT_GRAPHQL_URL = "https://api.example.com/graphql"
        private const val HTTP_OK = 200
        private const val MOCK_RESPONSE_BODY = "{\"data\":{}}"
    }
}
