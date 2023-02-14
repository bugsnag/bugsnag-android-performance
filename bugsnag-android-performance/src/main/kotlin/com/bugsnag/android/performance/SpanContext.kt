package com.bugsnag.android.performance

import java.util.ArrayDeque
import java.util.Deque
import java.util.UUID

interface SpanContext {
    val spanId: Long
    val traceId: UUID

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
            get() = contextStack.peekFirst() ?: invalid

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
            while((contextStack.peekFirst() as? Span)?.isOpen() == false) {
                contextStack.pollFirst()
            }
        }

        @JvmStatic
        val invalid: SpanContext = object : SpanContext {
            override val spanId: Long
                get() = 0
            override val traceId: UUID
                get() = UUID(0, 0)

            override fun toString() = "InvalidContext"
        }
    }
}
