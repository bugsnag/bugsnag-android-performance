package com.bugsnag.benchmarks.api

interface IteratorResult {
    val timeTaken: Long
    val excludedTime: Long
    val iterations: Int

    val measuredTime: Long
        get() = timeTaken - excludedTime

    val averageTimePerIteration: Double
        get() = if (iterations > 0) measuredTime.toDouble() / iterations else 0.0
}

data class BenchmarkResults(
    val benchmarkName: String,
    val runResults: List<RunResults>,
    val configFlags: Set<String>,
) : IteratorResult {
    override val timeTaken: Long
        get() = runResults.sumOf { it.timeTaken }

    override val excludedTime: Long
        get() = runResults.sumOf { it.excludedTime }

    override val iterations: Int
        get() = runResults.sumOf { it.iterations }
}

data class RunResults(
    override val timeTaken: Long,
    override val excludedTime: Long,
    override val iterations: Int,
    val cpuUse: Double,
) : IteratorResult
