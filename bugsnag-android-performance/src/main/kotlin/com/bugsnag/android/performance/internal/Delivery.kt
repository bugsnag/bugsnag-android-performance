package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal class Delivery(private val endpoint: String) {
    fun deliverSpanChain(head: SpanImpl) {
        val payload = encodeSpanPayload(head)

        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.setFixedLengthStreamingMode(payload.size)
        connection.setRequestProperty("Content-Encoding", "application/json")
        connection.doOutput = true
        connection.doInput = true
        connection.outputStream.use { out -> out.write(payload) }

        connection.inputStream.reader().readText()
    }

    @VisibleForTesting
    fun encodeSpanPayload(head: SpanImpl): ByteArray {
        val buffer = ByteArrayOutputStream()
        JsonWriter(buffer.writer()).use { json ->

            json.beginObject()
                .name("resourceSpans").beginArray()
                .beginObject()
                .name("scopeSpans").beginArray()
                .beginObject()
                .name("spans").beginArray()

            var currentSpan: SpanImpl? = head
            while (currentSpan != null) {
                currentSpan.toJson(json)
                currentSpan = currentSpan.previous
            }

            json.endArray()
                .endObject()
                .endArray()
                .endObject()
                .endArray()
                .endObject()
        }

        return buffer.toByteArray()
    }
}
