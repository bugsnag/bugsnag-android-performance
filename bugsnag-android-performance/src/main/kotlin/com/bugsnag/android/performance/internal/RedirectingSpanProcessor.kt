package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Span
import java.util.concurrent.atomic.AtomicReference

/**
 * A [BatchingSpanProcessor] that can be changed to delegate all of its functionality to another
 * `SpanProcessor` such that any collected spans are sent to the new `destination`, and any spans
 * processed *after* the redirect are also sent there.
 *
 * This class allows us to make late decisions about `SpanProcessor`s, for example: collecting
 * auto-instrumented spans before the applicable options are configured and then either discarding
 * them or redirecting them to be delivered, regardless of when they are ended.
 */
class RedirectingSpanProcessor : BatchingSpanProcessor() {
    private val redirect = AtomicReference<SpanProcessor?>(null)

    /**
     * Redirect all of the spans from this [RedirectingSpanProcessor] to the given [SpanProcessor].
     * This will start by flushing any batched spans to the [destination], and also redirect any
     * future spans as well (effectively turning [RedirectingSpanProcessor.onEnd] into a
     * `destination.onEnd` delegate).
     *
     * Returns `true` if the redirection was completed successfully, and `false` if `redirectTo`
     * has already been called (possibly by a different thread).
     */
    fun redirectTo(destination: SpanProcessor): Boolean {
        if (redirect.compareAndSet(null, destination)) {
            collectBatch().forEach { destination.onEnd(it) }
            return true
        }

        return false
    }

    override fun onEnd(span: Span) {
        val delegate = redirect.get()
        if (delegate != null) {
            delegate.onEnd(span)
        } else {
            super.onEnd(span)
        }
    }
}
