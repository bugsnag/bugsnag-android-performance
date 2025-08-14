package com.bugsnag.benchmarks.suite

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.benchmarks.api.Benchmark

class SpanOpenCloseBenchmark : Benchmark() {
    private val noContext = SpanOptions
        .makeCurrentContext(false)
        .setFirstClass(false)

    override fun run() {
        measureRepeated {
            BugsnagPerformance.startSpan("SpanOpenCloseBenchmark", noContext).end()
        }
    }
}
