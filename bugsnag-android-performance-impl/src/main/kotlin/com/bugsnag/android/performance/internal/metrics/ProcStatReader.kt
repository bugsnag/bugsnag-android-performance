package com.bugsnag.android.performance.internal.metrics

import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

    internal data class Stat(
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
        )

        val totalCpuTime: Double
            get() = (utime + stime + cutime + cstime).toDouble()
    }
}
