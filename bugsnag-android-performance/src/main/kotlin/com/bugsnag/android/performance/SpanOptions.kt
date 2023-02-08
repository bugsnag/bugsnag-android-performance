package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.SpanOptions.Companion.defaults

/**
 * Optional configuration used when starting a new `Span`. `SpanOptions` are immutable and
 * shareable, but internally track which options have been overridden. As such not setting a value
 * will cause its default to be used (example: not setting [startTime] will cause
 * `SystemClock.elapsedRealtimeNanos` to be used).
 *
 * @see defaults
 */
class SpanOptions private constructor(
    @JvmField
    @JvmSynthetic
    internal val optionsSet: Int,
    startTime: Long,
    val parentContext: SpanContext?,
    val makeContext: Boolean,
) {
    private val _startTime: Long = startTime

    val startTime: Long
        get() =
            if (isOptionSet(OPT_START_TIME)) _startTime
            else SystemClock.elapsedRealtimeNanos()

    fun startTime(startTime: Long) = SpanOptions(
        optionsSet or OPT_START_TIME,
        startTime,
        parentContext,
        makeContext
    )

    fun within(parentContext: SpanContext) = SpanOptions(
        optionsSet or OPT_PARENT_CONTEXT,
        _startTime,
        parentContext,
        makeContext
    )

    fun makeCurrentContext(makeContext: Boolean) = SpanOptions(
        optionsSet or OPT_MAKE_CONTEXT,
        _startTime,
        parentContext,
        makeContext
    )

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is SpanOptions) return false

        if ((isOptionSet(OPT_START_TIME) || other.isOptionSet(OPT_START_TIME))
            && this._startTime != other._startTime) return false
        if ((isOptionSet(OPT_PARENT_CONTEXT) || other.isOptionSet(OPT_PARENT_CONTEXT))
            && this.parentContext != other.parentContext) return false
        if ((isOptionSet(OPT_MAKE_CONTEXT) || other.isOptionSet(OPT_MAKE_CONTEXT))
            && this.makeContext != other.makeContext) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 31 * _startTime.hashCode()
        result = 31 * result + (parentContext?.hashCode() ?: 0)
        result = 31 * result + makeContext.hashCode()
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

        // if we are here, the last character will always be ',' - replace it with ']'
        setCharAt(lastIndex, ']')
    }

    private fun isOptionSet(expectedFlag: Int): Boolean =
        (optionsSet and expectedFlag) != 0

    companion object {
        private const val OPT_NONE = 0
        private const val OPT_START_TIME = 1
        private const val OPT_PARENT_CONTEXT = 2
        private const val OPT_MAKE_CONTEXT = 4

        /**
         * The default set of `SpanOptions` with no overrides set.
         */
        @JvmField
        val defaults = SpanOptions(OPT_NONE, 0, null, true)
    }
}
