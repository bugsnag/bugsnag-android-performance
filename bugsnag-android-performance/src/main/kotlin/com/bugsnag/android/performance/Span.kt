package com.bugsnag.android.performance

import android.os.SystemClock
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicLongFieldUpdater
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
            check(isOpen()) { "span '$field' is closed and cannot be modified" }
            val typeSeparator = field.indexOf('/')

            field =
                if (typeSeparator == -1) value
                else field.substring(0, typeSeparator + 1) + value
        }

    @Volatile
    var endTime: Long = NO_END_TIME
        private set

    fun end(endTime: Long) {
        if (END_TIME_UPDATER.compareAndSet(this, NO_END_TIME, endTime)) {
            processor.onEnd(this)
        }
    }

    fun end() = end(SystemClock.elapsedRealtimeNanos())

    val samplingValue: Double
    init {
        // Our "random" sampling value is actually derived from the traceId
        val msw = traceId.mostSignificantBits ushr 1
        samplingValue = when(msw) {
            0L -> 0.0
            else -> msw.toDouble() / Long.MAX_VALUE.toDouble()
        }
    }

    var samplingProbability: Double = 1.0
    set(value) {
        field = value
        attributes.set("bugsnag.sampling.p", value)
    }

    override fun close() = end()

    fun isOpen() = endTime == NO_END_TIME

    fun isNotOpen() = !isOpen()

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
        private val END_TIME_UPDATER =
            AtomicLongFieldUpdater.newUpdater(Span::class.java, "endTime")

        /**
         * The value of [Span.endTime] when the `Span` has not yet [ended](Span.end).
         */
        const val NO_END_TIME = -1L
    }
}
