package com.bugsnag.android.performance.internal.util

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A simple data class that wraps a value with a priority. This can be used to sort lists of
 * non-comparable objects, where it may be desirable for priorities to be duplicated (ie. two
 * objects can have the same priority).
 *
 * Priorities are sorted in descending order, so higher priority values will be sorted first. For
 * example, a priority of 10 will appear before a priority of 5 when using a natural sort.
 */
internal data class Prioritized<T>(val priority: Int, val value: T) : Comparable<Prioritized<T>> {
    override fun compareTo(other: Prioritized<T>): Int {
        return other.priority - priority
    }
}

/**
 * A simple thread-safe set of [Prioritized] values. This is a mutable collection that allows
 * adding values which may have duplicate priorities, but may *not* have duplicate values.
 * Attempting to add a value that already exists in the set will be ignored.
 *
 * This class does not implement [MutableSet] as it does not truly match the contract, specifically
 * equality is based on the values of the [Prioritized] elements while not considering their
 * priorities. A true set implementation would consider {priority, value} when taking equality into
 * account.
 */
internal class PrioritizedSet<T> {
    @Volatile
    @PublishedApi
    internal var values: Array<Prioritized<T>> = emptyArray()
        private set

    private val lock = ReentrantLock()

    val size: Int
        get() = values.size

    fun add(element: Prioritized<T>): Boolean {
        lock.withLock {
            if (values.any { it.value == element.value }) {
                return false
            }

            val newProviders = values + element
            newProviders.sort()
            values = newProviders
        }

        return true
    }

    fun addAll(elements: Collection<Prioritized<T>>): Boolean {
        lock.withLock {
            val newValuesArray = values.copyOf(values.size + elements.size)
            var index = values.size

            for (prioritized in elements) {
                if (values.none { it.value == prioritized.value }) {
                    newValuesArray[index++] = prioritized
                }
            }

            if (index == values.size) {
                // no elements were added
                return false
            } else if (index == newValuesArray.size) {
                newValuesArray.sort()
                @Suppress("UNCHECKED_CAST")
                values = newValuesArray as Array<Prioritized<T>>
            } else {
                newValuesArray.sort(0, index)
                @Suppress("UNCHECKED_CAST")
                values = newValuesArray.copyOf(index) as Array<Prioritized<T>>
            }
        }

        return true
    }

    inline fun forEach(action: (T) -> Unit) {
        for (prioritized in values) {
            action(prioritized.value)
        }
    }
}
