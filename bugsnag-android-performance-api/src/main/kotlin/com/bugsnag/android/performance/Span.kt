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
 * ### Not stable for inheritance
 * **The `Span` interface is not stable for inheritance in third party libraries**. New methods
 * might be added to this interface in the future. It is stable for use.
 *
 * @see BugsnagPerformance.startSpan
 */
public interface Span : SpanContext, Closeable, HasAttributes {
    /**
     * The name of this span.
     */
    public val name: String

    /**
     * End this with a specified timestamp relative to [SystemClock.elapsedRealtimeNanos]. If this
     * span has already been closed this will have no effect.
     */
    public fun end(endTime: Long)

    /**
     * End this span now. This is the same as `end(SystemClock.elapsedRealtimeNanos())`.
     */
    public fun end()

    /**
     * Convenience function to call [end], implementing the [Closeable] interface and allowing
     * `Span` to be used with try-with-resources in Java.
     */
    override fun close(): Unit = end()

    /**
     * Returns `true` if this span has been closed / ended. If this returns `true` the
     * span cannot be modified further.
     */
    public fun isEnded(): Boolean
}
