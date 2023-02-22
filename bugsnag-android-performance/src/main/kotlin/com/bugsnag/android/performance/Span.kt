package com.bugsnag.android.performance

import android.os.SystemClock
import java.io.Closeable

/**
 * Represents an ongoing or complete performance measurement and the associated attributes.
 * `Span`s are typically used in a try-with-resources in Java, or with the `use` extension function
 * in Kotlin to ensure they are [closed](Span.end).
 *
 * Spans may not be changed once they have been closed.
 *
 * @see BugsnagPerformance.startSpan
 */
abstract class Span internal constructor() : SpanContext, Closeable {
    /**
     * End this with a specified timestamp relative to [SystemClock.elapsedRealtimeNanos]. If this
     * span has already been closed this will have no effect.
     */
    abstract fun end(endTime: Long)

    /**
     * End this span now. This is the same as `end(SystemClock.elapsedRealtimeNanos())`.
     */
    abstract fun end()

    /**
     * Convenience function to call [end], implementing the [Closeable] interface and allowing
     * `Span` to be used with try-with-resources in Java.
     */
    override fun close() = end()

    /**
     * Returns `true` if this span has been closed / ended. If this returns `true` the
     * span cannot be modified further.
     */
    abstract fun isEnded(): Boolean
}
