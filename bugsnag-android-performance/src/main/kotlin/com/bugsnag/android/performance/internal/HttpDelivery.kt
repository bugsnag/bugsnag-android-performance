package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import java.net.HttpURLConnection
import java.net.URL

internal class HttpDelivery(
    private val endpoint: String,
    private val apiKey: String,
) : Delivery {
    override var newProbabilityCallback: NewProbabilityCallback? = null

    override fun deliver(
        spans: Collection<SpanImpl>,
        resourceAttributes: Attributes,
    ): DeliveryResult {
        return deliver(TracePayload.createTracePayload(apiKey, spans, resourceAttributes))
    }

    override fun deliver(tracePayload: TracePayload): DeliveryResult {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        with(connection) {
            requestMethod = "POST"

            setHeaders(tracePayload)

            doOutput = true
            doInput = true
            outputStream.use { out -> out.write(tracePayload.body) }
        }

        val result = getDeliveryResult(connection.responseCode, tracePayload)
        val newP = connection.getHeaderField("Bugsnag-Sampling-Probability")?.toDoubleOrNull()
        connection.disconnect()
        newP?.let { newProbabilityCallback?.onNewProbability(it) }
        return result
    }

    override fun fetchCurrentProbability() {
        // Server expects a call to /traces with an empty set of resource spans
        deliver(
            TracePayload.createTracePayload(
                apiKey,
                "{\"resourceSpans\": []}".toByteArray(),
                mapOf(
                    "Bugsnag-Sampling-Probability" to "1:0",
                ),
            ),
        )
    }

    private fun getDeliveryResult(statusCode: Int, payload: TracePayload): DeliveryResult {
        return when {
            statusCode / 100 == 2 -> DeliveryResult.Success
            statusCode / 100 == 4 && statusCode !in httpRetryCodes ->
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
    }

    companion object {
        private val httpRetryCodes = setOf(
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
