package com.bugsnag.android.performance.internal

import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import com.bugsnag.android.performance.internal.processing.JsonTraceWriter
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.TreeMap
import java.util.zip.GZIPOutputStream

internal data class TracePayload(
    val timestamp: Long,
    val body: ByteArray,
    val headers: Map<String, String>,
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
        /**
         * The minimum number of bytes in a payload before it will be considered for gzip
         * compression. This avoids wasting cycles on payloads that are trivial or would
         * actually increase in size (due to the gzip/deflate header overhead).
         */
        private const val MIN_GZIP_BYTES = 128

        @JvmStatic
        fun createTracePayload(
            apiKey: String,
            spans: Collection<SpanImpl>,
            resourceAttributes: Attributes,
            hasFixedProbability: Boolean,
            attributeLimits: AttributeLimits?,
        ): TracePayload {
            val payloadBytes = encodeSpanPayload(spans, resourceAttributes, attributeLimits)
            val timestamp =
                if (spans.isNotEmpty()) {
                    spans.maxOf { it.endTime }
                } else {
                    SystemClock.elapsedRealtimeNanos()
                }
            val headers =
                if (!hasFixedProbability) {
                    mapOf("Bugsnag-Span-Sampling" to calculateSpanSamplingHeader(spans))
                } else {
                    emptyMap()
                }
            return createTracePayload(apiKey, payloadBytes, headers, timestamp)
        }

        private fun calculateSpanSamplingHeader(spans: Collection<SpanImpl>): String {
            if (spans.isEmpty()) return "1:0"

            return calculateProbabilityCounts(spans).entries.joinToString(";") { (pValue, count) ->
                "$pValue:$count"
            }
        }

        private fun calculateProbabilityCounts(spans: Collection<SpanImpl>): Map<Double, Int> {
            // using a TreeMap gives us a natural ascending order here
            val pValueCounts = TreeMap<Double, Int>()
            spans.forEach { span ->
                pValueCounts[span.samplingProbability] =
                    (pValueCounts[span.samplingProbability] ?: 0) + 1
            }

            return pValueCounts
        }

        @JvmStatic
        fun createTracePayload(
            apiKey: String,
            payloadBytes: ByteArray,
            baseHeaders: Map<String, String> = emptyMap(),
            timestamp: Long = SystemClock.elapsedRealtimeNanos(),
        ): TracePayload {
            val headers = mutableMapOf<String, String>()
            headers.putAll(baseHeaders)

            headers["Bugsnag-Api-Key"] = apiKey
            headers["Content-Type"] = "application/json"

            computeSha1Digest(payloadBytes)?.let { digest ->
                headers["Bugsnag-Integrity"] = digest
            }

            var body = payloadBytes
            // we don't compress trivial payloads to avoid increasing the size of the payload
            if (payloadBytes.size >= MIN_GZIP_BYTES) {
                body = payloadBytes.gzipped()
                headers["Content-Encoding"] = "gzip"
            }
            headers["Content-Length"] = body.size.toString()

            return TracePayload(timestamp, body, headers)
        }

        @VisibleForTesting
        internal fun encodeSpanPayload(
            spans: Collection<SpanImpl>,
            resourceAttributes: Attributes,
            attributeLimits: AttributeLimits?,
        ): ByteArray {
            val buffer = ByteArrayOutputStream()
            JsonTraceWriter(buffer.writer(), attributeLimits).use { json ->
                json.obj {
                    name("resourceSpans").array {
                        encodeResourceSpans(resourceAttributes, spans)
                    }
                }
            }

            return buffer.toByteArray()
        }

        private fun JsonTraceWriter.encodeResourceSpans(
            resourceAttributes: Attributes,
            spans: Collection<SpanImpl>,
        ) {
            if (resourceAttributes.size == 0 && spans.isEmpty()) return

            obj {
                if (resourceAttributes.size > 0) {
                    name("resource").obj {
                        name("attributes").value(resourceAttributes)
                    }
                }

                if (spans.isNotEmpty()) {
                    name("scopeSpans").array {
                        obj {
                            name("spans").array {
                                spans.forEach { it.toJson(this) }
                            }
                        }
                    }
                }
            }
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

        private fun ByteArray.gzipped(): ByteArray {
            val body = ByteArrayOutputStream(size)
            body.use { out ->
                GZIPOutputStream(out).use { gzip ->
                    gzip.write(this@gzipped)
                    gzip.flush()
                }
            }
            return body.toByteArray()
        }
    }
}
