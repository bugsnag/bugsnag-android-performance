package com.bugsnag.performance

import android.os.SystemClock
import android.util.JsonWriter
import java.io.Closeable
import java.util.UUID
import kotlin.random.Random

class Span internal constructor(
    var name: String,
    val kind: SpanKind,
    val startTime: Long,
    val traceId: UUID,
    val id: Long = Random.nextLong(),
    private val processor: SpanProcessor,
) : Closeable {
    var endTime: Long = NO_END_TIME

    /*
     * Internally Spans form a linked-list when they are queued for delivery. By making each `Span`
     * into a natural link we avoid needing a dedicated `Link` structure or allocation.
     */
    @JvmField
    internal var previous: Span? = null

    @JvmOverloads
    fun end(endTime: Long = SystemClock.elapsedRealtimeNanos()) {
        if (this.endTime != NO_END_TIME) {
            throw IllegalStateException("span $name already ended")
        }

        this.endTime = endTime
        processor.onEnd(this)
    }

    override fun close() = end()

    companion object {
        const val NO_END_TIME = -1L
    }
}

@JvmName("-jsonify")
internal fun Span.jsonify(json: JsonWriter) {
    json.beginObject()
        .name("name").value(name)
        .name("kind").value(kind.otel)
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
    return StringBuilder(32)
        .appendHexLong(mostSignificantBits)
        .appendHexLong(leastSignificantBits)
        .toString()
}

private fun Long.toHexString(): String {
    return StringBuilder(16)
        .appendHexLong(this)
        .toString()
}
