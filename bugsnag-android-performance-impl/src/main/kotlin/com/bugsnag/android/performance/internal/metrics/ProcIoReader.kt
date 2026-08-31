package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.Logger
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Optimised reader & parser for `/proc/self/io` files. These objects are well suited to be reused
 * when the io file must be read for continuous sampling.
 *
 * The `/proc/self/io` file format:
 * ```
 * rchar: <bytes_read_by_read_syscalls>
 * wchar: <bytes_written_by_write_syscalls>
 * syscr: <read_syscall_count>
 * syscw: <write_syscall_count>
 * read_bytes: <bytes_actually_read_from_storage>
 * write_bytes: <bytes_actually_written_to_storage>
 * cancelled_write_bytes: <bytes_not_written_due_to_truncation>
 * ```
 *
 * This class is *not* thread safe, and appropriate synchronisation must be used when required.
 */
internal class ProcIoReader(
    /**
     * The full path to the `io` file to be read. Defaults to `/proc/self/io`.
     */
    private val path: String = "/proc/self/io",
) {
    private val buffer =
        ByteBuffer
            .allocateDirect(BUFFER_SIZE)
            .apply { order(ByteOrder.BIG_ENDIAN) }

    /**
     * Reads and parses the io file into [target]. Returns `true` on success, `false` on failure.
     */
    fun parse(target: IoCounters): Boolean {
        return try {
            readIoFile()
            parseBuffer(target)
        } catch (e: IOException) {
            Logger.w("ProcIoReader: failed to read io file", e)
            false
        }
    }

    private fun readIoFile() {
        buffer.clear()
        FileInputStream(path).channel.use { channel ->
            val bytesRead = channel.read(buffer)
            if (bytesRead <= 0) {
                buffer.limit(0)
            } else {
                buffer.flip()
            }
        }
    }

    @Suppress("ReturnCount")
    private fun parseBuffer(target: IoCounters): Boolean {
        if (!buffer.hasRemaining()) return false

        // Reset counters before parsing
        target.readSyscalls = 0L
        target.writeSyscalls = 0L

        // Parse each line looking for syscr and syscw fields
        var fieldsFound = 0
        while (buffer.hasRemaining() && fieldsFound < 2) {
            when {
                matchesPrefix(SYSCR_PREFIX) -> {
                    target.readSyscalls = parseLong() ?: return false
                    fieldsFound++
                }

                matchesPrefix(SYSCW_PREFIX) -> {
                    target.writeSyscalls = parseLong() ?: return false
                    fieldsFound++
                }

                else -> skipLine()
            }
        }

        return fieldsFound == 2
    }

    /**
     * Checks if the buffer at the current position starts with [prefix].
     * If it does, advances the buffer past the prefix and returns true.
     * Otherwise leaves the buffer position unchanged.
     */
    private fun matchesPrefix(prefix: ByteArray): Boolean {
        val startPos = buffer.position()
        if (buffer.remaining() < prefix.size) return false

        for (byte in prefix) {
            if (buffer.nextByte() != byte.intValue) {
                buffer.position(startPos)
                return false
            }
        }
        return true
    }

    /**
     * Parses an unsigned decimal integer. Returns null if the value is missing or contains
     * non-numeric characters (treated as an unreadable counter source per ED §3.1.4).
     */
    private fun parseLong(): Long? {
        var result = 0L
        var seenDigit = false

        // skip any whitespace or ':' after the prefix key
        while (buffer.hasRemaining()) {
            val b = buffer.get(buffer.position()).intValue
            if (b == ' '.code || b == ':'.code || b == '\t'.code) {
                buffer.get()
            } else {
                break
            }
        }

        while (buffer.hasRemaining()) {
            val b = buffer.nextByte()
            if (b == '\n'.code || b == ' '.code) break
            if (b in ZERO_CODE..NINE_CODE) {
                seenDigit = true
                result = result * 10L + (b - ZERO_CODE).toLong()
            } else {
                return null
            }
        }

        return if (seenDigit) result else null
    }

    /** Skips bytes until end-of-line (inclusive). */
    private fun skipLine() {
        while (buffer.hasRemaining()) {
            if (buffer.nextByte() == '\n'.code) break
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun ByteBuffer.nextByte(): Int = get().intValue

    private inline val Byte.intValue: Int
        get() = toInt() and BYTE_MASK

    companion object {
        internal const val BUFFER_SIZE = 256
        internal const val ZERO_CODE = '0'.code
        internal const val NINE_CODE = '9'.code
        internal const val BYTE_MASK = 0xff

        /** `syscr: ` prefix bytes */
        private val SYSCR_PREFIX = "syscr:".toByteArray(Charsets.US_ASCII)

        /** `syscw: ` prefix bytes */
        private val SYSCW_PREFIX = "syscw:".toByteArray(Charsets.US_ASCII)
    }

    /**
     * Holds parsed I/O counter values from `/proc/self/io`.
     *
     * @property readSyscalls  Total number of read-family syscalls (syscr).
     * @property writeSyscalls Total number of write-family syscalls (syscw).
     */
    internal data class IoCounters(
        var readSyscalls: Long = 0L,
        var writeSyscalls: Long = 0L,
    ) {
        /**
         * No-arg constructor returning zeroed counters, mirroring [ProcStatReader.Stat].
         */
        constructor() : this(readSyscalls = 0L, writeSyscalls = 0L)

        /** Combined total syscall count (read + write). */
        val totalSyscalls: Long
            get() = readSyscalls + writeSyscalls
    }
}
