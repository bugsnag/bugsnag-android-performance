package com.bugsnag.android.performance

import android.os.SystemClock
import java.io.Closeable
import java.util.UUID
import kotlin.random.Random

class Span internal constructor(
    name: String,
    val kind: SpanKind,
    val startTime: Long,
    val traceId: UUID,
    val id: Long = Random.nextLong(),
    private val processor: SpanProcessor,
) : Closeable, HasAttributes {

    override val attributes: Attributes = Attributes()

    var name: String = name
        set(value) {
            if (endTime != NO_END_TIME) {
                throw IllegalStateException("span '$name' is closed and cannot be modified")
            }

            field = value
        }

    @set:JvmSynthetic
    var endTime: Long = NO_END_TIME
        internal set

    /*
     * Internally Spans form a linked-list when they are queued for delivery. By making each `Span`
     * into a natural link we avoid needing a dedicated `Link` structure or allocation, we
     * also avoid the need for an array or List to contain the spans that are pending delivery.
     */
    @JvmField
    @JvmSynthetic
    internal var previous: Span? = null

    fun end(endTime: Long) {
        if (this.endTime != NO_END_TIME) {
            return
        }

        this.endTime = endTime
        processor.onEnd(this)
    }

    fun end() = end(SystemClock.elapsedRealtimeNanos())

    override fun close() = end()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Span

        if (kind != other.kind) return false
        if (startTime != other.startTime) return false
        if (traceId != other.traceId) return false
        if (id != other.id) return false
        if (attributes != other.attributes) return false
        if (name != other.name) return false
        if (endTime != other.endTime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = kind.hashCode()
        result = 31 * result + startTime.hashCode()
        result = 31 * result + traceId.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + attributes.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + endTime.hashCode()
        return result
    }

    override fun toString(): String {
        return buildString {
            append("Span(")
                .append(kind)
                .append(' ')
                .append(name)

            append(", id=").append(id)
            append(", traceId=").append(traceId)
            append(", startTime=").append(startTime)

            if (endTime == NO_END_TIME) append(", no endTime")
            else append(", endTime=").append(endTime)

            append(')')
        }
    }

    companion object {
        /**
         * The value of [Span.endTime] when the `Span` has not yet [ended](Span.end).
         */
        const val NO_END_TIME = -1L
    }
}
