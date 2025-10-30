package com.bugsnag.android.performance.context

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.internal.SpanContextStack

/**
 * `SpanContextStorage` with a composite structure where a single global `SpanContext` stack is
 * maintained (in the same way as [GlobalSpanContextStorage]) while individual threads may request
 * a (possibly temporary) `ThreadLocal` stack, useful for separating background work from the main
 * user-facing workflows.
 *
 * Setting the [SpanContext.defaultStorage] *must* be done as early as possible, typically in
 * the `Application` static block or constructor:
 * ```kotlin
 * class MyApplicationImpl : Application() {
 *     init {
 *         SpanContext.defaultStorage = HybridSpanContextStorage()
 *     }
 * ```
 *
 * @see GlobalSpanContextStorage
 */
public class HybridSpanContextStorage : SpanContextStorage {
    private val globalSpanContextStorage = GlobalSpanContextStorage()
    private val threadLocalStack = ThreadLocal<SpanContextStack>()

    override val currentContext: SpanContext?
        get() {
            val stack = threadLocalStack.get()
            return if (stack != null) {
                stack.top
            } else {
                globalSpanContextStorage.currentContext
            }
        }

    override val currentStack: Sequence<SpanContext>
        get() {
            val stack = threadLocalStack.get()
            return stack?.stackSequence() ?: globalSpanContextStorage.currentStack
        }

    override fun clear() {
        val stack = threadLocalStack.get()
        if (stack != null) {
            stack.clear()
        } else {
            globalSpanContextStorage.clear()
        }
    }

    override fun attach(spanContext: SpanContext) {
        val stack = threadLocalStack.get()
        if (stack != null) {
            stack.attach(spanContext)
        } else {
            globalSpanContextStorage.attach(spanContext)
        }
    }

    override fun detach(spanContext: SpanContext) {
        val stack = threadLocalStack.get()
        if (stack != null) {
            stack.detach(spanContext)
        } else {
            globalSpanContextStorage.detach(spanContext)
        }
    }

    /**
     * Attempt to start a thread-local trace if none is already active. This effectively isolates
     * spans created by the current thread from the rest of the app, allowing background workers
     * to produce traces without any accidental parentage to spans active for the user-facing
     * processes.
     *
     * This method is a no-op if the current thread already has a thread local trace active.
     *
     * @return true only if a new thread local trace was created, false if one was already active
     *
     * @see [endThreadLocalTrace]
     * @see [tryStartThreadLocalTrace]
     */
    public fun startThreadLocalTrace(): Boolean {
        if (threadLocalStack.get() != null) {
            return false
        }

        threadLocalStack.set(SpanContextStack())
        return true
    }

    public fun endThreadLocalTrace() {
        threadLocalStack.remove()
    }

    public companion object {
        /**
         * Attempt to start a thread-local trace if the [SpanContext.defaultStorage] is a
         * [HybridSpanContextStorage] (otherwise silently no-op). This is a convenient way to
         * clearly delineate work done on a background thread that should be tracked independently
         * from the main user-facing trace.
         *
         * @return true if a thread local trace was started by this call, false otherwise
         * @see [tryEndThreadLocalTrace]
         * @see [threadLocalTrace]
         */
        @JvmStatic
        public fun tryStartThreadLocalTrace(): Boolean {
            return (SpanContext.defaultStorage as? HybridSpanContextStorage)?.startThreadLocalTrace() == true
        }

        /**
         * Attempt to end a thread-local trace if the [SpanContext.defaultStorage] is a
         * [HybridSpanContextStorage] (otherwise silently no-op).
         *
         * @see [tryStartThreadLocalTrace]
         * @see [threadLocalTrace]
         */
        @JvmStatic
        public fun tryEndThreadLocalTrace() {
            (SpanContext.defaultStorage as? HybridSpanContextStorage)?.endThreadLocalTrace()
        }

        /**
         * Convenience for running a block of code within a thread local "trace", typically on
         * a background thread. This is a convenient way to clearly delineate work done on a
         * background thread that should be tracked independently from the main user-facing trace.
         *
         * @see [tryStartThreadLocalTrace]
         */
        @JvmStatic
        public inline fun <R> threadLocalTrace(start: () -> R): R {
            tryStartThreadLocalTrace()
            try {
                return start()
            } finally {
                tryEndThreadLocalTrace()
            }
        }
    }
}
