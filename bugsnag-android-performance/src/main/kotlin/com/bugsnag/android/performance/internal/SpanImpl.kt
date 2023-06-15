package com.bugsnag.android.performance.internal

import android.os.SystemClock
import android.util.JsonWriter
import androidx.annotation.FloatRange
import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicLongFieldUpdater

@Suppress("LongParameterList")
class SpanImpl internal constructor(
    name: String,
    internal val category: SpanCategory,
    internal val kind: SpanKind,
    internal val startTime: Long,
    override val traceId: UUID,
    override val spanId: Long = nextSpanId(),
    internal val parentSpanId: Long,
    private val processor: SpanProcessor,
    private val makeContext: Boolean,
) : Span, HasAttributes {

    override val attributes: Attributes = Attributes()

    /**
     * Internally SpanImpl objects can be chained together as a fast linked-list structure
     * (nicknamed [SpanChain]) allowing us a lock-free / allocation-free batching structure.
     *
     * See [Tracer] for more information on how this is used.
     */
    @JvmField
    @JvmSynthetic
    internal var next: SpanImpl? = null

    /**
     * The name of this `Span`
     */
    internal var name: String = name
        set(value) {
            if (!isEnded()) {
                field = value
            }
        }

    /**
     * The time that this `Span` ended at, or [NO_END_TIME] if still open.
     */
    @Volatile
    internal var endTime: Long = NO_END_TIME
        private set

    internal val samplingValue: Double

    @FloatRange(from = 0.0, to = 1.0)
    internal var samplingProbability: Double = 1.0
        internal set(value) {
            require(field in 0.0..1.0) { "samplingProbability out of range (0..1): $value" }
            field = value
            attributes["bugsnag.sampling.p"] = value
        }

    init {
        category.category?.let { attributes["bugsnag.span.category"] = it }
        samplingValue = samplingValueFor(traceId)

        // Starting a Span should cause it to become the current context
        if (makeContext) SpanContext.attach(this)
    }

    override fun end(endTime: Long) {
        if (END_TIME_UPDATER.compareAndSet(this, NO_END_TIME, endTime)) {
            processor.onEnd(this)
            if (makeContext) SpanContext.detach(this)
        }
    }

    override fun end() = end(SystemClock.elapsedRealtimeNanos())

    override fun isEnded() = endTime != NO_END_TIME

    internal fun toJson(json: JsonWriter) {
        json.obj {
            name("name").value(name)
            name("kind").value(kind.otelOrdinal)
            name("spanId").value(spanId.toHexString())
            name("traceId").value(traceId.toHexString())
            name("startTimeUnixNano")
                .value(BugsnagClock.elapsedNanosToUnixTime(startTime).toString())
            name("endTimeUnixNano")
                .value(BugsnagClock.elapsedNanosToUnixTime(endTime).toString())

            if (parentSpanId != 0L) {
                name("parentSpanId").value(parentSpanId.toHexString())
            }

            if (attributes.isNotEmpty()) {
                name("attributes").value(attributes)
            }
        }
    }


    override fun toString(): String {
        return buildString {
            append("Span(")
                .append(kind)
                .append(' ')
                .append(name)

            append(", id=").appendHexLong(spanId)

            if (parentSpanId != 0L) {
                append(", parentId=").appendHexLong(parentSpanId)
            }

            append(", traceId=")
                .appendHexLong(traceId.mostSignificantBits)
                .appendHexLong(traceId.leastSignificantBits)

            append(", startTime=").append(startTime)

            if (endTime == NO_END_TIME) append(", no endTime")
            else append(", endTime=").append(endTime)

            append(')')
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpanImpl

        if (traceId != other.traceId) return false
        if (spanId != other.spanId) return false
        if (parentSpanId != other.parentSpanId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = traceId.hashCode()
        result = 31 * result + spanId.hashCode()
        result = 31 * result + parentSpanId.hashCode()
        return result
    }

    companion object {
        private val END_TIME_UPDATER =
            AtomicLongFieldUpdater.newUpdater(SpanImpl::class.java, "endTime")

        private const val INVALID_ID = 0L

        const val NO_END_TIME = -1L

        private val spanIdRandom = Random(SecureRandom().nextLong())

        private fun nextSpanId(): Long {
            var id: Long
            do {
                id = spanIdRandom.nextLong()
            } while (id == INVALID_ID)
            return id
        }

        // Our "random" sampling value is actually derived from the traceId
        private fun samplingValueFor(traceId: UUID): Double {
            return when (val msw = traceId.mostSignificantBits ushr 1) {
                0L -> 0.0
                else -> msw.toDouble() / Long.MAX_VALUE.toDouble()
            }
        }
    }
}
