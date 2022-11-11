package com.bugsnag.android.performance

import android.os.SystemClock
import androidx.annotation.FloatRange
import com.bugsnag.android.performance.internal.SpanProcessor
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.atomic.AtomicLongFieldUpdater
import kotlin.random.Random

/**
 * Represents an ongoing or complete performance measurement and the associated attributes.
 * `Span`s are typically used in a try-with-resources in Java, or with the `use` extension function
 * in Kotlin to ensure they are [closed](Span.end).
 *
 * Spans may not be changed once they have been closed.
 *
 * @see BugsnagPerformance.startSpan
 */
class Span internal constructor(
    name: String,
    val kind: SpanKind,
    val startTime: Long,
    val traceId: UUID,
    val id: Long = Random.nextLong(),
    private val processor: SpanProcessor,
) : Closeable, HasAttributes {

    override val attributes: Attributes = Attributes()

    /**
     * The name of this `Span`
     */
    var name: String = name
        set(value) {
            check(isOpen()) { "span '$field' is closed and cannot be modified" }
            val typeSeparator = field.indexOf('/')

            field =
                if (typeSeparator == -1) value
                else field.substring(0, typeSeparator + 1) + value
        }

    /**
     * The time that this `Span` ended at, or [NO_END_TIME] if still open.
     */
    @Volatile
    var endTime: Long = NO_END_TIME
        private set

    internal val samplingValue: Double

    @FloatRange(from = 0.0, to = 1.0)
    internal var samplingProbability: Double = 1.0
        internal set(value) {
            require(value in 0.0..1.0) { "samplingProbability out of range (0..1): $value" }
            field = value
            attributes["bugsnag.sampling.p"] = value
        }

    /*
     * Internally Spans form a linked-list when they are queued for delivery. By making each `Span`
     * into a natural link we avoid needing a dedicated `Link` structure or allocation, we
     * also avoid the need for an array or List to contain the spans that are pending delivery.
     */
    @JvmField
    @JvmSynthetic
    internal var previous: Span? = null

    init {
        // Our "random" sampling value is actually derived from the traceId
        val msw = traceId.mostSignificantBits ushr 1
        samplingValue = when (msw) {
            0L -> 0.0
            else -> msw.toDouble() / Long.MAX_VALUE.toDouble()
        }
    }

    /**
     * End this with a specified timestamp relative to [SystemClock.elapsedRealtimeNanos]. If this
     * span has already been closed this will have no effect.
     */
    fun end(endTime: Long) {
        if (END_TIME_UPDATER.compareAndSet(this, NO_END_TIME, endTime)) {
            processor.onEnd(this)
        }
    }

    /**
     * End this span now. This is the same as `end(SystemClock.elapsedRealtimeNanos())`.
     */
    fun end() = end(SystemClock.elapsedRealtimeNanos())

    /**
     * Convenience function to call [end], implementing the [Closeable] interface and allowing
     * `Span` to be used with try-with-resources in Java.
     */
    override fun close() = end()

    /**
     * Returns `true` if this span has not yet been closed / ended. If this returns `false` the
     * span cannot be modified further.
     *
     * @see isNotOpen
     */
    fun isOpen() = endTime == NO_END_TIME

    /**
     * Returns `true` if this span has been closed / ended.
     *
     * @see isOpen
     */
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
