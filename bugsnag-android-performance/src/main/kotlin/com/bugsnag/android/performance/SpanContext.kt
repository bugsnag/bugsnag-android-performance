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

        private val contextStack: Deque<SpanContext> =
            threadLocalStorage.get() as Deque<SpanContext>

        /**
         * Retrieve the current [SpanContext] for this thread (or [invalid] if not available).
         */
        @JvmStatic
        val current: SpanContext
            get() = contextStack.peekFirst() ?: invalid

        internal fun attach(toAttach: SpanContext) {
            contextStack.push(toAttach)
        }

        internal fun detach(spanContext: SpanContext) {
            val stack = contextStack
            val top = contextStack.pollFirst()

            if (top != spanContext) {
                stack.push(top)
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
