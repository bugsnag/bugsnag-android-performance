package com.example.bugsnag.performance

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.bugsnag.android.performance.okhttp.BugsnagPerformanceOkhttp
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.IOException

class ExampleNetworkCalls(private val context: Context) {
    private companion object {
        const val TAG = "ExampleNetworkCalls"
        const val NETWORK_ENDPOINT = "https://developer.android.com"
        const val GRAPHQL_ENDPOINT = "https://countries.trevorblades.com/"
        const val PLAIN_ENDPOINT = "https://httpbin.org/post"
        const val GRAPHQL_OPERATION_NAME = "GetCountries"
        const val GRAPHQL_BODY =
            """{"operationName":"GetCountries","query":"query GetCountries { countries { code name } }","variables":{}}"""
        const val PLAIN_BODY = """{"message":"hello from performance-example"}"""
        val OPERATION_NAME_REGEX = "\"operationName\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val OPERATION_TYPE_REGEX = "\\b(query|mutation|subscription)\\b".toRegex()
    }

    private enum class RequestType {
        NETWORK,
        GRAPHQL,
        PLAIN_POST,
    }

    private val client = OkHttpClient.Builder()
        .eventListener(BugsnagPerformanceOkhttp())
        .build()

    private val mainHandler = Handler(Looper.getMainLooper())

    fun runRequest() {
        val request = Request.Builder()
            .url(NETWORK_ENDPOINT)
            .get()
            .build()

        enqueueWithToast(request, "Network request completed", RequestType.NETWORK)
    }

    fun runGraphQlRequest() {
        val request = Request.Builder()
            .url(GRAPHQL_ENDPOINT)
            .post(GRAPHQL_BODY.toRequestBody("application/json".toMediaType()))
            .build()

        enqueueWithToast(request, "GraphQL request completed", RequestType.GRAPHQL)
    }

    fun runPlainPostRequest() {
        val request = Request.Builder()
            .url(PLAIN_ENDPOINT)
            .post(PLAIN_BODY.toRequestBody("application/json".toMediaType()))
            .build()

        enqueueWithToast(request, "Plain POST request completed", RequestType.PLAIN_POST)
    }

    private fun enqueueWithToast(
        request: Request,
        successPrefix: String,
        requestType: RequestType,
    ) {
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val status: Int
                val durationMs: Long
                val bodyText: String
                response.use {
                    status = it.code
                    durationMs = it.receivedResponseAtMillis - it.sentRequestAtMillis
                    bodyText = it.peekBody(10_000).string()
                }
                mainHandler.post {
                    Toast
                        .makeText(context, "$successPrefix (status=$status)", Toast.LENGTH_LONG)
                        .show()
                }

                val logMessage =
                    when (requestType) {
                        RequestType.NETWORK -> buildNetworkLog(request, status, durationMs, bodyText)
                        RequestType.GRAPHQL -> buildGraphQlLog(request, status, durationMs, bodyText)
                        RequestType.PLAIN_POST -> buildPlainPostLog(status, durationMs, bodyText)
                    }
                Log.i(TAG, logMessage)
            }

            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Request failed", e)
            }
        })
    }

    private fun buildGraphQlLog(
        request: Request,
        status: Int,
        durationMs: Long,
        bodyText: String,
    ): String {
        val operationMeta = extractGraphQlOperationMeta(request)
        val operationName = operationMeta?.first ?: GRAPHQL_OPERATION_NAME
        val operationType = operationMeta?.second ?: "query"

        return try {
            val countries = JSONObject(bodyText).optJSONObject("data")?.optJSONArray("countries")
            val count = countries?.length() ?: 0
            val firstCountry = countries?.optJSONObject(0)
            val sample =
                if (firstCountry != null) {
                    "${firstCountry.optString("name")} (${firstCountry.optString("code")})"
                } else {
                    "none"
                }

            "GraphQL operation.name=$operationName operation.operationType=$operationType " +
                "status=$status durationMs=$durationMs countriesCount=$count sample=$sample"
        } catch (ignored: Exception) {
            val snippet = bodyText.take(160)
            "GraphQL operation.name=$operationName operation.operationType=$operationType " +
                "status=$status durationMs=$durationMs responsePreview=$snippet"
        }
    }

    private fun extractGraphQlOperationMeta(request: Request): Pair<String, String>? {
        val bodyText = request.body?.let { body ->
            try {
                Buffer().apply { body.writeTo(this) }.readUtf8()
            } catch (ignored: Exception) {
                null
            }
        } ?: return null

        val operationName = OPERATION_NAME_REGEX.find(bodyText)?.groupValues?.getOrNull(1) ?: return null
        val operationType = OPERATION_TYPE_REGEX.find(bodyText)?.groupValues?.getOrNull(1)?.lowercase() ?: "query"
        return operationName to operationType
    }

    private fun buildNetworkLog(
        request: Request,
        status: Int,
        durationMs: Long,
        bodyText: String,
    ): String {
        val host = request.url.host
        val path = request.url.encodedPath
        val preview = bodyText.replace("\n", " ").take(80)
        return "HTTP method=${request.method} host=$host path=$path status=$status durationMs=$durationMs preview=$preview"
    }

    private fun buildPlainPostLog(
        status: Int,
        durationMs: Long,
        bodyText: String,
    ): String {
        return try {
            val echoedMessage = JSONObject(bodyText).optJSONObject("json")?.optString("message")
            "Plain POST status=$status durationMs=$durationMs echoedMessage=${echoedMessage ?: "missing"}"
        } catch (ignored: Exception) {
            "Plain POST status=$status durationMs=$durationMs responsePreview=${bodyText.take(160)}"
        }
    }
}