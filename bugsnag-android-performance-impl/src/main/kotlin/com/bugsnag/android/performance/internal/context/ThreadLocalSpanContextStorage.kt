package com.bugsnag.android.performance.internal.context

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.context.ThreadAwareSpanContextStorage
import com.bugsnag.android.performance.internal.SpanContextStack

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class ThreadLocalSpanContextStorage : ThreadAwareSpanContextStorage {
    private val threadLocal =
        object : ThreadLocal<SpanContextStorage>() {
            override fun initialValue(): SpanContextStorage = SpanContextStack()
        }

    public var contextStack: SpanContextStorage
        get() = threadLocal.get() as SpanContextStorage
        set(value) {
            threadLocal.set(value)
        }

    override var currentThreadSpanContextStorage: SpanContextStorage?
        get() = contextStack
        set(value) {
            contextStack = value ?: SpanContextStack()
        }

    override val currentContext: SpanContext?
        get() = contextStack.currentContext

    override val currentStack: Sequence<SpanContext>
        get() = contextStack.currentStack

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
