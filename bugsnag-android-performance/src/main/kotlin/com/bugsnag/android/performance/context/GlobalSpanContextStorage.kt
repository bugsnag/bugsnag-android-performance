package com.bugsnag.android.performance.context

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.internal.SpanImpl
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * `SpanContextStorage` with a single global stack, applicable when threaded trace separation is
 * not a concern, and all spans should nest under the most recently started span regardless of
 * their thread. This results in traces that behave similar to time-based parentage, spans
 * always naturally nest under the most recently created span.
 *
 * This implementation can have undesirable results when used with background tooling such as
 * `WorkManager` as it does not distinguish between foreground and background work. See
 * [HybridSpanContextStorage] for a more flexible approach that can handle background work.
 *
 * Setting the [SpanContext.defaultStorage] *must* be done as early as possible, typically in
 * the `Application` static block or constructor:
 * ```kotlin
 * class MyApplicationImpl : Application() {
 *     init {
 *         SpanContext.defaultStorage = GlobalSpanContextStorage()
 *     }
 * ```
 *
 * __WARNING__: This class is inherently unsafe with coroutines and will likely lead to misleading
 * traces as the context can remain active when the coroutines suspend. We recommend using the
 * [HybridSpanContextStorage] instead to avoid coroutine spans unexpectedly affecting context.
 *
 * @see HybridSpanContextStorage
 */
public class GlobalSpanContextStorage : SpanContextStorage {
    private val stack = AtomicReference<StackElement?>(null)

    override val currentContext: SpanContext?
        get() {
            while (true) {
                val top = stack.get()
                if (top == null) {
                    return null
                }

                val context = top.get()
                if (context == null || (context as? SpanImpl)?.isEnded() == true) {
                    stack.weakCompareAndSet(top, top.next)
                } else {
                    return context
                }
            }
        }

    override val currentStack: Sequence<SpanContext>
        get() =
            sequence {
                var current = stack.get()
                while (current != null) {
                    val context = current.get()
                    if (context != null) {
                        yield(context)
                    }

                    current = current.next
                }
            }

    override fun clear() {
        stack.set(null)
    }

    override fun attach(spanContext: SpanContext) {
        val newElement = StackElement(spanContext)
        while (true) {
            val oldParent = stack.get()
            newElement.next = oldParent
            if (stack.weakCompareAndSet(oldParent, newElement)) {
                return
            }
        }
    }

    override fun detach(spanContext: SpanContext) {
        while (true) {
            val top = stack.get()
            if (top == null) {
                return
            }

            val context = top.get()
            if (context == null || context == spanContext) {
                if (stack.compareAndSet(top, top.next)) {
                    // pop!
                    return
                }
            } else {
                // unexpected top of stack, return without popping
                return
            }
        }
    }

    private class StackElement(ctx: SpanContext) : WeakReference<SpanContext>(ctx) {
        @JvmField
        var next: StackElement? = null
    }
}
