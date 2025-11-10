package com.bugsnag.android.performance.internal.context

import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.SpanContextStorage
import com.bugsnag.android.performance.internal.SpanContextStack
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

public interface ThreadAwareSpanContextStorage : SpanContextStorage {
    public var localContextStack: SpanContextStack?
}

/**
 * `ThreadFactory` wrapper for threads that are intended as background workers. In cases where
 * the [SpanContextStorage] is [ThreadAwareSpanContextStorage] this thread factory will attempt
 * to ensure that the created threads all have a `localContextStack` set before performing any
 * work.
 */
public class ContextAwareThreadFactory(private val delegate: ThreadFactory) : ThreadFactory {
    override fun newThread(r: Runnable): Thread {
        return delegate.newThread {
            try {
                val store = SpanContext.defaultStorage as? ThreadAwareSpanContextStorage
                if (store != null) {
                    if (store.localContextStack == null) {
                        store.localContextStack = SpanContextStack()
                    }
                }
            } finally {
                r.run()
            }
        }
    }

    public companion object {
        public val defaultContextAwareThreadFactory: ThreadFactory =
            ContextAwareThreadFactory(Executors.defaultThreadFactory())

        public fun wrap(delegate: ThreadFactory?): ThreadFactory {
            if (delegate == null) {
                return defaultContextAwareThreadFactory
            }

            return ContextAwareThreadFactory(delegate)
        }
    }
}
