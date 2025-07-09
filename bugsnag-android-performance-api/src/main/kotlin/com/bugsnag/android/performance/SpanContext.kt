package com.bugsnag.android.performance

import java.util.UUID
import java.util.concurrent.Callable

public interface SpanContext {
    public val spanId: Long
    public val traceId: UUID

    /**
     * Convenience function to wrap a given [Runnable] in the current [SpanContext].
     * This can be used when submitting tasks to Executors or other targets that are not aware of
     * the BugSnag [SpanContext].
     */
    public fun wrap(runnable: Runnable): Runnable {
        return Runnable {
            try {
                Storage.defaultStorage?.attach(this)
                runnable.run()
            } finally {
                Storage.defaultStorage?.detach(this)
            }
        }
    }

    /**
     * Convenience function to wrap a given [Callable] in the current [SpanContext].
     * This can be used when submitting tasks to Executors or other targets that are not aware of
     * the BugSnag [SpanContext].
     */
    public fun <T> wrap(callable: Callable<T>): Callable<T> {
        return Callable<T> {
            try {
                Storage.defaultStorage?.attach(this)
                callable.call()
            } finally {
                Storage.defaultStorage?.detach(this)
            }
        }
    }

    public companion object Storage {
        @JvmStatic
        public var defaultStorage: SpanContextStorage? = null

        /**
         * Retrieve the current [SpanContext] for this thread (or [invalid] if not available).
         */
        @JvmStatic
        public val current: SpanContext
            get() = defaultStorage?.currentContext ?: invalid

        @JvmStatic
        public val invalid: SpanContext =
            object : SpanContext {
                override val spanId: Long
                    get() = 0
                override val traceId: UUID
                    get() = UUID(0, 0)

                override fun toString() = "InvalidContext"

                override fun wrap(runnable: Runnable): Runnable = runnable

                override fun <T> wrap(callable: Callable<T>): Callable<T> = callable
            }
    }
}

public interface SpanContextStorage {
    public val currentContext: SpanContext?
    public val currentStack: Sequence<SpanContext>

    public fun clear()

    public fun attach(spanContext: SpanContext)

    public fun detach(spanContext: SpanContext)
}
