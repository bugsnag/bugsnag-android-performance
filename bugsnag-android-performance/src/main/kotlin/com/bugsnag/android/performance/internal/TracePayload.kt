package com.bugsnag.android.performance.internal

import android.os.SystemClock
import android.util.JsonWriter
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

data class TracePayload(
    val timestamp: Long,
    val body: ByteArray,
    val headers: Map<String, String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TracePayload

        if (timestamp != other.timestamp) return false
        if (!body.contentEquals(other.body)) return false
        if (headers != other.headers) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + headers.hashCode()
        return result
    }

    companion object Encoder {
        @JvmStatic
        fun createTracePayload(
            apiKey: String,
            spans: Collection<Span>,
            resourceAttributes: Attributes
        ): TracePayload {
            val payloadBytes = encodeSpanPayload(spans, resourceAttributes)
            val timestamp = spans.maxOf { it.endTime }

            return createTracePayload(apiKey, payloadBytes, timestamp)
        }

        @JvmStatic
        fun createTracePayload(
            apiKey: String,
            payloadBytes: ByteArray,
            timestamp: Long = SystemClock.elapsedRealtimeNanos()
        ): TracePayload {
            val headers = mutableMapOf(
                "Bugsnag-Api-Key" to apiKey,
                "Content-Encoding" to "application/json",
                "Content-Length" to payloadBytes.size.toString()
            )

            computeSha1Digest(payloadBytes)?.let { digest ->
                headers["Bugsnag-Integrity"] = digest
            }

            return TracePayload(
                timestamp,
                payloadBytes,
                headers
            )
        }

        @VisibleForTesting
        internal fun encodeSpanPayload(
            spans: Collection<Span>,
            resourceAttributes: Attributes
        ): ByteArray {
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
    }
}
