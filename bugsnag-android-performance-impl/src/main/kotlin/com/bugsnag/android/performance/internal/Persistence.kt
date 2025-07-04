package com.bugsnag.android.performance.internal

import android.content.Context
import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.TraceFileDecoder.FILENAME_PREFIX
import com.bugsnag.android.performance.internal.TraceFileDecoder.FILENAME_SUFFIX
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class Persistence(public val context: Context) {
    public val topLevelDirectory: File = File(context.cacheDir, "bugsnag-performance/v1").apply { mkdirs() }

    public val retryQueue: RetryQueue = RetryQueue(File(topLevelDirectory, "retry-queue"))
    public val persistentState: PersistentState = PersistentState(File(topLevelDirectory, "persistent-state.json"))

    public fun clear() {
        // delete the contents of the directory, but not the directory itself
        topLevelDirectory.listFiles()?.forEach {
            it.deleteRecursively()
        }
    }
}

internal object TraceFileDecoder {
    private const val CR = '\r'.code // 13
    private const val LF = '\n'.code // 10

    internal const val FILENAME_PREFIX = "retry-"
    internal const val FILENAME_SUFFIX = ".json"

    fun decodeTracePayload(file: File): TracePayload {
        val timestamp =
            parseTimestamp(file.name)
                ?: throw IOException("not a valid trace payload filename: '${file.name}'")

        return file.inputStream().buffered().use { istream ->
            // parse the HTTP headers from the start of the file
            val headers = readHeaders(istream)

            // read the remaining bytes as the body of the file
            val body = istream.readBytes()
            TracePayload(timestamp, body, headers)
        }
    }

    private fun readHeaders(inputStream: InputStream): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        val buffer = ByteArrayOutputStream(1024)

        while (true) {
            if (inputStream.readUntilCRLF(buffer)) {
                if (buffer.size() != 0) {
                    parseHeaderLine(buffer.toString(), headers)
                } else {
                    // an empty line indicates the end of the headers
                    break
                }
            } else {
                // we hit an unexpected EOF before the headers were finished
                // that indicates that the file is invalid
                throw EOFException("unexpected EOF in headers")
            }

            // reuse the buffer without having to re-allocate it
            buffer.reset()
        }

        return headers
    }

    private fun parseHeaderLine(
        headerLine: String,
        output: MutableMap<String, String>,
    ) {
        val separator = headerLine.indexOf(':')
        if (separator == -1) {
            throw IOException("Not a valid HTTP header line: '$headerLine'")
        }

        val name = headerLine.substring(0, separator)
        val value = headerLine.substring(separator + 1).trimStart()

        output[name] = value
    }

    internal fun parseTimestamp(filename: String): Long? {
        if (filename.startsWith(FILENAME_PREFIX) && filename.endsWith(FILENAME_SUFFIX)) {
            return filename.substring(FILENAME_PREFIX.length).substringBefore('.').toLongOrNull()
        }

        return null
    }

    /**
     * Due to all the nice `readLine` implementations being on `Reader`s (which includes a
     * read-ahead) we need to implement our own strict CRLF based line reader which only
     * consumes a single byte at a time, leaving binary payloads intact.
     */
    private fun InputStream.readUntilCRLF(output: ByteArrayOutputStream): Boolean {
        while (true) {
            when (val b = read()) {
                CR -> {
                    // checkpoint in case the next byte != LF
                    mark(1)
                    val next = read()

                    if (next == LF) {
                        // end of line, return happy
                        return true
                    } else {
                        // oops this is not a strict CRLF we passthrough the CR and rewind
                        // to our checkpoint
                        output.write(CR)
                        reset()
                    }
                }
                -1 -> return false
                else -> output.write(b)
            }
        }
    }
}

