package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class OkhttpSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val client =
                    OkHttpClient.Builder()
                        .eventListenerFactory(BugsnagPerformanceOkhttp.EventListenerFactory)
                        .build()
                val request =
                    Request.Builder()
                        .url(
                            scenarioMetadata.takeUnless { it.isBlank() }
                                ?: "https://google.com/?test=true",
                        )
                        .build()

                client.newCall(request).execute().use { response ->
                    // Consume and discard the response body.
                    val size = response.body.byteString().size.toString()
                    Log.i("OkhttpSpanScenario", "Read $size bytes from ${request.url}")
                }
            }
        }
    }
}
