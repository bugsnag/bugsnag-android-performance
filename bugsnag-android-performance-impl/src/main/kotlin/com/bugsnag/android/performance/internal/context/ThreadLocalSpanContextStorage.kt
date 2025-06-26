package com.bugsnag.android.performance.internal.context

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.internal.SpanContextStack

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ThreadLocalSpanContextStorage : SpanContextStorage {
    private val threadLocalStorage =
        object : ThreadLocal<SpanContextStack>() {
            override fun initialValue(): SpanContextStack = SpanContextStack()
        }

    public var contextStack: SpanContextStack
        get() = threadLocalStorage.get() as SpanContextStack
        set(value) {
            threadLocalStorage.set(value)
        }

    override val currentContext: SpanContext?
        get() = contextStack.top

    override val currentStack: Sequence<SpanContext>
        get() = contextStack.stack
            .asSequence()
            .mapNotNull { it.get() }

    override fun clear() {
        contextStack.clear()
    }

    override fun attach(spanContext: SpanContext) {
        contextStack.attach(spanContext)
    }

    override fun detach(spanContext: SpanContext) {
        contextStack.detach(spanContext)
    }
}
