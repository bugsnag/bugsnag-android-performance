package com.bugsnag.mazeracer.scenarios

import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.NetworkRequestInstrumentationCallback
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.mazeracer.Scenario
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL
import kotlin.concurrent.thread

class OkhttpManualNetworkCallbackScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    override fun startScenario() {
        config.networkRequestCallback =
            NetworkRequestInstrumentationCallback { reqInfo ->
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
            runAndFlush {
                val client =
                    OkHttpClient.Builder()
                        .build()

                makeCall(client, "https://www.google.com/")
                makeCall(client, "https://www.google.com/changeme")
                makeCall(client, "https://www.google.com/")
            }
        }
    }

    fun makeCall(
        client: OkHttpClient,
        url: String,
    ) {
        val request = Request.Builder().url(url).build()

        val span = BugsnagPerformance.startNetworkRequestSpan(URL(url), "GET")
        client.newCall(request).execute().use { response ->
            span?.end()
            // Consume and discard the response body.
            val size = response.body.byteString().size.toString()
            Log.i("OkhttpAutoInstrument", "Read $size bytes from ${request.url}")
        }
    }
}
