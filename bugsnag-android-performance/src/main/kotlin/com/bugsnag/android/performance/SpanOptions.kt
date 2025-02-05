package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.SpanOptions.Companion.DEFAULTS

/**
 * Optional configuration used when starting a new `Span`. `SpanOptions` are immutable and
 * shareable, but internally track which options have been overridden. As such not setting a value
 * will cause its default to be used (example: not setting [startTime] will cause
 * `SystemClock.elapsedRealtimeNanos` to be used).
 *
 * New `SpanOptions` are typically based on the [DEFAULTS]:
 * ```kotlin
 * BugsnagPerformance.startSpan("LoadSuggestions", SpanOptions.startTime(actualStartTime))
 * ```
 *
 * @see DEFAULTS
 * @see BugsnagPerformance.startSpan
 */
public class SpanOptions private constructor(
    private val optionsSet: Int,
    startTime: Long,
    parentContext: SpanContext?,
    public val makeContext: Boolean,
    isFirstClass: Boolean,
    spanMetrics: SpanMetrics?,
) {
    private val _startTime: Long = startTime

    private val _isFirstClass: Boolean = isFirstClass

    private val _parentContext: SpanContext? = parentContext

    private val _spanMetrics: SpanMetrics? = spanMetrics

    /**
     * Return the time (relative to [SystemClock.elapsedRealtimeNanos]) that new `Span`s will
     * be reported with. Defaults to returning [SystemClock.elapsedRealtimeNanos] if no specific
     * start time is specified.
     */
    public val startTime: Long
        get() =
            if (isOptionSet(OPT_START_TIME)) _startTime
            else SystemClock.elapsedRealtimeNanos()

    public val isFirstClass: Boolean?
        get() = _isFirstClass.takeIf { isOptionSet(OPT_IS_FIRST_CLASS) }

    public val parentContext: SpanContext?
        get() =
            if (isOptionSet(OPT_PARENT_CONTEXT)) _parentContext
            else SpanContext.current

    public val instrumentRendering: Boolean?
        get() = _spanMetrics?.rendering

    public val spanMetrics: SpanMetrics?
        get() = _spanMetrics

    /**
     * Override the start time of new `Span`s created with these `SpanOptions`. This is useful when
     * the start time is reported from an external system.
     *
     * @param startTime the start time to report relative to [SystemClock.elapsedRealtimeNanos]
     */
    public fun startTime(startTime: Long): SpanOptions = SpanOptions(
        optionsSet or OPT_START_TIME,
        startTime,
        _parentContext,
        makeContext,
        _isFirstClass,
        _spanMetrics,
    )

    public fun within(parentContext: SpanContext?): SpanOptions = SpanOptions(
        optionsSet or OPT_PARENT_CONTEXT,
        _startTime,
        parentContext,
        makeContext,
        _isFirstClass,
        _spanMetrics,
    )

    public fun makeCurrentContext(makeContext: Boolean): SpanOptions = SpanOptions(
        optionsSet or OPT_MAKE_CONTEXT,
        _startTime,
        _parentContext,
        makeContext,
        _isFirstClass,
        _spanMetrics,
    )

    public fun setFirstClass(isFirstClass: Boolean): SpanOptions = SpanOptions(
        optionsSet or OPT_IS_FIRST_CLASS,
        _startTime,
        _parentContext,
        makeContext,
        isFirstClass,
        _spanMetrics,
    )

    @Deprecated(
        message = "use spanMetrics.rendering",
        replaceWith = ReplaceWith(
            expression = "withMetrics(SpanMetrics(rendering = instrumentRendering))",
            imports = ["com.bugsnag.android.performance.SpanMetrics"],
        ),
    )
    public fun withRenderingMetrics(instrumentRendering: Boolean): SpanOptions {
        return if (_spanMetrics != null) {
            withMetrics(
                SpanMetrics(
                    rendering = instrumentRendering,
                    cpu = _spanMetrics.cpu,
                    memory = _spanMetrics.memory,
                ),
            )
        } else withMetrics(SpanMetrics(rendering = instrumentRendering))
    }

    /**
     * Set the metrics that should be captured with the spans. Adding metrics that have been
     * disabled via [PerformanceConfiguration.enabledMetrics] will have no effect (as they are
     * not being captured). Calling this with an explicit `null` (`withMetrics(null)`) will capture
     * only the default metrics for the span (see [SpanMetrics] for more details).
     */
    @JvmOverloads
    public fun withMetrics(
        spanMetrics: SpanMetrics? = SpanMetrics(rendering = true, cpu = true, memory = true),
    ): SpanOptions = SpanOptions(
        optionsSet or OPT_METRICS,
        _startTime,
        _parentContext,
        makeContext,
        _isFirstClass,
        spanMetrics,
    )

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is SpanOptions) return false

        if ((isOptionSet(OPT_START_TIME) || other.isOptionSet(OPT_START_TIME))
            && this._startTime != other._startTime
        ) return false

        if ((isOptionSet(OPT_PARENT_CONTEXT) || other.isOptionSet(OPT_PARENT_CONTEXT))
            && this.parentContext != other.parentContext
        ) return false

        if ((isOptionSet(OPT_MAKE_CONTEXT) || other.isOptionSet(OPT_MAKE_CONTEXT))
            && this.makeContext != other.makeContext
        ) return false

        if ((isOptionSet(OPT_IS_FIRST_CLASS) || other.isOptionSet(OPT_IS_FIRST_CLASS))
            && this.isFirstClass != other.isFirstClass
        ) return false

        if ((isOptionSet(OPT_METRICS) || other.isOptionSet(OPT_METRICS))
            && this.spanMetrics != other.spanMetrics
        ) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 * _startTime.hashCode()
        result = 31 * result + (parentContext?.hashCode() ?: 0)
        result = 31 * result + makeContext.hashCode()
        result = 31 * result + _isFirstClass.hashCode()
        return result
    }

    override fun toString(): String = buildString {
        append("SpanOptions[")

        // early exit for no-options
        if (optionsSet == 0) {
            append(']')
            return@buildString
        }

        if (isOptionSet(OPT_START_TIME)) {
            append("startTime=").append(_startTime).append(',')
        }

        if (isOptionSet(OPT_PARENT_CONTEXT)) {
            append("parentContext=").append(parentContext).append(',')
        }

        if (isOptionSet(OPT_MAKE_CONTEXT)) {
            append("makeCurrentContext=").append(makeContext).append(',')
        }

        if (isOptionSet(OPT_IS_FIRST_CLASS)) {
            append("isFirstClass=").append(_isFirstClass).append(',')
        }

        if (isOptionSet(OPT_METRICS)) {
            append("metrics=").append(_spanMetrics).append(',')
        }

        // if we are here, the last character will always be ',' - replace it with ']'
        setCharAt(lastIndex, ']')
    }

    private fun isOptionSet(expectedFlag: Int): Boolean =
        (optionsSet and expectedFlag) != 0

    public companion object {
        private const val OPT_NONE = 0
        private const val OPT_START_TIME = 1
        private const val OPT_PARENT_CONTEXT = 2
        private const val OPT_MAKE_CONTEXT = 4
        private const val OPT_IS_FIRST_CLASS = 8
        private const val OPT_METRICS = 16

        /**
         * The default set of `SpanOptions` with no overrides set. Use this as a starting-point to
         * create new `SpanOptions`
         */
        @JvmField
        public val DEFAULTS: SpanOptions =
            SpanOptions(
                OPT_NONE,
                0,
                null,
                makeContext = true,
                isFirstClass = false,
                null,
            )

        @JvmName("createWithStartTime")
        @JvmStatic
        public fun startTime(startTime: Long): SpanOptions = DEFAULTS.startTime(startTime)

        @JvmName("createWithin")
        @JvmStatic
        public fun within(parentContext: SpanContext?): SpanOptions = DEFAULTS.within(parentContext)

        @JvmName("createAsCurrentContext")
        @JvmStatic
        public fun makeCurrentContext(makeContext: Boolean): SpanOptions =
            DEFAULTS.makeCurrentContext(makeContext)

        @JvmName("createFirstClass")
        @JvmStatic
        public fun setFirstClass(isFirstClass: Boolean): SpanOptions =
            DEFAULTS.setFirstClass(isFirstClass)

        @JvmName("createWithRenderingMetrics")
        @JvmStatic
        public fun withRenderingMetrics(instrumentRendering: Boolean): SpanOptions =
            DEFAULTS.withRenderingMetrics(instrumentRendering)

        @JvmName("createWithMetrics")
        @JvmOverloads
        @JvmStatic
        public fun withMetrics(metrics: SpanMetrics? = SpanMetrics(true, true, true)): SpanOptions =
            DEFAULTS.withMetrics(metrics)
    }
}
