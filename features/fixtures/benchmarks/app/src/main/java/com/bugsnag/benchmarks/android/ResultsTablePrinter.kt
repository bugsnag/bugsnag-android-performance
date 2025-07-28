package com.bugsnag.benchmarks.android

import com.bugsnag.benchmarks.api.BenchmarkResults

class ResultsTablePrinter(
    private val println: (String) -> Unit,
) {
    fun print(results: BenchmarkResults) {
        val table = mutableListOf(
            arrayOf(
                "Benchmark",
                "Total Duration (ns)",
                "Excluded Time (ns)",
                "Iterations",
                "Measured Time (ns)",
                "Duration per Iteration (ns)",
                "CPU Usage (%)",
            ),

            arrayOf(
                results.benchmarkName,
                results.timeTaken.toString(),
                results.excludedTime.toString(),
                results.iterations.toString(),
                results.measuredTime.toString(),
                "%.2f".format(results.averageTimePerIteration),
                "",
            ),
        )

        results.runResults.forEachIndexed { index, run ->
            val runNr = index + 1
            table.add(
                arrayOf(
                    "Run $runNr",
                    run.timeTaken.toString(),
                    run.excludedTime.toString(),
                    run.iterations.toString(),
                    run.measuredTime.toString(),
                    (run.measuredTime / run.iterations).toString(),
                    "%.2f".format(run.cpuUse),
                ),
            )
        }

        printTable(table)
    }

    private fun printTable(rows: List<Array<String>>) {
        val columnWidths = calculateColumnWidths(rows)
        // allow for spaces on either side of every cell, and a separator between columns
        val totalWidth = columnWidths.sum() + ((columnWidths.size + 2) * 3)

        println(" " + "-".repeat(totalWidth))

        for (row in rows) {
            val formattedRow =
                row.mapIndexed { index, cell -> cell.padEnd(columnWidths[index] + 1) }
                    .joinToString(" | ", prefix = "| ", postfix = " |")
            println(formattedRow)
        }

        println(" " + "-".repeat(totalWidth))
    }

    private fun calculateColumnWidths(rows: List<Array<String>>): IntArray {
        val columnCount = rows.maxOf { it.size }
        val widths = IntArray(columnCount)

        for (row in rows) {
            for (i in row.indices) {
                widths[i] = maxOf(widths[i], row[i].length)
            }
        }
        return widths
    }
}
