package com.bugsnag.benchmarks.api

import androidx.annotation.CallSuper

/**
 * Base class for benchmarks.
 */
abstract class Benchmark : Runnable {
    @JvmField
    var remainingIterations: Int = 0

    @JvmField
    var excludedTime: Long = 0L

    open val name: String
        get() = this::class.java.simpleName

    @CallSuper
    open fun reset() {
        remainingIterations = 0
        excludedTime = 0L
    }

    protected inline fun measureRepeated(block: () -> Unit) {
        while (remainingIterations > 0) {
            block()
            remainingIterations--
        }
    }

    protected inline fun <T> runWithTimingDisabled(block: () -> T): T {
        val startTime = System.nanoTime()
        try {
            return block()
        } finally {
            val endTime = System.nanoTime()
            excludedTime += (endTime - startTime)
        }
    }
}
