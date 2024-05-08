package com.bugsnag.android.performance.okhttp.instrumentation

import com.bugsnag.android.performance.okhttp.withBugsnagPerformance
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.io.IOException

internal fun makeNetworkOkhttpRequest(
    request: Request.Builder,
    response: MockResponse? = null,
    path: String = "/test",
    action: (OkHttpClient.Builder) -> Unit = {},
): RecordedRequest {
    val server = MockWebServer().apply {
        response?.let(this::enqueue)
        start()
    }
    val baseUrl = server.url(path)

    val builder = OkHttpClient.Builder().withBugsnagPerformance()
    action(builder)
    val okHttpClient = builder.build()

    val req = request.url(baseUrl).build()
    val call = okHttpClient.newCall(req)
    try {
        call.execute().close()
    } catch (ignored: IOException) {
    }

    val result = server.takeRequest()
    server.shutdown()
    return result
}
