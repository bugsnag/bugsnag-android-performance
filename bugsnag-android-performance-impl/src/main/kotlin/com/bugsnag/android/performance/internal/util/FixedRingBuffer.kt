package com.bugsnag.android.performance.internal.util

import kotlin.math.max
import kotlin.math.min

/**
 * A simple pre-allocated ring buffer designed to hold sample data for various metrics. All of the
 * contents are pre-allocated on init, and are updated by [put]. As such we assume the contents
 * is mutable.
 *
 * This structure is not thread-safe, external synchronization must be used when required.
 */
public class FixedRingBuffer<T>(
    @PublishedApi
    internal val values: Array<T>,
) {
    private var tail = 0

    /**
     * The number of items currently "added" to this [FixedRingBuffer], and the maximum number of
     * items that can be returned by [forEach].
     */
    public val size: Int get() = min(values.size, tail)

    /**
     * The current "pointer" or "tail" for this [FixedRingBuffer], this should be treated as an
     * opaque value and only be used as arguments to the [forEach] function.
     */
    public val currentIndex: Int
        get() = tail

    /**
     * Returns the "next" (tail) item in the buffer, this function can be used instead of [put]
     * if more complex update logic is required.
     */
    public fun next(): T {
        val index = tail % values.size
        tail++
        return values[index]
    }

    /**
     * "add" an item to this [FixedRingBuffer]
     */
    public inline fun put(update: (T) -> Unit) {
        update(next())
    }

    public fun countItemsBetween(
        from: Int,
        to: Int,
    ): Int {
        val realMin = tail - values.size

        // if the "to" value is no longer in the buffer, we have no data that can be delivered
        if (to < realMin) {
            return 0
        }

        // calculate the actual number of samples that can be iterated
        return min(to - max(from, realMin), size)
    }

    /**
     * Loop through all of the items between `from` (inclusive) and `to` (exclusive) if all of the
     * available items are still in the buffer. If `to - from` is larger than the buffer the
     * entire buffer will be iterated over resulting in only the most recent values being passed
     * to [consumer].
     */
    public inline fun forEach(
        from: Int,
        to: Int,
        consumer: (T) -> Unit,
    ) {
        // calculate the actual number of samples that can be iterated
        val count = countItemsBetween(from, to)
        val start = to - count

        for (i in 0 until count) {
            val index = (start + i) % values.size
            consumer(values[index])
        }
    }

    public inline fun forEachIndexed(
        from: Int,
        to: Int,
        consumer: (index: Int, T) -> Unit,
    ) {
        // calculate the actual number of samples that can be iterated
        val count = countItemsBetween(from, to)
        val start = to - count

        for (i in 0 until count) {
            val index = (start + i) % values.size
            consumer(i, values[index])
        }
    }
}

@Suppress("FunctionNaming")
public inline fun <reified T> FixedRingBuffer(
    size: Int,
    init: (index: Int) -> T,
): FixedRingBuffer<T> {
    return FixedRingBuffer(Array(size) { index -> init(index) })
}
