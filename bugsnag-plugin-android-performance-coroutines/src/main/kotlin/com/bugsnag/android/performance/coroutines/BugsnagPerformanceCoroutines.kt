package com.bugsnag.android.performance.coroutines

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.BugsnagPerformanceInternals
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ThreadContextElement
import java.util.ArrayDeque
import java.util.Deque
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ContextAwareCoroutineContextElement(
    private val coroutineContextStack: Deque<SpanContext> = ArrayDeque(),
) : ThreadContextElement<Deque<SpanContext>> {

    constructor(spanContext: SpanContext) : this(ArrayDeque<SpanContext>().apply { push(spanContext) })

    override val key: CoroutineContext.Key<ContextAwareCoroutineContextElement>
        get() = Key

    fun copy() = ContextAwareCoroutineContextElement(ArrayDeque(coroutineContextStack))

    override fun updateThreadContext(context: CoroutineContext): Deque<SpanContext> {
        // coroutine starting/resuming - grab the current SpanContext stack for this thread
        // so that we can restore it when the coroutine is suspended
        val previousStack = BugsnagPerformanceInternals.currentSpanContextStack

        // Replace with the coroutine SpanContext stack
        BugsnagPerformanceInternals.currentSpanContextStack = coroutineContextStack

        return previousStack
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Deque<SpanContext>) {
        // coroutine suspended - restore this thread's previous SpanContext stack
        BugsnagPerformanceInternals.currentSpanContextStack = oldState
    }

    companion object Key : CoroutineContext.Key<ContextAwareCoroutineContextElement>
}

/**
 * Wraps the [SpanContext] into a [CoroutineContext.Element].
 *
 * Maintains a Span Context stack for the coroutine, with the [SpanContext] at the root,
 * which persists the suspend/resume boundary.
 */
fun SpanContext.asCoroutineElement(): CoroutineContext.Element =
    ContextAwareCoroutineContextElement(this)


/**
 * Returns a context containing the [SpanContext] as a [CoroutineContext.Element] and elements
 * from other context.
 */
operator fun SpanContext.plus(context: CoroutineContext): CoroutineContext {
    return context + this.asCoroutineElement()
}

/**
 * Returns a context containing elements from this context and the [SpanContext] as a
 * [CoroutineContext.Element].
 */
operator fun CoroutineContext.plus(context: SpanContext): CoroutineContext {
    return this + context.asCoroutineElement()
}

/**
 * Each time a child coroutine is created (and this `CoroutineContext` is added to) we make
 * a clone of the [ContextAwareCoroutineContextElement] which includes a full copy of the
 * `SpanContext` stack. This means that span parentage tracks logically across child /
 * forked coroutines.
 *
 * This behaviour is similar to `CopyableThreadContextElement` but does not depend on an
 * experimental API. Once `CopyableThreadContextElement` is stable we should consider removing
 * this class.
 */
private class BugsnagCoroutineContext(
    private val baseContext: CoroutineContext,
    private val baseSpanContext: ContextAwareCoroutineContextElement,
) : CoroutineContext {

    override fun plus(context: CoroutineContext): CoroutineContext {
        if (context === EmptyCoroutineContext) return this
        return BugsnagCoroutineContext(baseContext + context, baseSpanContext.copy())
    }

    override fun <R> fold(initial: R, operation: (R, CoroutineContext.Element) -> R): R {
        return baseContext.fold(operation(initial, baseSpanContext), operation)
    }

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
        return if (key === ContextAwareCoroutineContextElement.Key) {
            baseSpanContext as E
        } else {
            baseContext[key]
        }
    }

    override fun minusKey(key: CoroutineContext.Key<*>): CoroutineContext {
        return if (key === ContextAwareCoroutineContextElement.Key) {
            baseContext
        } else {
            BugsnagCoroutineContext(baseContext.minusKey(key), baseSpanContext.copy())
        }
    }
}


/**
 * A Coroutine Scope to automatically create context-aware coroutines
 */
class BugsnagPerformanceScope(dispatcher: CoroutineDispatcher = Dispatchers.Main) : CoroutineScope {
    private val baseContext = SupervisorJob() + dispatcher

    override val coroutineContext: CoroutineContext
        get() = BugsnagCoroutineContext(
            baseContext,
            ContextAwareCoroutineContextElement(SpanContext.current),
        )
}
