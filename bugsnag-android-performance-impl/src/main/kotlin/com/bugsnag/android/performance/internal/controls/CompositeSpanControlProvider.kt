package com.bugsnag.android.performance.internal.controls

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.internal.util.Prioritized
import com.bugsnag.android.performance.internal.util.PrioritizedSet

/**
 * A compositing [SpanControlProvider] that delegates to a list of other [SpanControlProvider]s.
 * This class is thread-safe and can be used to manage multiple span control providers in a
 * single instance.
 *
 * Each `SpanControlProvider` is wrapped as [Prioritized] to allow for ordering of the providers.
 * The `SpanControlProvider`s are sorted by priority, so providers with higher priority values
 * will be run *before* those with lower priorities. Priority values can be duplicated (i.e. two
 * `SpanControlProvider`s can have the same priority), in which case the order of
 * the providers is the order in which they were added.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class CompositeSpanControlProvider : SpanControlProvider<Any> {
    private val providers = PrioritizedSet<SpanControlProvider<*>>()

    public val size: Int
        get() = providers.size

    /**
     * Adds a [SpanControlProvider] to the list of providers if it is not already present. Any
     * existing `SpanControlProvider` will be ignored (as determined by [Object.equals]). The list
     * of providers is sorted by priority after the addition.
     *
     * @param provider The [SpanControlProvider] to add and its priority
     */
    public fun addProvider(provider: Prioritized<SpanControlProvider<*>>) {
        providers.add(provider)
    }

    /**
     * Adds a collection of [SpanControlProvider]s to the list of providers if they are not
     * already present. Any existing `SpanControlProvider` will be ignored (as determined by
     * [Object.equals]). The list of providers is sorted by priority after the addition.
     *
     * @param newProviders The collection of [SpanControlProvider]s to add and their priorities
     */
    public fun addProviders(newProviders: Collection<Prioritized<SpanControlProvider<*>>>) {
        providers.addAll(newProviders)
    }

    override operator fun <Q : SpanQuery<Any>> get(query: Q): Any? {
        providers.forEach { provider ->
            @Suppress("UNCHECKED_CAST")
            val anyProvider = provider as SpanControlProvider<Any>
            val result = anyProvider[query]
            if (result != null) {
                return result
            }
        }

        return null
    }
}