/**
 * Implements the on-storage retry file storage. Slightly misnamed as the `RetryQueue` is actually
 * a stack (LIFO instead of FIFO) since more recently stored data is considered more important
 * than the older content (and older content may be discarded as being "stale").
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class RetryQueue(
    private val queueDirectory: File,
    private val maxPayloadAgeNanos: Long = TimeUnit.MILLISECONDS.toNanos(InternalDebug.dropSpansOlderThanMs),
) {
    private fun ensureQueueDirectory(): File {
        if (!queueDirectory.exists()) queueDirectory.mkdirs()
        return queueDirectory
    }

    private fun createRetryFile(timestamp: Long): File {
        // make sure the timestamp is the actual Unix timestamp so that it doesn't reset on reboot
        val unixNanoTimestamp = BugsnagClock.elapsedNanosToUnixTime(timestamp)
        return fileForNanoTimestamp(unixNanoTimestamp)
    }

    private fun fileForNanoTimestamp(timestamp: Long): File {
        val timestampString = timestamp.toString().padStart(19, '0')
        return File(ensureQueueDirectory(), "$FILENAME_PREFIX$timestampString$FILENAME_SUFFIX")
    }

    private fun writeHeaders(
        headers: Map<String, String>,
        out: OutputStream,
    ) {
        val headerBytes =
            headers.entries
                .joinToString(
                    separator = "\r\n",
                    postfix = "\r\n\r\n",
                    transform = { (key, value) -> "$key: $value" },
                )
                .toByteArray()

        out.write(headerBytes)
    }

    /**
     * Delete any payloads older than [maxPayloadAgeNanos]
     */
    public fun sweep() {
        val minExpectedTimestamp = BugsnagClock.currentUnixNanoTime() - maxPayloadAgeNanos
        queueDirectory.listFiles()
            ?.filter { (TraceFileDecoder.parseTimestamp(it.name) ?: 0) < minExpectedTimestamp }
            ?.forEach { it.deleteRecursively() }
    }

    public fun next(): TracePayload? {
        sweep()

        val nextFile = queueDirectory.listFiles()?.maxByOrNull { it.name } ?: return null

        return try {
            TraceFileDecoder.decodeTracePayload(nextFile)
        } catch (ioe: IOException) {
            Logger.w("Discarding trace file: $nextFile", ioe)
            nextFile.delete()
            null
        }
    }

    public fun remove(timestamp: Long) {
        fileForNanoTimestamp(timestamp).delete()
    }

    public fun add(tracePayload: TracePayload) {
        val (timestamp, trace, headers) = tracePayload
        createRetryFile(timestamp).outputStream().buffered().use { out ->
            writeHeaders(headers, out)
            out.write(trace)
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class PersistentState(
    private val stateFile: File,
) {
    public var pValue: Double = 1.0
    public var pValueExpiryTime: Long = 0

    init {
        load()
    }

    private fun load() {
        if (!stateFile.exists()) return

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            val jsonObject = JSONObject(stateFile.readText())
            this.pValue = jsonObject.optDouble(P_VALUE_KEY, pValue)
            this.pValueExpiryTime = jsonObject.optLong(P_VALUE_EXPIRY_KEY, pValueExpiryTime)
        } catch (ex: Exception) {
            // ignored, the stored values will be discarded and the values will be reset
        }
    }

    public fun save() {
        val jsonObject =
            JSONObject().apply {
                put(P_VALUE_KEY, pValue)
                put(P_VALUE_EXPIRY_KEY, pValueExpiryTime)
            }

        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            stateFile.writer().use { out ->
                out.write(jsonObject.toString())
            }
        } catch (ex: Exception) {
            // ignored, the stored values will be discarded and the values will be reset
        }
    }

    /**
     * Update this `PersistentState` and then attempt to [save] the changes. This should only
     * be invoked by [Task]s on the [Worker] thread.
     */
    public inline fun update(mutator: PersistentState.() -> Unit) {
        mutator.invoke(this)
        save()
    }

    internal companion object {
        internal const val P_VALUE_KEY = "pValue"
        internal const val P_VALUE_EXPIRY_KEY = "pValueExpiry"
    }
}
