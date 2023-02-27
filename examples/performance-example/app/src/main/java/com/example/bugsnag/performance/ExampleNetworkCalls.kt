package com.example.bugsnag.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class ExampleNetworkCalls(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .eventListener(BugsnagPerformanceOkhttp())
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runRequest() {
        val call = client.newCall(
            Request.Builder()
                .url("https://developer.android.com")
                .build()
        )

        call.enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val contentLength = response.use { it.body?.contentLength() }
                mainHandler.post {
                    Toast
                        .makeText(
                            context,
                            "Retrieved $contentLength bytes from developer.android.com",
                            Toast.LENGTH_LONG
                        )
                        .show()
                }
            }

            override fun onFailure(call: Call, e: IOException) = Unit
        })
    }
}