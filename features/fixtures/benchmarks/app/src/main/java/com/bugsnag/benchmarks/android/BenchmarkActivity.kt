package com.bugsnag.benchmarks.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.bugsnag.android.performance.AutoInstrument
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.controls.NamedSpanControlsPlugin
import com.bugsnag.benchmarks.api.BenchmarkResults
import com.bugsnag.benchmarks.api.BenchmarkRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

const val BENCHMARK_PACKAGE_NAME = "com.bugsnag.benchmarks.suite"
const val PERFORMANCE_API_KEY = "a35a2a72bd230ac0aa0f52715bbdc6aa"

class BenchmarkActivity : Activity(), CoroutineScope by MainScope() {
    private val startTime = Date()
    private val resultsPrinter = ResultsTablePrinter {
        Log.i("BenchmarkResults", it)
    }

    private lateinit var mazeRunner: MazeRunnerClient
    private var benchmarkRunner: BenchmarkRunner = BenchmarkRunner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        mazeRunner = MazeRunnerClient(this)

        consumeAndProcessCommands()
    }

    private fun consumeAndProcessCommands() = launch {
        val commands = mazeRunner.run { commands() }
        commands.consumeEach { command ->
            when (command.action) {
                "run_scenario" -> {
                    val benchmarkName: String = requireNotNull(command["scenario_name"]) {
                        "Benchmark name ('scenario_name' attribute) is required"
                    }

                    val configFlags: String? = command["scenario_metadata"]
                    configFlags?.let { configureWithFlags(it) }

                    runBenchmark(benchmarkName)

                    // stop polling for commands
                    commands.cancel()
                }
            }
        }
    }

    private fun configureWithFlags(configFlags: String) {
        val config = configFlags.split(' ').toSet()
        configurePerformance(config)
        benchmarkRunner = BenchmarkRunner(configFlags = config)
    }

    private fun configurePerformance(config: Set<String>) {
        val configuration = PerformanceConfiguration.load(this, PERFORMANCE_API_KEY)
        configuration.endpoint = mazeRunner.getEndpoint("traces")

        configuration.enabledMetrics = EnabledMetrics(
            rendering = config.contains("rendering"),
            cpu = config.contains("cpu"),
            memory = config.contains("memory"),
        )

        if (config.contains("NamedSpan")) {
            configuration.addPlugin(NamedSpanControlsPlugin())
        }

        configuration.autoInstrumentAppStarts = false
        configuration.autoInstrumentActivities = AutoInstrument.OFF
        configuration.samplingProbability = 0.0 // discard all spans for benchmarking

        BugsnagPerformance.start(configuration)
    }

    private fun runBenchmark(benchmarkName: String) {
        launch(Dispatchers.Default) {
            delay(1000L) // Allow time for the UI to settle before running the benchmark
            val benchmarkClass = Class.forName("$BENCHMARK_PACKAGE_NAME.$benchmarkName")
            val benchmark = benchmarkClass.getDeclaredConstructor()
                .newInstance() as com.bugsnag.benchmarks.api.Benchmark

            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.benchmark_name).text = benchmark.name
            }

            val results = benchmarkRunner.runBenchmark(benchmark)
            reportBenchmarkResults(results)
        }
    }

    private suspend fun reportBenchmarkResults(results: BenchmarkResults) {
        val resultsMap = LinkedHashMap<String, String>()
        resultsMap["timestamp"] = startTime.toString()
        resultsMap["benchmark"] = results.benchmarkName

        results.configFlags.forEach { flag ->
            resultsMap[flag] = "true"
        }

        resultsMap["totalTimeTaken"] = results.timeTaken.toString()
        resultsMap["totalExcludedTime"] = results.excludedTime.toString()
        resultsMap["totalMeasuredTime"] = results.measuredTime.toString()
        resultsMap["totalIterations"] = results.iterations.toString()

        results.runResults.forEachIndexed { index, run ->
            val runNr = index + 1
            resultsMap["timeTaken.$runNr"] = run.timeTaken.toString()
            resultsMap["excludedTime.$runNr"] = run.excludedTime.toString()
            resultsMap["measuredTime.$runNr"] = run.measuredTime.toString()
            resultsMap["iterations.$runNr"] = run.iterations.toString()
            resultsMap["cpuUse.$runNr"] = run.cpuUse.toString()
        }

        mazeRunner.reportMetrics(resultsMap)
        resultsPrinter.print(results)
    }
}
