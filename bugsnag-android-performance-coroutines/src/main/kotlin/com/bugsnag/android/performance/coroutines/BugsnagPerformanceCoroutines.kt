package com.bugsnag.android.performance.coroutines

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.SpanContextStack
import com.bugsnag.android.performance.internal.context.ThreadAwareSpanContextStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

private class ContextAwareCoroutineContextElement(
    private val coroutineContextStack: SpanContextStack?,
) : ThreadContextElement<SpanContextStack?> {
    constructor(spanContext: SpanContext) : this(
        SpanContextStack().apply { attach(spanContext) },
    )

    override val key: CoroutineContext.Key<ContextAwareCoroutineContextElement>
        get() = Key

    fun copy() = ContextAwareCoroutineContextElement(coroutineContextStack?.copy())

    override fun updateThreadContext(context: CoroutineContext): SpanContextStack? {
        // coroutine starting/resuming - grab the current SpanContext stack for this thread
        // so that we can restore it when the coroutine is suspended
        val threadLocalSpanContextStorage =
            SpanContext.defaultStorage as? ThreadAwareSpanContextStorage
                ?: return null
        val previousStack = threadLocalSpanContextStorage.localContextStack

        // Replace with the coroutine SpanContext stack
        threadLocalSpanContextStorage.localContextStack = coroutineContextStack ?: SpanContextStack()

        return previousStack
    }

    override fun restoreThreadContext(
        context: CoroutineContext,
        oldState: SpanContextStack?,
    ) {
        // coroutine suspended - restore this thread's previous SpanContext stack
        val threadLocalSpanContextStorage =
            SpanContext.defaultStorage as? ThreadAwareSpanContextStorage
                ?: return
        // If the old state is null, we are resuming a coroutine that was started without a SpanContext
        if (oldState == null) {
            threadLocalSpanContextStorage.clear()
        }
        // Restore the previous SpanContext stack
        threadLocalSpanContextStorage.localContextStack = oldState
    }

    companion object Key : CoroutineContext.Key<ContextAwareCoroutineContextElement>
}

/**
 * Wraps the [SpanContext] into a [CoroutineContext.Element].
 *
 * Maintains a Span Context stack for the coroutine, with the [SpanContext] at the root,
 * which persists the suspend/resume boundary.
 */
public fun SpanContext.asCoroutineElement(): CoroutineContext.Element = ContextAwareCoroutineContextElement(this)

/**
 * Returns a context containing the [SpanContext] as a [CoroutineContext.Element] and elements
 * from other context.
 */
public operator fun SpanContext.plus(context: CoroutineContext): CoroutineContext {
    return context + this.asCoroutineElement()
}

/**
 * Returns a context containing elements from this context and the [SpanContext] as a
 * [CoroutineContext.Element].
 */
public operator fun CoroutineContext.plus(context: SpanContext): CoroutineContext {
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

    override fun <R> fold(
        initial: R,
        operation: (R, CoroutineContext.Element) -> R,
    ): R {
        return baseContext.fold(operation(initial, baseSpanContext), operation)
    }

    override fun <E : CoroutineContext.Element> get(key: CoroutineContext.Key<E>): E? {
        return if (key === ContextAwareCoroutineContextElement.Key) {
            @Suppress("UNCHECKED_CAST")
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
public class BugsnagPerformanceScope(
    dispatcher: CoroutineDispatcher = Dispatchers.Main,
) : CoroutineScope {
    private val baseContext = SupervisorJob() + dispatcher

    override val coroutineContext: CoroutineContext
        get() =
            BugsnagCoroutineContext(
                baseContext,
                ContextAwareCoroutineContextElement(SpanContext.current),
            )
}
