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
) : Closeable {
    var name: String = name
        set(value) {
            if (endTime != NO_END_TIME) {
                throw IllegalStateException("span '$name' is closed and cannot be modified")
            }

            field = value
        }

    @set:JvmSynthetic
    @set:JvmName("-setEndTime")
    var endTime: Long = NO_END_TIME
        internal set

    /*
     * Internally Spans form a linked-list when they are queued for delivery. By making each `Span`
     * into a natural link we avoid needing a dedicated `Link` structure or allocation, we
     * also avoid the need for an array or List to contain the spans that are pending delivery.
     */
    @JvmField
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

    companion object {
        /**
         * The value of [Span.endTime] when the `Span` has not yet [ended](Span.end).
         */
        const val NO_END_TIME = -1L
    }
}
