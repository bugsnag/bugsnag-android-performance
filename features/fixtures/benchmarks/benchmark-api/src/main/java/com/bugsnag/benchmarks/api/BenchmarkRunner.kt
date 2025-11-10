package com.bugsnag.benchmarks.api

import android.util.Log
import kotlin.system.measureNanoTime

class BenchmarkRunner(
    val configFlags: Set<String> = emptySet(),
    val warmupIterations: Int = 1000,
    val iterationsPerRun: Int = 100_000,
    val numberOfRuns: Int = 5,
) {
    fun runBenchmark(benchmark: Benchmark): BenchmarkResults {
        Log.i("BenchmarkRunner", "Running benchmark: ${benchmark.name} with config flags: $configFlags")

        val cpuTimeCollector = CpuMetricsSampler(
            android.os.Process.myPid(),
            android.os.Process.myTid(),
        )

        runWarmup(benchmark)
        cleanup()

        val runResults = (1..numberOfRuns).map {
            runBenchmarkFor(benchmark, iterationsPerRun, cpuTimeCollector)
                .also { cleanup() }
        }

        return BenchmarkResults(
            benchmarkName = benchmark.name,
            runResults = runResults,
            configFlags = configFlags,
        )
    }

    private fun cleanup() {
        System.gc() // Suggest garbage collection after each run
        Thread.sleep(100L) // Allow time for GC to complete
    }

    internal fun runWarmup(benchmark: Benchmark) {
        runBenchmarkFor(benchmark, warmupIterations, null)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun runBenchmarkFor(
        benchmark: Benchmark,
        iterations: Int,
        cpuTimeCollector: CpuMetricsSampler?,
    ): RunResults {
        benchmark.reset()

        benchmark.remainingIterations = iterations
        val startStat = ProcStatReader.Stat()
        val endStat = ProcStatReader.Stat()

        cpuTimeCollector?.captureStat(startStat)
        val timeTaken = measureNanoTime {
            benchmark.run()
        }
        cpuTimeCollector?.captureStat(endStat)

        return RunResults(
            timeTaken = timeTaken,
            excludedTime = benchmark.excludedTime,
            iterations = iterations,
            cpuUse = cpuTimeCollector?.cpuUseBetween(startStat, endStat) ?: 0.0,
        )
    }

    override fun toString(): String {
        return "BenchmarkRunner(warmupIterations=$warmupIterations, iterationsPerRun=$iterationsPerRun, numberOfRuns=$numberOfRuns)"
    }
}
