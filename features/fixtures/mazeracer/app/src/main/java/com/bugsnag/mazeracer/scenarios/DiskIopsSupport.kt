package com.bugsnag.mazeracer.scenarios

import android.content.Context
import com.bugsnag.android.performance.SpanMetrics
import com.bugsnag.android.performance.SpanOptions
import com.bugsnag.android.performance.internal.InternalDebug
import java.io.File

/**
 * Shared Maze helpers for ROAD 2233 Disk IOPS scenarios that run against real /proc/self/io.
 */
internal object DiskIopsSupport {
    const val SAMPLER_TIMEOUT_MS = 2000L
    const val SPAN_SLEEP_MS = 200L
    const val FAKE_IO_RANGE = 50_000L
    const val FAKE_IO_BASE = 200L
    const val FAKE_IO_WRITE_DIVISOR = 2L

    fun ensureProcIoReadable(context: Context) {
        val realIo = File("/proc/self/io")
        if (!realIo.exists() || !realIo.canRead()) {
            val fakeIo = File(context.cacheDir, "fake_io")
            fakeIo.writeText("syscr: 100\nsyscw: 50\n")
            InternalDebug.procIoPath = fakeIo.absolutePath
        }
    }

    fun generateDiskActivity(
        context: Context,
        label: String,
    ) {
        if (InternalDebug.procIoPath == "/proc/self/io") {
            File(context.cacheDir, "$label.txt").writeText("data-$label-${System.nanoTime()}")
        } else {
            val seq = (System.nanoTime() % FAKE_IO_RANGE) + FAKE_IO_BASE
            File(InternalDebug.procIoPath).writeText("syscr: $seq\nsyscw: ${seq / FAKE_IO_WRITE_DIVISOR}\n")
        }
    }

    fun diskSpanOptions(
        disk: Boolean = true,
        cpu: Boolean? = null,
        memory: Boolean? = null,
    ): SpanOptions {
        return SpanOptions.setFirstClass(true).withMetrics(
            SpanMetrics(disk = disk, cpu = cpu, memory = memory),
        )
    }
}
