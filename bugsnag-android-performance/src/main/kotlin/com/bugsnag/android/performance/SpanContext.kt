package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanContextStack
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
                SpanContext.attach(this)
                runnable.run()
            } finally {
                SpanContext.detach(this)
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
                SpanContext.attach(this)
                callable.call()
            } finally {
                SpanContext.detach(this)
            }
        }
    }

    public companion object Storage {
        private val threadLocalStorage =
            object : ThreadLocal<SpanContextStack>() {
                override fun initialValue(): SpanContextStack = SpanContextStack()
            }

        @get:JvmSynthetic
        internal var contextStack: SpanContextStack
            get() = threadLocalStorage.get() as SpanContextStack
            set(value) {
                threadLocalStorage.set(value)
            }

        /**
         * Retrieve the current [SpanContext] for this thread (or [invalid] if not available).
         */
        @JvmStatic
        public val current: SpanContext
            get() = contextStack.top ?: invalid

        @JvmSynthetic
        internal fun attach(toAttach: SpanContext) {
            contextStack.attach(toAttach)
        }

        @JvmSynthetic
        internal fun detach(spanContext: SpanContext) {
            contextStack.detach(spanContext)
        }

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
