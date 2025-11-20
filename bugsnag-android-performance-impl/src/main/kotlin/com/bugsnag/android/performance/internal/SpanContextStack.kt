package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import java.lang.ref.WeakReference
import java.util.ArrayDeque

/**
 * WeakReference stack of SpanContexts used by [SpanContext.Storage].
 */
public class SpanContextStack(
    contents: Collection<WeakReference<SpanContext>> = emptyList(),
) : ArrayDeque<WeakReference<SpanContext>>(contents),
    SpanContextStorage {
    /**
     * The current top of the stack or `null` if none is valid or the stack is empty.
     */
    public val top: SpanContext?
        get() {
            while (true) {
                if (isEmpty()) {
                    return null
                }

                val contextRef = peekFirst()
                val context = contextRef?.get()

                if (context == null || (context as? SpanImpl)?.isEnded() == true) {
                    removeFirst()
                } else {
                    return context
                }
            }
        }

    override val currentContext: SpanContext? by this::top
    override val currentStack: Sequence<SpanContext>
        get() = stackSequence()

    public override fun attach(spanContext: SpanContext) {
        if (spanContext == SpanContext.invalid) {
            return
        }

        push(WeakReference(spanContext))
    }

    public override fun detach(spanContext: SpanContext) {
        // assume that the top of the stack is 'spanContext' and 'poll' it off
        // since poll returns null instead of throwing an exception
        val top = pollFirst() ?: return

        if (top.get() != spanContext) {
            // oops! the top of the stack wasn't what we expected so we put it back here
            push(top)
        }
    }

    public fun stackSequence(): Sequence<SpanContext> {
        return asSequence()
            .mapNotNull { it.get() }
    }

    public fun copy(): SpanContextStack {
        return SpanContextStack(this)
    }

    /**
     * Return the top Span for a given SpanCategory, exactly the same as:
     * ```kotlin
     * findSpan { it.category == spanCategory }
     * ```
     */
    public fun current(spanCategory: SpanCategory): SpanImpl? {
        return findSpan { it.category == spanCategory }
    }

    /**
     * Test that no [SpanImpl] objects within the this stack match the given [predicate].
     * Returns `true` if [predicate] only returned `false`, otherwise `false`.
     */
    public inline fun noSpansMatch(predicate: (SpanImpl) -> Boolean): Boolean {
        return none { (it.get() as? SpanImpl)?.let(predicate) == true }
    }

    public inline fun findSpan(predicate: (SpanImpl) -> Boolean): SpanImpl? {
        for (contextRef in this) {
            val spanImpl = contextRef.get() as? SpanImpl
            if (spanImpl != null && predicate(spanImpl)) {
                return spanImpl
            }
        }

        return null
    }
}
