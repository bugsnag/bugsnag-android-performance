package com.bugsnag.mazeracer.scenarios

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.measureSpan
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class OkhttpSpanScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        BugsnagPerformance.start(config)

        thread {
            val client = OkHttpClient.Builder()
                .eventListener(BugsnagPerformanceOkhttp)
                .build()
            val request = Request.Builder()
                .url("https://google.com?test=true")
                .build()

            client.newCall(request).execute().use { response ->
                // Consume and discard the response body.
                response.body?.source()?.readByteString()
            }
        }
        Thread.sleep(1000L)

        measureSpan("OkhttpSpanScenario") {
            Thread.sleep(100L)
        }
    }
}
