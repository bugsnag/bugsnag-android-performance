package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.internal.InternalDebug
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException

/**
 * Optimised reader & parser for `/proc/self/io` files.
 */
internal class ProcIoReader(private val customPath: String? = null) {
    private val path: String get() = customPath ?: InternalDebug.procIoPath

    /**
     * Reads and parses the io file into [target]. Returns a status string ("ok" on success).
     */
    @Synchronized
    fun parse(target: IoCounters): String {
        val currentPath = path
        return try {
            val file = File(currentPath)
            if (!file.exists()) return "file_not_found"

            val content = FileInputStream(file).bufferedReader().use { it.readText() }
            if (content.isBlank()) return "file_empty"

            var foundRead = false
            var foundWrite = false
            content.lineSequence().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("syscr")) {
                    extractFirstLong(trimmed)?.let {
                        target.readSyscalls = it
                        foundRead = true
                    }
                } else if (trimmed.startsWith("syscw")) {
                    extractFirstLong(trimmed)?.let {
                        target.writeSyscalls = it
                        foundWrite = true
                    }
                }
            }
            if (foundRead && foundWrite) "ok"
            else "fields_missing_r${foundRead}_w${foundWrite}"
        } catch (e: FileNotFoundException) {
            "file_not_found"
        } catch (e: Exception) {
            val msg = e.message ?: "unknown"
            if (msg.contains("Permission denied", ignoreCase = true)) "file_not_readable"
            else "exception_$msg"
        }
    }

    private fun extractFirstLong(line: String): Long? {
        return DIGIT_REGEX.find(line)?.value?.toLongOrNull()
    }

    private companion object {
        private val DIGIT_REGEX = Regex("\\d+")
    }

    internal data class IoCounters(
        var readSyscalls: Long = 0L,
        var writeSyscalls: Long = 0L,
    ) {
        constructor() : this(readSyscalls = 0L, writeSyscalls = 0L)
        val totalSyscalls: Long
            get() = readSyscalls + writeSyscalls
    }
}
