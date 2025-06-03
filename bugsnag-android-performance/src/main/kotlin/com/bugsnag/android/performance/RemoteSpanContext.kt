package com.bugsnag.android.performance

import com.bugsnag.android.performance.RemoteSpanContext.Companion.encodeAsTraceParent
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.appendHexLong
import com.bugsnag.android.performance.internal.appendHexUUID
import com.bugsnag.android.performance.internal.parseUnsignedLong
import java.util.UUID

/**
 * A `SpanContext` implementation that can be constructed from a different system such as a server,
 * or a different app layer (such as JavaScript running in a [WebView] or cross-platform framework).
 *
 * @see [parseTraceParent]
 * @see [parseTraceParentOrNull]
 */
public class RemoteSpanContext(
    override val spanId: Long,
    override val traceId: UUID,
) : SpanContext {
    /**
     * Returns a string representation of the span context in the W3C `traceparent` header format.
     *
     * @see [encodeAsTraceParent]
     */
    override fun toString(): String {
        return encodeAsTraceParent()
    }

    /**
     * Compares this [SpanContext] to another object. Two [SpanContext] objects are considered equal
     * if they have the same `spanId` and `traceId`.
     */
    override fun equals(other: Any?): Boolean {
        return other is SpanContext &&
            other.spanId == spanId &&
            other.traceId == traceId
    }

    override fun hashCode(): Int {
        return spanId.hashCode() xor traceId.hashCode()
    }

    public companion object {
        private val traceParentRegex =
            Regex("^00-([0-9a-f]{32})-([0-9a-f]{16})-[0-9]{2}$")

        private const val TRACE_ID_MID = 16
        private const val TRACE_ID_END = 32

        @JvmStatic
        public fun encodeAsTraceParent(context: SpanContext): String {
            return context.encodeAsTraceParent()
        }

        /**
         * Parses a `traceparent` header string into a [RemoteSpanContext] object. This expects the
         * string to be in the W3C traceparent header format defined as part of the
         * [W3C Trace Context](https://www.w3.org/TR/trace-context/#traceparent-header)
         * specification (version `00`).
         *
         * Trace flags are currently ignored (but must still be valid).
         *
         * @throws IllegalArgumentException if the string cannot be parsed
         * @see [parseTraceParentOrNull]
         */
        @JvmStatic
        public fun parseTraceParent(traceParent: String): RemoteSpanContext {
            return parseTraceParentOrNull(traceParent)
                ?: throw IllegalArgumentException("Invalid traceparent string")
        }

        /**
         * Parses a `traceparent` header string into a [RemoteSpanContext] object. This expects the
         * string to be in the W3C traceparent header format defined as part of the
         * [W3C Trace Context](https://www.w3.org/TR/trace-context/#traceparent-header)
         * specification (version `00`).
         *
         * Trace flags are currently ignored (but must still be valid).
         *
         * Returns `null` if the string cannot be parsed.
         *
         * @see [parseTraceParent]
         */
        @JvmStatic
        public fun parseTraceParentOrNull(traceParent: String): RemoteSpanContext? {
            val match =
                traceParentRegex.matchEntire(traceParent)
                    ?: return null

            val (traceIdHex, spanIdHex) = match.destructured
            val traceId =
                UUID(
                    traceIdHex.substring(0, TRACE_ID_MID).parseUnsignedLong(),
                    traceIdHex.substring(TRACE_ID_MID, TRACE_ID_END).parseUnsignedLong(),
                )
            val spanId = spanIdHex.parseUnsignedLong()

            return RemoteSpanContext(
                spanId = spanId,
                traceId = traceId,
            )
        }
    }
}

private const val TRACEPARENT_LENGTH = 55

private fun buildTraceParentHeader(
    traceId: UUID,
    parentSpanId: Long,
    sampled: Boolean,
): String {
    return buildString(TRACEPARENT_LENGTH) {
        append("00-")
        appendHexUUID(traceId)
        append('-')
        appendHexLong(parentSpanId)
        append('-')
        append(if (sampled) "01" else "00")
    }
}

/**
 * Returns a string representation of the span context in the W3C `traceparent` header format.
 */
public fun SpanContext.encodeAsTraceParent(): String {
    return buildTraceParentHeader(
        traceId = traceId,
        parentSpanId = spanId,
        sampled = if (this is SpanImpl) isSampled() else true,
    )
}
