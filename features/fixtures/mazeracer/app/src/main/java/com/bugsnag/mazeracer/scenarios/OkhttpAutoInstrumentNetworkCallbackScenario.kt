package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import com.bugsnag.mazeracer.Scenario
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.concurrent.thread

class OkhttpAutoInstrumentNetworkCallbackScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.networkRequestCallback =
            NetworkRequestInstrumentationCallback { reqInfo ->
                val url = reqInfo.url ?: return@NetworkRequestInstrumentationCallback
                if (url.startsWith("http://bs-local.com")) {
                    reqInfo.url = null
                } else if (url == "https://google.com/") {
                    reqInfo.url = null
                } else if (url.endsWith("/changeme")) {
                    reqInfo.url = url.dropLast(9) + "/changed"
                }
            }

        BugsnagPerformance.start(config)

        thread {
            runAndFlush {
                val client =
                    OkHttpClient.Builder()
                        .eventListenerFactory(BugsnagPerformanceOkhttp)
                        .build()

                makeCall(client, "https://www.bugsnag.com/")
                makeCall(client, "https://www.bugsnag.com/changeme")
                makeCall(client, "https://www.google.com/")
            }
        }
    }

    fun makeCall(
        client: OkHttpClient,
        url: String,
    ) {
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            // Consume and discard the response body.
            val size = response.body.byteString().size.toString()
            Log.i("OkhttpAutoInstrument", "Read $size bytes from ${request.url}")
        }
    }
}
