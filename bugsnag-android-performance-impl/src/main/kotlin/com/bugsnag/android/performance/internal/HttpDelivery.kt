package com.bugsnag.android.performance.internal

import android.net.TrafficStats
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.connectivity.Connectivity
import com.bugsnag.android.performance.internal.connectivity.shouldAttemptDelivery
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public open class HttpDelivery(
    private val endpoint: String,
    private val apiKey: String,
    private val connectivity: Connectivity,
    private val hasFixedProbability: Boolean,
    private val attributeLimits: AttributeLimits?,
) : Delivery {
    private val initialProbabilityRequest =
        TracePayload.createTracePayload(
            apiKey,
            emptyList(),
            Attributes(),
            hasFixedProbability,
            null,
        )

    override var newProbabilityCallback: NewProbabilityCallback? = null

    override fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
    ): DeliveryResult {
        return deliver(
            TracePayload.createTracePayload(
                apiKey,
                spans,
                resourceAttributes,
                hasFixedProbability,
                attributeLimits,
            ),
        )
    }

    override fun deliver(tracePayload: TracePayload): DeliveryResult {
        val requestLabel = if (tracePayload === initialProbabilityRequest) {
            "App session config"
        } else {
            "App session delivery"
        }

        if (!connectivity.shouldAttemptDelivery()) {
            Logger.d("$requestLabel request skipped - no connectivity.")
            // We can't deliver now but can retry later.
            return DeliveryResult.Failed(tracePayload, true)
        }

        TrafficStats.setThreadStatsTag(1)
        return try {
            val connection = openConnection()
            Logger.d(
                "$requestLabel request -> POST $endpoint " +
                    "headers=${tracePayload.headers.asDebugString()} body=${tracePayload.describeBody()}",
            )

            with(connection) {
                requestMethod = "POST"

                setHeaders(tracePayload)

                doOutput = true
                doInput = true
                outputStream.use { out -> out.write(tracePayload.body) }
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage.orEmpty()
            val responseHeaders = connection.describeHeaders()
            val responseBody = connection.readResponseBody(responseCode)
            Logger.d(
                "$requestLabel response <- HTTP $responseCode ${responseMessage.ifEmpty { "<no message>" }} " +
                    "headers=$responseHeaders body=$responseBody",
            )

            val result = getDeliveryResult(responseCode, tracePayload)
            val newP = connection.getHeaderField("Bugsnag-Sampling-Probability")?.toDoubleOrNull()
            if (newP != null) {
                Logger.d("$requestLabel response header Bugsnag-Sampling-Probability=$newP")
            }
            connection.disconnect()
            newP?.let { newProbabilityCallback?.onNewProbability(it) }

            Logger.d("$requestLabel delivery result -> $result")
            result
        } catch (ioe: IOException) {
            Logger.w("$requestLabel request failed - I/O error", ioe)
            DeliveryResult.Failed(tracePayload, true)
        } catch (ex: Exception) {
            Logger.e("$requestLabel request failed - unexpected error", ex)
            DeliveryResult.Failed(tracePayload, false)
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    @VisibleForTesting
    internal open fun openConnection() = URL(endpoint).openConnection() as HttpURLConnection

    override fun fetchCurrentProbability() {
        // Server expects a call to /traces with an empty set of resource spans
        deliver(initialProbabilityRequest)
    }

    @Suppress("MagicNumber")
    private fun getDeliveryResult(
        statusCode: Int,
        payload: TracePayload,
    ): DeliveryResult {
        return when {
            statusCode in 200..299 -> DeliveryResult.Success
            statusCode in 400..499 && statusCode !in httpRetryCodes ->
                DeliveryResult.Failed(payload, false)

            else -> DeliveryResult.Failed(payload, true)
        }
    }

    override fun toString(): String = "HttpDelivery(\"$endpoint\")"

    private fun HttpURLConnection.setHeaders(tracePayload: TracePayload) {
        tracePayload.headers.forEach { (name, value) ->
            if (name == "Content-Length") {
                // try and parse this and call setFixedLengthStreamingMode instead of
                // just setRequestProperty
                value.toIntOrNull()?.let { setFixedLengthStreamingMode(it) }
                    // if Content-Length isn't an int set it as a normal header
                    // so we don't unexpectedly loose anything
                    ?: setRequestProperty(name, value)
            } else {
                setRequestProperty(name, value)
            }
        }

        setRequestProperty("Bugsnag-Sent-At", DateUtils.toIso8601(BugsnagClock.toDate()))
    }

    private fun Map<String, String>.asDebugString(): String {
        if (isEmpty()) return "{}"

        return entries.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "$name=$value"
        }
    }

    private fun TracePayload.describeBody(): String {
        val rawBody =
            runCatching {
                if (headers["Content-Encoding"]?.equals("gzip", ignoreCase = true) == true) {
                    GZIPInputStream(ByteArrayInputStream(body)).bufferedReader().use { it.readText() }
                } else {
                    body.toString(Charsets.UTF_8)
                }
            }.getOrElse {
                return "<${body.size} bytes>"
            }

        return rawBody.truncateForLogging()
    }

    private fun HttpURLConnection.describeHeaders(): String {
        val headers =
            headerFields.orEmpty().mapNotNull { (name, values) ->
                name?.let { "$it=${values.joinToString(",")}" }
            }

        return headers.joinToString(prefix = "{", postfix = "}")
    }

    private fun HttpURLConnection.readResponseBody(statusCode: Int): String {
        val stream =
            runCatching {
                if (statusCode in 200..299) inputStream else errorStream
            }.getOrNull() ?: return "<empty>"

        return runCatching {
            val bytes = stream.use { it.readBytes() }
            if (contentEncoding?.equals("gzip", ignoreCase = true) == true) {
                GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader().use { it.readText() }
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }.getOrElse {
            "<unreadable response body: ${it.javaClass.simpleName}>"
        }.truncateForLogging()
    }

    private fun String.truncateForLogging(maxChars: Int = 4096): String {
        if (length <= maxChars) return this
        return take(maxChars) + "…<truncated ${length - maxChars} chars>"
    }

    internal companion object {
        private val httpRetryCodes =
            setOf(
                // 402 Payment Required: a nonstandard client error status response code that is
                // reserved for future use. This status code is returned by ngrok when a tunnel has expired.
                402,
                // 407 Proxy Authentication Required: the request has not been applied because it
                // lacks valid authentication credentials for a proxy server that is between the browser
                // and the server that can access the requested resource.
                407,
                // 408 Request Timeout: the server would like to shut down this unused connection.
                408,
                // 429 Too Many Requests: the user has sent too many requests in a given amount of time
                // ("rate limiting").
                429,
            )
    }
}
