package com.bugsnag.android.performance.internal.metrics

import com.bugsnag.android.performance.internal.InternalDebug
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

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
        return try {
            parseFile(File(path), target)
        } catch (_: FileNotFoundException) {
            "file_not_found"
        } catch (e: IOException) {
            mapIoException(e)
        } catch (_: SecurityException) {
            "file_not_readable"
        }
    }

    private fun mapIoException(e: IOException): String {
        val msg = e.message ?: "unknown"
        return if (msg.contains("Permission denied", ignoreCase = true)) {
            "file_not_readable"
        } else {
            "exception_$msg"
        }
    }

    private fun parseFile(
        file: File,
        target: IoCounters,
    ): String {
        return when {
            !file.exists() -> "file_not_found"

            else -> {
                val content = FileInputStream(file).bufferedReader().use { it.readText() }
                if (content.isBlank()) {
                    "file_empty"
                } else {
                    val result = parseContent(content, target)
                    if (result.foundRead && result.foundWrite) {
                        "ok"
                    } else {
                        "fields_missing_r${result.foundRead}_w${result.foundWrite}"
                    }
                }
            }
        }
    }

    private fun parseContent(
        content: String,
        target: IoCounters,
    ): ParseResult {
        var foundRead = false
        var foundWrite = false

        content.lineSequence().forEach { line ->
            when (val parsed = parseLine(line.trim())) {
                is ParsedLine.Read -> {
                    target.readSyscalls = parsed.value
                    foundRead = true
                }
                is ParsedLine.Write -> {
                    target.writeSyscalls = parsed.value
                    foundWrite = true
                }
                ParsedLine.Ignored -> Unit
            }
        }

        return ParseResult(foundRead, foundWrite)
    }

    private fun parseLine(line: String): ParsedLine {
        return when {
            line.startsWith("syscr") -> extractFirstLong(line)?.let { ParsedLine.Read(it) }
            line.startsWith("syscw") -> extractFirstLong(line)?.let { ParsedLine.Write(it) }
            else -> null
        } ?: ParsedLine.Ignored
    }

    private fun extractFirstLong(line: String): Long? {
        return DIGIT_REGEX.find(line)?.value?.toLongOrNull()
    }

    private data class ParseResult(
        val foundRead: Boolean,
        val foundWrite: Boolean,
    )

    private sealed interface ParsedLine {
        data class Read(val value: Long) : ParsedLine

        data class Write(val value: Long) : ParsedLine

        object Ignored : ParsedLine
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
