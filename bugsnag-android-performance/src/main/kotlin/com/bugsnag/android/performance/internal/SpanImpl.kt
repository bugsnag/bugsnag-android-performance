package com.bugsnag.android.performance.internal

import android.os.SystemClock
import androidx.annotation.FloatRange
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.HasAttributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.integration.NotifierIntegration
import com.bugsnag.android.performance.internal.metrics.SpanMetricsSnapshot
import com.bugsnag.android.performance.internal.processing.AttributeLimits
import com.bugsnag.android.performance.internal.processing.JsonTraceWriter
import com.bugsnag.android.performance.internal.processing.Timeout
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor
import java.security.SecureRandom
import java.util.Random
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

@Suppress("LongParameterList", "TooManyFunctions")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SpanImpl internal constructor(
    name: String,
    internal val category: SpanCategory,
    internal val kind: SpanKind,
    @get:JvmName("getStartTime\$internal")
    internal val startTime: Long,
    override val traceId: UUID,
    override val spanId: Long = nextSpanId(),
    public val parentSpanId: Long,
    private val makeContext: Boolean,
    private val attributeLimits: AttributeLimits?,
    internal val metrics: SpanMetricsSnapshot?,
    private val timeoutExecutor: TimeoutExecutor,
    private val processor: SpanProcessor,
) : Span, HasAttributes {
    public val attributes: Attributes = Attributes()

    /**
     * The name of this `Span`
     */
    public var name: String = name
        set(value) {
            if (!isEnded()) {
                field = value
            }
        }

    internal var isSealed: Boolean = false

    @get:JvmName("getEndTime\$internal")
    internal var endTime: Long = 0L
        private set

    /**
     * Internally SpanImpl objects can be chained together as a fast linked-list structure
     * (nicknamed [SpanChain]) allowing us a lock-free / allocation-free batching structure.
     *
     * See [Tracer] for more information on how this is used.
     */
    @JvmField
    @JvmSynthetic
    internal var next: SpanImpl? = null

    internal val samplingValue: Double

    @get:FloatRange(from = 0.0, to = 1.0)
    internal var samplingProbability: Double = 1.0
        set(
        @FloatRange(from = 0.0, to = 1.0) value
        ) {
            field = value.coerceIn(0.0, 1.0)
            attributes["bugsnag.sampling.p"] = field
        }

    internal var droppedAttributesCount: Int = 0

    private var customAttributesCount: Int = 0

    private val state = SpanState()

    private var conditions: MutableSet<ConditionImpl>? = null

    init {
        samplingProbability = 1.0

        attributes["bugsnag.span.category"] = category.category
        samplingValue = samplingValueFor(traceId)

        // Starting a Span should cause it to become the current context
        if (makeContext) SpanContext.attach(this)
    }

    override fun end(endTime: Long) {
        if (state.ending()) {
            markEndTime(endTime)

            if (makeContext) SpanContext.detach(this)
            NotifierIntegration.onSpanEnded(this)
            state.end()

            if (!isBlocked()) {
                sendForProcessing()
            }
        }
    }

    @JvmName("markEndTime\$internal")
    internal fun markEndTime(endTime: Long) {
        this.endTime = endTime
        metrics?.finish(this)
    }

    @JvmName("sendForProcessing\$internal")
    internal fun sendForProcessing() {
        if (state.process()) {
            processor.onEnd(this)
        }
    }

    /**
     * Deliberately discard this `SpanImpl`. This will mark the span as ended, but not record
     * a valid end time. It will also (if required) detach the Span from the SpanContext
     */
    public fun discard() {
        if (state.discard()) {
            if (conditions != null) {
                synchronized(this) {
                    // ensure all of the conditions are released if discarded
                    conditions?.forEach { condition ->
                        condition.cancel()
                    }

                    conditions = null
                }
            }

            NotifierIntegration.onSpanEnded(this)
            if (makeContext) SpanContext.detach(this)
        }
    }

    public fun block(timeoutMs: Long): Condition? {
        synchronized(this) {
            if (state.block() || conditions != null) {
                if (conditions == null) {
                    conditions = HashSet()
                }

                val condition = ConditionImpl(this, SystemClock.elapsedRealtime() + timeoutMs)
                timeoutExecutor.scheduleTimeout(condition)
                conditions?.add(condition)
                return condition
            }
        }

        return null
    }

    override fun end(): Unit = end(SystemClock.elapsedRealtimeNanos())

    public fun isSampled(): Boolean = samplingValue <= samplingProbability

    override fun isEnded(): Boolean = !state.isOpen

    public fun isOpen(): Boolean = state.isOpen

    public fun isBlocked(): Boolean = state.isBlocked && (conditions == null || conditions?.isNotEmpty() == true)

    internal fun toJson(json: JsonTraceWriter) {
        json.writeSpan(this) {
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

            if (attributes.size > 0) {
                name("attributes").value(attributes)
            }

            if (droppedAttributesCount > 0) {
                name("droppedAttributesCount").value(droppedAttributesCount)
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

            if (state.isOpen) {
                append(", no endTime")
            } else {
                append(", endTime=").append(endTime)
            }

            append(')')
        }
    }

    override fun setAttribute(
        name: String,
        value: String?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Long,
    ) {
        if (!isSealed) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Int,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Double,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Boolean,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Array<String>?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: Collection<Any>?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: IntArray?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: LongArray?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    override fun setAttribute(
        name: String,
        value: DoubleArray?,
    ) {
        setAttributeImpl(name) {
            attributes[name] = value
        }
    }

    private inline fun setAttributeImpl(
        name: String,
        setter: () -> Unit,
    ) {
        val attributeCountLimit = attributeLimits?.attributeCountLimit ?: Int.MAX_VALUE
        if (!isSealed) {
            if (name in attributes) {
                setter()
            } else if (customAttributesCount < attributeCountLimit) {
                setter()
                customAttributesCount++
            } else {
                droppedAttributesCount++
            }
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

    public companion object {
        private const val INVALID_ID = 0L

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

    /**
     * Represents a possible "block" to a Span being ended naturally. Any conditions that are active
     * when [SpanImpl.end] is called will prevent the span from being batched and processed, until
     * all of the [Condition]s are either cancelled, timed-out, or closed. A condition is considered
     * "inactive" until it is [upgrade]ed, at which point the [Condition.close] will cause the span
     * end-time to be adjusted.
     *
     * These are a way to deal with cases where a span "might have child spans at some point in the
     * future, where the parent span end time should match the last of these children".
     */
    public interface Condition {
        /**
         * Close this [Condition] possibly releasing the underlying Span for batching and
         * processing. The Span end time may be adjusted to [endTime] if it is strictly after the
         * existing Span end time.
         */
        public fun close(endTime: Long = SystemClock.elapsedRealtimeNanos())

        /**
         * Attempt to upgrade this [Condition]. A Condition may only be upgraded only be upgraded
         * once, and must still be valid (not closed, or cancelled) in order to be upgraded.
         *
         * @return the [SpanContext] that the [Condition] was upgraded to, or `null` if this
         * [Condition] cannot be upgraded
         */
        public fun upgrade(): SpanContext?

        /**
         * Cancel this [Condition] possibly releasing the underlying Span for batching and
         * processing. This function has the same effect as the [Condition] passing its timeout.
         * The [Span] this condition is blocking will not be altered by this function.
         */
        public fun cancel()

        /**
         * Wrap a span in a span which also closes this `Condition` when it ends. This does not
         * upgrade or change the `Condition` in any way, the `Condition` should already be
         * [upgrade]ed when this function is called.
         */
        public fun wrap(span: Span): Span {
            return object : Span by span {
                override fun end(endTime: Long) {
                    span.end(endTime)
                    this@Condition.close(endTime)
                }

                override fun end() {
                    span.end()
                    this@Condition.close()
                }
            }
        }
    }

    private class ConditionImpl(
        private val span: SpanImpl,
        override val target: Long,
    ) : Condition, Timeout {
        private var isValid = true
        private var isUpgraded = false

        override fun close(endTime: Long) {
            releaseCondition {
                if (isUpgraded) {
                    span.endTime = max(endTime, span.endTime)
                }
            }
        }

        override fun upgrade(): SpanContext? {
            synchronized(span) {
                if (!isValid) {
                    return null
                }

                span.timeoutExecutor.cancelTimeout(this)
                isUpgraded = true

                return span
            }
        }

        override fun run() {
            cancel()
        }

        override fun cancel() {
            synchronized(span) {
                // an upgraded condition cannot be cancelled
                if (isUpgraded) {
                    return
                }
                releaseCondition()
            }
        }

        private inline fun releaseCondition(completeRelease: () -> Unit = {}) {
            synchronized(span) {
                if (!isValid) {
                    return
                }

                isValid = false

                span.conditions?.remove(this)
                span.timeoutExecutor.cancelTimeout(this)

                completeRelease()

                if (span.conditions.isNullOrEmpty() && span.isEnded()) {
                    // the last condition was cancelled, so the Span is now considered ended
                    span.sendForProcessing()
                }
            }
        }

        override fun toString(): String {
            return "Condition[isValid=$isValid, isUpgraded=$isUpgraded, span=$span]"
        }
    }
}

/**
 * Encapsulation of the span state field. This is implemented as an `AtomicInteger` which can
 * either represent the various states (open, discarded, blocked) of a span.
 */
@JvmInline
internal value class SpanState private constructor(private val state: AtomicInteger) {
    constructor() : this(AtomicInteger(OPEN))

    val isOpen: Boolean get() = state.get().let { it == OPEN || it == OPEN_BLOCKED }
    val isBlocked: Boolean
        get() =
            state.get().let {
                it == OPEN_BLOCKED || it == ENDING_BLOCKED || it == ENDED_BLOCKED
            }
    val isDiscarded: Boolean get() = state.get() == DISCARDED

    fun process(): Boolean {
        while (true) {
            when (val s = state.get()) {
                // discarded & already processed spans cannot be processed
                DISCARDED, PROCESSED -> return false
                else ->
                    if (state.compareAndSet(s, PROCESSED)) {
                        return true
                    }
            }
        }
    }

    fun ending(): Boolean {
        while (true) {
            when (val s = state.get()) {
                OPEN ->
                    if (state.compareAndSet(s, ENDING)) {
                        return true
                    }

                OPEN_BLOCKED ->
                    if (state.compareAndSet(s, ENDING_BLOCKED)) {
                        return true
                    }

                else -> return false
            }
        }
    }

    fun end(): Boolean {
        while (true) {
            when (val s = state.get()) {
                OPEN, ENDING ->
                    if (state.compareAndSet(s, ENDED)) {
                        return true
                    }

                OPEN_BLOCKED, ENDING_BLOCKED ->
                    if (state.compareAndSet(s, ENDED_BLOCKED)) {
                        return true
                    }

                else -> return false
            }
        }
    }

    /**
     * Mark the span as discarded returning `true` if the span is definitely considered discarded.
     * A span can only be discarded if it is currently considered open or blocked (or has already
     * been discarded). If the span was already [end]ed this method will return `false`.
     */
    fun discard(): Boolean {
        while (true) {
            when (val s = state.get()) {
                OPEN, OPEN_BLOCKED, ENDING, ENDING_BLOCKED ->
                    if (state.compareAndSet(s, DISCARDED)) {
                        return true
                    }

                else -> return s == DISCARDED
            }
        }
    }

    fun block(): Boolean {
        while (true) {
            when (val s = state.get()) {
                OPEN ->
                    if (state.compareAndSet(s, OPEN_BLOCKED)) {
                        return true
                    }

                ENDING ->
                    if (state.compareAndSet(s, ENDING_BLOCKED)) {
                        return true
                    }

                else -> return s == OPEN_BLOCKED || s == ENDING_BLOCKED || s == ENDED_BLOCKED
            }
        }
    }

    override fun toString(): String =
        when (state.get()) {
            OPEN -> "open"
            DISCARDED -> "discarded"
            OPEN_BLOCKED -> "blocked"
            else -> "ended"
        }

    companion object {
        internal const val OPEN: Int = -1
        internal const val DISCARDED: Int = -2
        internal const val OPEN_BLOCKED: Int = -3
        internal const val ENDING: Int = -4
        internal const val ENDING_BLOCKED: Int = -5
        internal const val ENDED: Int = -6
        internal const val ENDED_BLOCKED: Int = -7
        internal const val PROCESSED = -8
    }
}
