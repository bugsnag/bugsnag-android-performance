package com.bugsnag.android.performance.context

import com.bugsnag.android.performance.SpanContextStorage

/**
 * An extension of [SpanContextStorage] for implementations that can differentiate between
 * execution threads.
 *
 * This interface provides a contract for storage mechanisms that are "thread-aware". It does not
 * mandate that all operations are isolated per-thread, but rather for interacting with what might
 * be a thread-specific storage instance.
 *
 * Implementations can use this to provide advanced capabilities, such as allowing manual
 * propagation of a `SpanContext` stack from one thread to another.
 *
 * @see SpanContextStorage
 */
public interface ThreadAwareSpanContextStorage : SpanContextStorage {
    /**
     * Represents the [SpanContextStorage] for the currently executing thread, providing a
     * mechanism to retrieve or replace it.
     *
     * This property is intended for advanced use cases, such as manual context propagation
     * across asynchronous boundaries. For example, a caller could get the storage from a
     * parent thread and set it on a newly created child thread to ensure the span
     * hierarchy is correctly maintained.
     *
     * **Getter**: Retrieves a representation of the `SpanContextStorage` for the current thread.
     * This may be the actual storage instance, a proxy, or `null` if no context is
     * associated with the thread.
     *
     * **Setter**: Attempts to replace the `SpanContextStorage` for the current thread.
     * This should be used with caution, as it can lead to an inconsistent state if not
     * managed carefully.
     */
    public var currentThreadSpanContextStorage: SpanContextStorage?
}
