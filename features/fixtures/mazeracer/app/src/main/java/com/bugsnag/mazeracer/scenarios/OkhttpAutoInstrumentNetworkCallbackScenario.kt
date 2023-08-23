package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class OkhttpAutoInstrumentNetworkCallbackScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
    }

    override fun startScenario() {
        config.networkRequestCallback = {reqInfo ->
            reqInfo.url?.let { url ->
                if (url.startsWith("http://bs-local.com")) {
                    reqInfo.url = null
                } else if (url == "https://google.com/") {
                    reqInfo.url = null
                } else if (url.endsWith("/changeme")) {
                    reqInfo.url = url.dropLast(9) + "/changed"
                }
            }
        }

        BugsnagPerformance.start(config)

        thread {
            val client = OkHttpClient.Builder()
                .eventListenerFactory(BugsnagPerformanceOkhttp)
                .build()

            makeCall(client, "https://bugsnag.com/")
            makeCall(client, "https://bugsnag.com/changeme")
            makeCall(client, "https://google.com/")
        }
        Thread.sleep(1000L)
    }

    fun makeCall(client: OkHttpClient, url: String) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            // Consume and discard the response body.
            val size = response.body?.byteString()?.size?.toString() ?: "no"
            Log.i("OkhttpAutoInstrument", "Read $size bytes from ${request.url}")
        }
    }
}
