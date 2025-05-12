package com.bugsnag.android.performance.internal.processing

import android.util.JsonWriter
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.internal.Attributes
import com.bugsnag.android.performance.internal.SpanImpl
import java.io.Closeable
import java.io.Writer
import kotlin.math.min

internal class JsonTraceWriter(
    private val json: JsonWriter,
    private val attributeLimits: AttributeLimits? = null,
) : Closeable by json {
    /**
     * We can only encode a single Span at a time - so we keep track of it here.
     */
    internal var currentSpan: SpanImpl? = null

    constructor(out: Writer, attributeLimits: AttributeLimits? = null) :
            this(JsonWriter(out), attributeLimits)

    inline fun writeSpan(
        spanImpl: SpanImpl,
        scopedWrite: JsonTraceWriter.() -> Unit,
    ) {
        currentSpan = spanImpl
        obj {
            scopedWrite()
        }
        currentSpan = null
    }

    fun beginObject() = json.beginObject()

    fun endObject() = json.endObject()

    fun beginArray() = json.beginArray()

    fun endArray() = json.endArray()

    fun name(name: String): JsonTraceWriter {
        json.name(name)
        return this
    }

    fun value(value: String): JsonTraceWriter {
        json.value(value)
        return this
    }

    fun value(value: Int): JsonTraceWriter {
        json.value(value)
        return this
    }

    fun value(value: Long): JsonTraceWriter {
        json.value(value)
        return this
    }

    fun value(value: Boolean): JsonTraceWriter {
        json.value(value)
        return this
    }

    fun value(value: Double): JsonTraceWriter {
        if (!value.isFinite()) {
            json.nullValue()
            return this
        }

        json.value(value)
        return this
    }

    fun value(value: Number): JsonTraceWriter {
        if (value is Double || value is Float) {
            value(value.toDouble())
            return this
        }

        json.value(value)
        return this
    }

    fun nullValue(): JsonTraceWriter {
        json.nullValue()
        return this
    }

    inline fun obj(builder: JsonTraceWriter.() -> Unit): JsonTraceWriter {
        this.beginObject()
        this.builder()
        this.endObject()

        return this
    }

    inline fun array(builder: JsonTraceWriter.() -> Unit): JsonTraceWriter {
        this.beginArray()
        this.builder()
        this.endArray()

        return this
    }

    fun value(attributes: Attributes): JsonTraceWriter {
        return array {
            attributes.entries.forEach { (key, value) ->
                if (isValidAttributeKey(key)) {
                    obj {
                        name("key").value(key)

                        name("value")
                        writeAttributeValue(key, value)
                    }
                } else {
                    Logger.w(
                        "Span attribute $key in span ${currentSpan?.name} was dropped as " +
                                "the key exceeds the $MAX_KEY_LENGTH character fixed limit.",
                    )
                    currentSpan?.apply { droppedAttributesCount++ }
                }
            }
        }
    }

    private fun writeAttributeValue(
        key: String,
        value: Any?,
    ) {
        obj {
            when (value) {
                is String -> json.name("stringValue").value(limitedStringValue(value))
                is Float -> json.name("doubleValue").value(value.toDouble())
                is Double -> json.name("doubleValue").value(value)
                is Boolean -> json.name("boolValue").value(value)
                // int64 is JSON encoded as a String
                is Long, is Int, is Short, is Byte -> {
                    json.name("intValue").value(value.toString())
                }

                is Collection<*> -> writeArray(key, value.size, value.iterator())
                is Array<*> -> writeArray(key, value.size, value.iterator())
                is IntArray -> writeIntArray(key, value)
                is LongArray -> writeLongArray(key, value)
                is DoubleArray -> writeDoubleArray(key, value)
                else -> Logger.w("Unexpected value in attribute '$key' attribute of span '${currentSpan?.name}'")
            }
        }
    }

    private fun writeArray(
        attributeName: String,
        arraySize: Int,
        iterator: Iterator<Any?>,
    ) {
        name("arrayValue").obj {
            name("values").array {
                val itemCount =
                    if (attributeLimits != null) {
                        min(
                            arraySize,
                            attributeLimits.attributeArrayLengthLimit,
                        )
                    } else {
                        arraySize
                    }

                var index = 0
                while (index < itemCount && iterator.hasNext()) {
                    writeAttributeValue(attributeName, iterator.next())
                    index++
                }
            }
        }

        logArrayOversizeWarningIfRequired(attributeName, arraySize)
    }

    private fun writeIntArray(
        attributeName: String,
        value: IntArray,
    ) {
        name("arrayValue").obj {
            name("values").array {
                val itemCount =
                    if (attributeLimits != null) {
                        min(
                            value.size,
                            attributeLimits.attributeArrayLengthLimit,
                        )
                    } else {
                        value.size
                    }

                for (index in 0 until itemCount) {
                    obj { name("intValue").value(value[index].toString()) }
                }
            }
        }

        logArrayOversizeWarningIfRequired(attributeName, value.size)
    }

    private fun writeDoubleArray(
        attributeName: String,
        value: DoubleArray,
    ) {
        name("arrayValue").obj {
            name("values").array {
                val itemCount =
                    if (attributeLimits != null) {
                        min(
                            value.size,
                            attributeLimits.attributeArrayLengthLimit,
                        )
                    } else {
                        value.size
                    }

                for (index in 0 until itemCount) {
                    obj { name("doubleValue").value(value[index]) }
                }
            }
        }
        logArrayOversizeWarningIfRequired(attributeName, value.size)
    }

    private fun writeLongArray(
        attributeName: String,
        value: LongArray,
    ) {
        name("arrayValue").obj {
            name("values").array {
                val itemCount =
                    if (attributeLimits != null) {
                        min(
                            value.size,
                            attributeLimits.attributeArrayLengthLimit,
                        )
                    } else {
                        value.size
                    }

                for (index in 0 until itemCount) {
                    obj { name("intValue").value(value[index].toString()) }
                }
            }
        }

        logArrayOversizeWarningIfRequired(attributeName, value.size)
    }

    private fun isValidAttributeKey(key: String): Boolean = key.length <= MAX_KEY_LENGTH

    private fun limitedStringValue(value: String): String =
        when {
            attributeLimits != null && value.length > attributeLimits.attributeStringValueLimit -> {
                val trimmedCharacters = value.length - attributeLimits.attributeStringValueLimit
                val truncatedAttr = value.substring(0, attributeLimits.attributeStringValueLimit)
                "$truncatedAttr*** $trimmedCharacters CHARS TRUNCATED"
            }

            else -> value
        }

    private fun logArrayOversizeWarningIfRequired(
        key: String,
        arraySize: Int,
    ) {
        if (attributeLimits != null && arraySize > attributeLimits.attributeArrayLengthLimit) {
            Logger.w(
                "Span attribute $key in span ${currentSpan?.name} was truncated as the " +
                        "array exceeds the ${attributeLimits.attributeArrayLengthLimit} element " +
                        "limit set by attributeArrayLengthLimit.",
            )
        }
    }

    internal companion object {
        const val MAX_KEY_LENGTH = 128
    }
}
