package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class HttpDelivery(private val endpoint: String) : Delivery {
    override fun deliver(spans: Collection<Span>, resourceAttributes: Attributes): DeliveryResult {
        if (spans.isEmpty()) {
            return DeliveryResult.SUCCESS
        }

        val payload = encodeSpanPayload(spans, resourceAttributes)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        with(connection) {
            setFixedLengthStreamingMode(payload.size)
            setRequestProperty("Content-Encoding", "application/json")
            computeSha1Digest(payload)?.let { digest ->
                setRequestProperty("Bugsnag-Integrity", digest)
            }

            doOutput = true
            doInput = true
            outputStream.use { out -> out.write(payload) }
        }

        val result = getDeliveryResult(connection)
        if (result == DeliveryResult.SUCCESS) {
            connection.inputStream.reader().readText()
        }
        return result
    }

    private fun getDeliveryResult(connection: HttpURLConnection): DeliveryResult {
        val statusCode = connection.responseCode

        return when {
            statusCode / 100 == 2 -> DeliveryResult.SUCCESS
            statusCode / 100 == 4 && statusCode !in retriable400Codes -> DeliveryResult.FAIL_PERMANENT
            else -> DeliveryResult.FAIL_RETRIABLE
        }
    }

    @VisibleForTesting
    fun encodeSpanPayload(spans: Collection<Span>, resourceAttributes: Attributes): ByteArray {
        val buffer = ByteArrayOutputStream()
        JsonWriter(buffer.writer()).use { json ->
            json.beginObject()
                .name("resourceSpans").beginArray()
                .beginObject()

            json.name("resource").beginObject()
                .name("attributes").value(resourceAttributes)
                .endObject()

            json.name("scopeSpans").beginArray()
                .beginObject()
                .name("spans").beginArray()

            spans.forEach { it.toJson(json) }

            json.endArray() // spans
                .endObject()
                .endArray() // scopeSpans
                .endObject()
                .endArray() // resourceSpans
                .endObject()
        }

        return buffer.toByteArray()
    }

    private fun computeSha1Digest(payload: ByteArray): String? {
        runCatching {
            val shaDigest = MessageDigest.getInstance("SHA-1")
            shaDigest.update(payload)

            return buildString {
                append("sha1 ")
                appendHexString(shaDigest.digest())
            }
        }.getOrElse { return null }
    }

    companion object {
        private val retriable400Codes = setOf(
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
