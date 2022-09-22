package com.bugsnag.performance

import android.os.SystemClock
import com.bugsnag.performance.Span.Companion.NO_END_TIME
import java.io.Closeable
import java.util.UUID
import kotlin.random.Random

interface Span : Closeable {
    var name: String
    val kind: SpanKind
    val startTime: Long
    val traceId: UUID
    val id: Long
    val endTime: Long

    fun end()

    fun end(endTime: Long = SystemClock.elapsedRealtimeNanos())

    override fun close()

    companion object {
        /**
         * The value of [Span.endTime] when the `Span` has not yet [ended](Span.end).
         */
        const val NO_END_TIME = -1L
    }
}

internal class SpanImpl(
    name: String,
    override val kind: SpanKind,
    override val startTime: Long,
    override val traceId: UUID,
    override val id: Long = Random.nextLong(),
    private val processor: SpanProcessor,
) : Span {
    override var name: String = name
        set(value) {
            require(endTime != NO_END_TIME) {
                "span '$name' is closed and cannot be modified"
            }

            field = value
        }

    override var endTime: Long = NO_END_TIME

    /*
     * Internally Spans form a linked-list when they are queued for delivery. By making each `Span`
     * into a natural link we avoid needing a dedicated `Link` structure or allocation.
     */
    @JvmField
    internal var previous: SpanImpl? = null

    override fun end(endTime: Long) {
        if (this.endTime != NO_END_TIME) {
            throw IllegalStateException("span $name already ended")
        }

        this.endTime = endTime
        processor.onEnd(this)
    }

    override fun close() = end()

    override fun end() = end(SystemClock.elapsedRealtimeNanos())
}
