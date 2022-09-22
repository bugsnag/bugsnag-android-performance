@file:JvmName("SpanJson")

package com.bugsnag.android.performance

import android.util.JsonWriter
import java.util.UUID

private const val UUID_ID_STRING_LENGTH = 32
private const val LONG_ID_STRING_LENGTH = 16

@JvmName("-jsonify")
internal fun Span.toJson(json: JsonWriter) {
    json.beginObject()
        .name("name").value(name)
        .name("kind").value(kind.otelName)
        .name("spanId").value(id.toHexString())
        .name("traceId").value(traceId.toHexString())
        .name("startTimeUnixNano").value(BugsnagClock.elapsedNanosToUnixTime(startTime).toString())
        .name("endTimeUnixNano").value(BugsnagClock.elapsedNanosToUnixTime(endTime).toString())
        .endObject()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.appendHexPair(b: Byte): StringBuilder {
    if (b < 16) append('0')
    return append(b.toString(16))
}

@Suppress("NOTHING_TO_INLINE")
private inline fun StringBuilder.appendHexLong(value: Long): StringBuilder {
    return appendHexPair(((value ushr 56) and 0xff).toByte())
        .appendHexPair(((value ushr 48) and 0xff).toByte())
        .appendHexPair(((value ushr 40) and 0xff).toByte())
        .appendHexPair(((value ushr 32) and 0xff).toByte())
        .appendHexPair(((value ushr 16) and 0xff).toByte())
        .appendHexPair(((value ushr 8) and 0xff).toByte())
        .appendHexPair((value and 0xff).toByte())
}

private fun UUID.toHexString(): String {
    return StringBuilder(UUID_ID_STRING_LENGTH)
        .appendHexLong(mostSignificantBits)
        .appendHexLong(leastSignificantBits)
        .toString()
}

private fun Long.toHexString(): String {
    return StringBuilder(LONG_ID_STRING_LENGTH)
        .appendHexLong(this)
        .toString()
}

