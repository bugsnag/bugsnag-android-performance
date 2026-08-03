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
import javax.net.ssl.SSLHandshakeException

class ExampleNetworkCalls(private val context: Context) {
    private companion object {
        const val TAG = "ExampleNetworkCalls"
        const val NETWORK_ENDPOINT = "https://postman-echo.com/get?source=performance-example"
        const val GRAPHQL_ENDPOINT = "https://rickandmortyapi.com/graphql"
        const val PLAIN_ENDPOINT = "https://jsonplaceholder.typicode.com/posts"
        const val GRAPHQL_OPERATION_NAME = "GetCharacters"
        const val GRAPHQL_BODY =
            """{"operationName":"GetCharacters","query":"query GetCharacters { characters(page: 1) { results { id name } } }","variables":{}}"""
        const val PLAIN_BODY = """{"title":"Performance Example","body":"Testing plain POST from Android performance SDK","userId":1}"""
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
                        RequestType.NETWORK -> {
                            val requestBody = readRequestBodyText(request)
                            buildNetworkLog(request, status, durationMs, bodyText, requestBody)
                        }
                        RequestType.GRAPHQL -> {
                            val requestBody = readRequestBodyText(request)
                            buildGraphQlLog(status, durationMs, bodyText, requestBody)
                        }
                        RequestType.PLAIN_POST -> buildPlainPostLog(status, durationMs, bodyText)
                    }
                Log.i(TAG, logMessage)
            }

            override fun onFailure(call: Call, e: IOException) {
                val hint = buildTlsHint(e)
                Log.e(TAG, "Request failed. $hint", e)
                mainHandler.post {
                    Toast.makeText(context, "Request failed: $hint", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

    private fun buildTlsHint(error: IOException): String {
        return if (error is SSLHandshakeException) {
            "TLS handshake failed. If you use a proxy/VPN with a user-installed CA, trust user certs in debug via network security config."
        } else {
            error.message ?: "Unknown network error"
        }
    }

    private fun buildGraphQlLog(
        status: Int,
        durationMs: Long,
        bodyText: String,
        requestBody: String?,
    ): String {
        val operationMeta = extractGraphQlOperationMeta(requestBody)
        val operationName = operationMeta?.first ?: GRAPHQL_OPERATION_NAME
        val operationType = operationMeta?.second ?: "query"
        val fullRequestBody = requestBody ?: "none"

        return try {
            val data = JSONObject(bodyText).optJSONObject("data")
            val topLevelKeys = data?.keys()?.asSequence()?.toList() ?: emptyList()
            val firstKey = topLevelKeys.firstOrNull() ?: "none"

            "GraphQL operation.name=$operationName operation.operationType=$operationType " +
                "status=$status durationMs=$durationMs topLevelFields=${topLevelKeys.joinToString(",")} sampleField=$firstKey requestBody=$fullRequestBody"
        } catch (ignored: Exception) {
            val snippet = bodyText.take(160)
            "GraphQL operation.name=$operationName operation.operationType=$operationType " +
                "status=$status durationMs=$durationMs responsePreview=$snippet requestBody=$fullRequestBody"
        }
    }

    private fun extractGraphQlOperationMeta(bodyText: String?): Pair<String, String>? {
        val requestBody = bodyText ?: return null

        val operationName = OPERATION_NAME_REGEX.find(requestBody)?.groupValues?.getOrNull(1) ?: return null
        val operationType = OPERATION_TYPE_REGEX.find(requestBody)?.groupValues?.getOrNull(1)?.lowercase() ?: "query"
        return operationName to operationType
    }

    private fun readRequestBodyText(request: Request): String? {
        val body = request.body ?: return null
        return try {
            Buffer().apply { body.writeTo(this) }.readUtf8()
        } catch (ignored: Exception) {
            null
        }
    }

    private fun buildNetworkLog(
        request: Request,
        status: Int,
        durationMs: Long,
        bodyText: String,
        requestBody: String?,
    ): String {
        val host = request.url.host
        val path = request.url.encodedPath
        val preview = bodyText.replace("\n", " ").take(80)
        val fullRequestBody = requestBody ?: "none"
        return "HTTP method=${request.method} host=$host path=$path status=$status durationMs=$durationMs preview=$preview requestBody=$fullRequestBody"
    }

    private fun buildPlainPostLog(
        status: Int,
        durationMs: Long,
        bodyText: String,
    ): String {
        if (status >= 500) {
            return "Plain POST status=$status durationMs=$durationMs serverUnavailable=true responsePreview=${bodyText.take(160)}"
        }

        return try {
            val root = JSONObject(bodyText)
            val postId = root.optInt("id", -1)
            val title = root.optString("title", "unknown")
            val userId = root.optInt("userId", -1)
            "Plain POST status=$status durationMs=$durationMs createdPostId=$postId title=$title userId=$userId"
        } catch (ignored: Exception) {
            "Plain POST status=$status durationMs=$durationMs responsePreview=${bodyText.take(160)}"
        }
    }
}