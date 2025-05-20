package com.bugsnag.android.performance.internal.util

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
