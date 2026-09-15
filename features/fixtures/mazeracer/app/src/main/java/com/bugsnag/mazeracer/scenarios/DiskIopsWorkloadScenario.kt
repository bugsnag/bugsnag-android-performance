package com.bugsnag.mazeracer.scenarios

import android.database.sqlite.SQLiteDatabase
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.internal.InternalDebug
import com.bugsnag.mazeracer.Scenario
import java.io.File

/**
 * ROAD 2233 Scenario 11: high / burst / copy workloads on the real disk collector.
 * Exact ROAD table values stay unit-tested; Maze asserts valid integers and total = read + write.
 */
class DiskIopsWorkloadScenario(
    config: PerformanceConfiguration,
    scenarioMetadata: String,
) : Scenario(config, scenarioMetadata) {
    init {
        InternalDebug.spanBatchSizeSendTriggerPoint = 1
        InternalDebug.attachDiskIoSnapshots = true
        config.appSessionConfig.autoStartSession = false
        config.enabledMetrics.disk = true
    }

    override fun startScenario() {
        DiskIopsSupport.ensureProcIoReadable(context)
        BugsnagPerformance.start(config)
        forceConfigureMetrics(config.enabledMetrics)

        runAndFlush {
            val span =
                BugsnagPerformance.startSpan(
                    "DiskIopsWorkload",
                    DiskIopsSupport.diskSpanOptions(),
                )
            when (scenarioMetadata) {
                "burst_write" -> burstWriteThenIdle()
                "file_copy" -> copyFile()
                else -> runSqliteQueries()
            }
            span.end()
        }
    }

    private fun runSqliteQueries() {
        val dbFile = File(context.cacheDir, "disk_iops.db")
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS t (id INTEGER PRIMARY KEY, v TEXT)")
            db.beginTransaction()
            try {
                repeat(SQLITE_INSERT_COUNT) { index ->
                    db.execSQL("INSERT INTO t (v) VALUES (?)", arrayOf("row-$index"))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } finally {
            db.close()
        }
        DiskIopsSupport.generateDiskActivity(context, "sqlite")
    }

    private fun burstWriteThenIdle() {
        File(context.cacheDir, "burst.bin").writeBytes(ByteArray(BURST_BYTES) { 1 })
        Thread.sleep(IDLE_AFTER_BURST_MS)
        DiskIopsSupport.generateDiskActivity(context, "burst")
    }

    private fun copyFile() {
        val src = File(context.cacheDir, "copy_src.bin")
        val dst = File(context.cacheDir, "copy_dst.bin")
        src.writeBytes(ByteArray(COPY_BYTES) { it.toByte() })
        src.copyTo(dst, overwrite = true)
        DiskIopsSupport.generateDiskActivity(context, "copy")
    }

    private companion object {
        const val SQLITE_INSERT_COUNT = 1000
        const val BURST_BYTES = 2 * 1024 * 1024
        const val COPY_BYTES = 2 * 1024 * 1024
        const val IDLE_AFTER_BURST_MS = 1000L
    }
}
