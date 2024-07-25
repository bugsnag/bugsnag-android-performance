package com.bugsnag.android.performance.internal

import android.os.SystemClock
import android.util.JsonWriter
import androidx.annotation.FloatRange
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@Suppress("LongParameterList", "TooManyFunctions")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanImpl internal constructor(
    name: String,
    internal val category: SpanCategory,
    internal val kind: SpanKind,
    internal val startTime: Long,
    override val traceId: UUID,
    override val spanId: Long = nextSpanId(),
    public val parentSpanId: Long,
    private val processor: SpanProcessor,
    private val makeContext: Boolean,
) : Span, HasAttributes {

    public val attributes: Attributes = Attributes()

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
    public var name: String = name
        set(value) {
            if (!isEnded()) {
                field = value
            }
        }

    /**
     * The time that this `Span` ended at, or [NO_END_TIME] if still open.
     */
    internal val endTime: AtomicLong = AtomicLong(NO_END_TIME)

    internal val samplingValue: Double

    @get:FloatRange(from = 0.0, to = 1.0)
    internal var samplingProbability: Double = 1.0
        set(@FloatRange(from = 0.0, to = 1.0) value) {
            field = value.coerceIn(0.0, 1.0)
            attributes["bugsnag.sampling.p"] = field
        }

    init {
        samplingProbability = 1.0
    }

    init {
        category.category?.let { attributes["bugsnag.span.category"] = it }
        samplingValue = samplingValueFor(traceId)

        // Starting a Span should cause it to become the current context
        if (makeContext) SpanContext.attach(this)
    }

    override fun end(endTime: Long) {
        if (this.endTime.compareAndSet(NO_END_TIME, endTime)) {
            processor.onEnd(this)
            NotifierIntegration.onSpanEnded(this)
            if (makeContext) SpanContext.detach(this)
        }
    }

    /**
     * Deliberately discard this `SpanImpl`. This will mark the span as ended, but not record
     * a valid end time. It will also (if required) detach the Span from the SpanContext
     */
    public fun discard() {
        if (endTime.compareAndSet(NO_END_TIME, DISCARDED)) {
            NotifierIntegration.onSpanEnded(this)
            if (makeContext) SpanContext.detach(this)
        }
    }

    override fun end(): Unit = end(SystemClock.elapsedRealtimeNanos())

    override fun isEnded(): Boolean = endTime.get() != NO_END_TIME

    internal fun toJson(json: JsonWriter) {
        json.obj {
            name("name").value(name)
            name("kind").value(kind.otelOrdinal)
            name("spanId").value(spanId.toHexString())
            name("traceId").value(traceId.toHexString())
            name("startTimeUnixNano")
                .value(BugsnagClock.elapsedNanosToUnixTime(startTime).toString())
            name("endTimeUnixNano")
                .value(BugsnagClock.elapsedNanosToUnixTime(endTime.get()).toString())

            if (parentSpanId != 0L) {
                name("parentSpanId").value(parentSpanId.toHexString())
            }

            if (attributes.size > 0) {
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

            if (endTime.get() == NO_END_TIME) append(", no endTime")
            else append(", endTime=").append(endTime)

            append(')')
        }
    }

    override fun setAttribute(name: String, value: String?) {
        if (!isEnded()) {
            attributes[name] = value
        }
    }

    override fun setAttribute(name: String, value: Long) {
        if (!isEnded()) {
            attributes[name] = value
        }
    }

    override fun setAttribute(name: String, value: Int) {
        if (!isEnded()) {
            attributes[name] = value
        }
    }

    override fun setAttribute(name: String, value: Double) {
        if (!isEnded()) {
            attributes[name] = value
        }
    }

    override fun setAttribute(name: String, value: Boolean) {
        if (!isEnded()) {
            attributes[name] = value
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

    public fun isSampled(): Boolean =
        samplingValue <= samplingProbability

    public companion object {
        private const val INVALID_ID = 0L

        internal const val NO_END_TIME: Long = -1L
        internal const val DISCARDED: Long = -2L

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
