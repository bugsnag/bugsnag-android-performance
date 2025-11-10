package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.SpanContext
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.Deque

/**
 * WeakReference stack of SpanContexts used by [SpanContext.Storage].
 */
@JvmInline
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public value class SpanContextStack(
    @PublishedApi
    internal val stack: Deque<WeakReference<SpanContext>> = ArrayDeque(),
) {
    /**
     * The current top of the stack or `null` if none is valid or the stack is empty.
     */
    public val top: SpanContext?
        get() {
            while (true) {
                if (stack.isEmpty()) {
                    return null
                }

                val contextRef = stack.peekFirst()
                val context = contextRef?.get()

                if (context == null || (context as? SpanImpl)?.isEnded() == true) {
                    stack.removeFirst()
                } else {
                    return context
                }
            }
        }

    public val size: Int get() = stack.size

    public fun attach(spanContext: SpanContext) {
        if (spanContext == SpanContext.invalid) {
            return
        }

        stack.push(WeakReference(spanContext))
    }

    public fun detach(spanContext: SpanContext) {
        // assume that the top of the stack is 'spanContext' and 'poll' it off
        // since poll returns null instead of throwing an exception
        val top = stack.pollFirst() ?: return

        if (top.get() != spanContext) {
            // oops! the top of the stack wasn't what we expected so we put it back here
            stack.push(top)
        }
    }

    public fun clear() {
        stack.clear()
    }

    public fun stackSequence(): Sequence<SpanContext> {
        return stack.asSequence()
            .mapNotNull { it.get() }
    }

    public fun copy(): SpanContextStack {
        return SpanContextStack(ArrayDeque(stack))
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
        return stack.none { (it.get() as? SpanImpl)?.let(predicate) == true }
    }

    public inline fun findSpan(predicate: (SpanImpl) -> Boolean): SpanImpl? {
        for (contextRef in stack) {
            val spanImpl = contextRef.get() as? SpanImpl
            if (spanImpl != null && predicate(spanImpl)) {
                return spanImpl
            }
        }

        return null
    }
}
