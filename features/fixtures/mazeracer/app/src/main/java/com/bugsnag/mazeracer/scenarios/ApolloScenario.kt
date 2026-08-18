package com.bugsnag.mazeracer.scenarios

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.Adapter
import com.apollographql.apollo3.api.CustomScalarAdapters
import com.apollographql.apollo3.api.Query
import com.apollographql.apollo3.api.json.JsonReader
import com.apollographql.apollo3.api.json.JsonWriter
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.apollo.withBugsnagPerformance
import com.bugsnag.mazeracer.Scenario
import kotlinx.coroutines.runBlocking
import kotlin.concurrent.thread

class ApolloScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val client =
                    ApolloClient.Builder()
                        .serverUrl(scenarioMetadata)
                        .withBugsnagPerformance()
                        .build()

                val operation =
                    object : Query<Query.Data> {
                        override fun id(): String = "test-id"

                        override fun document(): String = "query TestQuery { test }"

                        override fun name(): String = "TestQuery"

                        // Required for Apollo 3 compatibility
                        override fun serializeVariables(
                            writer: JsonWriter,
                            customScalarAdapters: CustomScalarAdapters,
                        ) {
                            // No variables to serialize for this mock operation
                        }

                        override fun adapter(): Adapter<Query.Data> =
                            object : Adapter<Query.Data> {
                                override fun fromJson(
                                    reader: JsonReader,
                                    customScalarAdapters: CustomScalarAdapters,
                                ): Query.Data =
                                    object : Query.Data {
                                        // No serialization needed for this mock test query
                                    }

                                override fun toJson(
                                    writer: JsonWriter,
                                    customScalarAdapters: CustomScalarAdapters,
                                    value: Query.Data,
                                ) {
                                    // No serialization needed for this mock test query
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
                } catch (e: java.io.IOException) {
                    // Ignore network failures in the fixture
                    Log.w(
                        "ApolloScenario",
                        "Network request failed as expected or due to environment",
                        e,
                    )
                }
            }
        }
    }
}
