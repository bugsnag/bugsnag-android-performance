package com.bugsnag.android.performance.internal.controls

import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.controls.SpanQuery

import com.bugsnag.android.performance.internal.util.Prioritized

import java.util.concurrent.locks.ReentrantLock

import kotlin.concurrent.withLock

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
internal class CompositeSpanControlProvider : SpanControlProvider<Any> {
    @Volatile
    private var providers: Array<Prioritized<SpanControlProvider<Any>>> = emptyArray()

    private val lock = ReentrantLock()

    val size: Int
        get() = providers.size

    /**
     * Adds a [SpanControlProvider] to the list of providers if it is not already present. Any
     * existing `SpanControlProvider` will be ignored (as determined by [Object.equals]). The list
     * of providers is sorted by priority after the addition.
     *
     * @param provider The [SpanControlProvider] to add and its priority
     */
    fun addProvider(provider: Prioritized<SpanControlProvider<*>>) {
        lock.withLock {
            @Suppress("UNCHECKED_CAST")
            val castProvider = provider as Prioritized<SpanControlProvider<Any>>
            if (providers.any { it.value == castProvider.value }) {
                return
            }

            val newProviders = providers + castProvider
            newProviders.sort()
            providers = newProviders
        }
    }

    /**
     * Adds a collection of [SpanControlProvider]s to the list of providers if they are not
     * already present. Any existing `SpanControlProvider` will be ignored (as determined by
     * [Object.equals]). The list of providers is sorted by priority after the addition.
     *
     * @param newProviders The collection of [SpanControlProvider]s to add and their priorities
     */
    fun addProviders(newProviders: Collection<Prioritized<SpanControlProvider<*>>>) {
        lock.withLock {
            val newProviderArray = providers.copyOf(providers.size + newProviders.size)
            var index = providers.size

            for (provider in newProviders) {
                @Suppress("UNCHECKED_CAST")
                val castProvider = provider as Prioritized<SpanControlProvider<Any>>
                if (providers.none { it.value == castProvider.value }) {
                    newProviderArray[index++] = castProvider
                }
            }

            if (index == newProviderArray.size) {
                newProviderArray.sort()
                @Suppress("UNCHECKED_CAST")
                providers = newProviderArray as Array<Prioritized<SpanControlProvider<Any>>>
            } else {
                newProviderArray.sort(0, index)
                @Suppress("UNCHECKED_CAST")
                providers = newProviderArray.copyOf(index)
                        as Array<Prioritized<SpanControlProvider<Any>>>
            }
        }
    }

    override operator fun <Q : SpanQuery<Any>> get(query: Q): Any? {
        val providerList = providers

        for (provider in providerList) {
            val result = provider.value[query]
            if (result != null) {
                return result
            }
        }

        return null
    }
}
