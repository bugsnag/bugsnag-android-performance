package com.bugsnag.performance

import android.util.JsonWriter
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

internal class Tracer(private val endpoint: String) : Runnable, SpanProcessor {
    /*
     * The "head" of the linked-list of `Span`s current queued for delivery. The `Span`s are
     * actually stored in reverse order to keep the list logic simple. The last `Span` queued
     * for delivery is stored here (or `null` if the list is empty). Combined with the time
     * it takes to deliver a a payload this has a natural batching effect, while we avoid locks
     * when enqueuing new `Span`s for delivery.
     */
    private var tail = AtomicReference<SpanImpl?>()

    @Volatile
    private var running = true

    fun stop() {
        running = false
    }

    override fun run() {
        while (running) {
            val listHead = tail.getAndSet(null) ?: continue

            val payload = spansToJson(listHead)
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.setFixedLengthStreamingMode(payload.size)
            connection.setRequestProperty("Content-Encoding", "application/json")
            connection.doOutput = true
            connection.doInput = true
            connection.outputStream.use { out -> out.write(payload) }

            connection.inputStream.reader().readText()
        }
    }

    fun spansToJson(head: SpanImpl): ByteArray {
        val buffer = ByteArrayOutputStream()
        val json = JsonWriter(buffer.writer())

        json.beginArray()

        var currentSpan: SpanImpl? = head
        while (currentSpan != null) {
            currentSpan.toJson(json)
            currentSpan = currentSpan.previous
        }

        json.endArray()

        return buffer.toByteArray()
    }

    override fun onEnd(span: Span) {
        if (span !is SpanImpl) return
        while (true) {
            val oldHead = tail.get()
            span.previous = oldHead
            if (tail.compareAndSet(oldHead, span)) break
        }
    }
}
