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

private class ContextAwareCoroutineContextElement(spanContext: SpanContext) :
    ThreadContextElement<Deque<SpanContext>> {

    override val key: CoroutineContext.Key<ContextAwareCoroutineContextElement>
        get() = Key

    private val coroutineContextStack: Deque<SpanContext> = ArrayDeque()

    init {
        coroutineContextStack.push(spanContext)
    }

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
 * A Coroutine Scope to automatically create context-aware coroutines
 */
class BugsnagPerformanceScope(dispatcher: CoroutineDispatcher = Dispatchers.Main) : CoroutineScope {
    private val baseContext = SupervisorJob() + dispatcher

    override val coroutineContext: CoroutineContext
        get() = baseContext + SpanContext.current.asCoroutineElement()
}
