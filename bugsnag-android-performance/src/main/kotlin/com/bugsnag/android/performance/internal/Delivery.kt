package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class Delivery(private val endpoint: String) {
    fun deliverSpanChain(head: Span, resourceAttributes: Attributes) {
        val payload = encodeSpanPayload(head, resourceAttributes)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.setFixedLengthStreamingMode(payload.size)
        connection.setRequestProperty("Content-Encoding", "application/json")
        connection.doOutput = true
        connection.doInput = true
        connection.outputStream.use { out -> out.write(payload) }

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
}
