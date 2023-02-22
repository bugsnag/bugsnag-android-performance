package com.bugsnag.android.performance

import java.util.ArrayDeque
import java.util.Deque
import java.util.UUID
import java.util.concurrent.Callable

interface SpanContext {
    val spanId: Long
    val traceId: UUID

    /**
     * Convenience function to wrap a given [Runnable] in the current [SpanContext].
     * This can be used when submitting tasks to Executors or other targets that are not aware of
     * the BugSnag [SpanContext].
     */
    fun wrap(runnable: Runnable): Runnable {
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
    fun <T> wrap(callable: Callable<T>): Callable<T> {
        return Callable<T> {
            try {
                SpanContext.attach(this)
                callable.call()
            } finally {
                SpanContext.detach(this)
            }
        }
    }

    companion object Storage {
        private val threadLocalStorage = object : ThreadLocal<Deque<SpanContext>>() {
            override fun initialValue(): Deque<SpanContext> = ArrayDeque()
        }

        @get:JvmSynthetic
        internal val contextStack: Deque<SpanContext>
            get() = threadLocalStorage.get() as Deque<SpanContext>

        /**
         * Retrieve the current [SpanContext] for this thread (or [invalid] if not available).
         */
        @JvmStatic
        val current: SpanContext
            get() {
                removeClosedContexts()
                return contextStack.peekFirst() ?: invalid
            }

        @JvmSynthetic
        internal fun attach(toAttach: SpanContext) {
            contextStack.push(toAttach)
        }

        @JvmSynthetic
        internal fun detach(spanContext: SpanContext) {
            val stack = contextStack
            // assume that the top of the stack is 'spanContext' and 'poll' it off
            // since poll returns null instead of throwing an exception
            val top = contextStack.pollFirst() ?: return

            if (top != spanContext) {
                // oops! the top of the stack wasn't what we expected so we put it back here
                stack.push(top)
            }
            // remove any closed contexts from the top of the stack
            removeClosedContexts()
        }

        @JvmStatic
        val invalid: SpanContext = object : SpanContext {
            override val spanId: Long
                get() = 0
            override val traceId: UUID
                get() = UUID(0, 0)

            override fun toString() = "InvalidContext"

            override fun wrap(runnable: Runnable): Runnable = runnable

            override fun <T> wrap(callable: Callable<T>): Callable<T> = callable
        }

        private fun removeClosedContexts() {
            while((contextStack.peekFirst() as? Span)?.isEnded() == true) {
                contextStack.pollFirst()
            }
        }
    }
}
