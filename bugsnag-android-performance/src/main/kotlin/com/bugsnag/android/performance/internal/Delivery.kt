package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal class Delivery(private val endpoint: String) {
    fun deliverSpanChain(head: Span, resourceAttributes: Attributes) {
        val payload = encodeSpanPayload(head, resourceAttributes)

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

        connection.inputStream.reader().readText()
    }

    @VisibleForTesting
    fun encodeSpanPayload(head: Span, resourceAttributes: Attributes): ByteArray {
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

            var currentSpan: Span? = head
            while (currentSpan != null) {
                currentSpan.toJson(json)
                currentSpan = currentSpan.previous
            }

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
}
