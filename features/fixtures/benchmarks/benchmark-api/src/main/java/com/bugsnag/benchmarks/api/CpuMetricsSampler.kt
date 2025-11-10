package com.bugsnag.benchmarks.api

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RestrictTo
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

internal class CpuMetricsSampler(statFile: String) {
    constructor(pid: Int) : this("/proc/$pid/stat")
    constructor(pid: Int, tid: Int) : this("/proc/$pid/task/$tid/stat")

    private val statReader = ProcStatReader(statFile)

    fun captureStat(output: ProcStatReader.Stat): Boolean {
        return statReader.parse(output)
    }

    fun cpuUseBetween(s1: ProcStatReader.Stat, s2: ProcStatReader.Stat): Double {
        val uptimeSec = s2.timestamp / 1000.0
        val totalCpuTime = s2.totalCpuTime / SystemConfig.clockTickHz

        val previousCpuTime = s1.totalCpuTime / SystemConfig.clockTickHz
        val previousUptime = s1.timestamp / 1000.0

        // calculate the avg cpu % between the previous sample and now
        val deltaCpuTime = totalCpuTime - previousCpuTime
        val deltaUptime = uptimeSec - previousUptime

        val cpuUsagePercent = 100.0 * (deltaCpuTime / deltaUptime)
        val normalisedPct = cpuUsagePercent / SystemConfig.numCores
        return if (normalisedPct.isFinite()) normalisedPct else 0.0
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object SystemConfig {
    private var _clockTickHz: Double = Os.sysconf(OsConstants._SC_CLK_TCK).toDouble()

    /**
     * The number of scheduler slices per second. Contrary to SC_CLK_TCK naming, this is not the
     * CPU clock speed (https://www.softprayog.in/tutorials/linux-process-execution-time).
     */
    val clockTickHz: Double get() = _clockTickHz

    val numCores: Int = numProcessorCores()

    private fun numProcessorCores(): Int {
        val cores = Os.sysconf(OsConstants._SC_NPROCESSORS_CONF).toInt()
        return max(cores, 1)
    }
}


/**
 * Optimised reader & parser for `/proc/{pid}/stat` files. These objects are well suited be reuse
 * when a single stat file must be read for continuous sampling.
 *
 * This class is *not* thread safe, and appropriate synchronisation must be used when required.
 */
internal class ProcStatReader(
    /**
     * The full path to the `stat` file to be read.
     */
    private val path: String,
) {
    private val buffer =
        ByteBuffer
            .allocateDirect(BUFFER_SIZE)
            .apply { order(ByteOrder.BIG_ENDIAN) }

    private fun skipUntil(ch: Char): Boolean {
        val code = ch.code

        while (buffer.hasRemaining()) {
            val b = buffer.nextByte()
            if (b == code) {
                return true
            }
        }

        return false
    }

    private fun parseLong(): Long {
        var result = 0L

        // check for '-' without moving the buffer cursor (most of the values cannot be negative)
        val isNegative = (buffer.get(buffer.position()).intValue) == '-'.code

        if (isNegative) {
            // skip the '-' character
            buffer.get()
        }

        while (buffer.hasRemaining()) {
            val b = buffer.nextByte()

            if (b == ' '.code) {
                break
            } else if (b in ZERO_CODE..NINE_CODE) {
                val number = (b - ZERO_CODE).toLong()
                result *= 10L
                result += number
            }
        }

        if (isNegative) {
            result = -result
        }

        return result
    }

    fun parse(target: Stat): Boolean {
        try {
            buffer.rewind()
            FileInputStream(path).channel.use { channel ->
                channel.read(buffer)
            }

            buffer.rewind()

            // skip until the end of the process name
            skipUntil(')')
            buffer.get() // skip the space

            target.state = (buffer.nextByte()).toChar()
            buffer.get() // skip the space

            repeat(FIELDS_TO_SKIP) {
                skipUntil(' ')
            }

            target.utime = parseLong()
            target.stime = parseLong()
            target.cutime = parseLong()
            target.cstime = parseLong()
            target.counter = parseLong().toInt()
            target.priority = parseLong().toInt()
            target.timeout = parseLong()
            target.itrealvalue = parseLong()
            target.starttime = parseLong()
            target.vsize = parseLong()
            target.rss = parseLong()
            target.timestamp = SystemClock.elapsedRealtime()
        } catch (ioe: IOException) {
            // suppress
            return false
        }

        return true
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun ByteBuffer.nextByte(): Int {
        return get().intValue
    }

    private inline val Byte.intValue: Int
        get() = toInt() and BYTE_MASK

    companion object {
        internal const val BUFFER_SIZE = 256
        internal const val ZERO_CODE = '0'.code
        internal const val NINE_CODE = '9'.code

        internal const val BYTE_MASK = 0xff

        /**
         * The number of fields to skip between the status & utime
         */
        internal const val FIELDS_TO_SKIP = 10
    }

    data class Stat(
        var state: Char,
        var utime: Long,
        var stime: Long,
        var cutime: Long,
        var cstime: Long,
        var counter: Int,
        var priority: Int,
        var timeout: Long,
        var itrealvalue: Long,
        var starttime: Long,
        var vsize: Long,
        var rss: Long,
        var timestamp: Long,
    ) {
        constructor() : this(
            state = ' ',
            utime = 0L,
            stime = 0L,
            cutime = 0L,
            cstime = 0L,
            counter = 0,
            priority = 0,
            timeout = 0L,
            itrealvalue = 0L,
            starttime = 0L,
            vsize = 0L,
            rss = 0L,
            timestamp = 0L
        )

        val totalCpuTime: Double
            get() = (utime + stime + cutime + cstime).toDouble()
    }
}

