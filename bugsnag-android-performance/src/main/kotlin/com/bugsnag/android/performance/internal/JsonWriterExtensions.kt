package com.bugsnag.android.performance.internal

import android.util.JsonWriter

internal inline fun JsonWriter.obj(builder: JsonWriter.() -> Unit): JsonWriter {
    beginObject()
    builder()
    endObject()

    return this
}

internal inline fun JsonWriter.array(builder: JsonWriter.() -> Unit): JsonWriter {
    beginArray()
    builder()
    endArray()

    return this
}

internal fun writeIntArray(json: JsonWriter, value: IntArray) {
    json.name("array").obj {
        json.name("values").array {
            value.forEach { intValue ->
                json.obj {
                    name("intValue").value(intValue.toString())
                }
            }
        }
    }
}

internal fun writeDoubleArray(json: JsonWriter, value: DoubleArray) {
    json.name("array").obj {
        json.name("values").array {
            value.forEach { doubleArray ->
                json.obj {
                    name("doubleValue").value(doubleArray.toString())
                }
            }
        }
    }
}

internal fun writeLongArray(json: JsonWriter, value: LongArray) {
    json.name("array").obj {
        json.name("values").array {
            value.forEach { longValue ->
                json.obj {
                    name("intValue").value(longValue.toString())
                }
            }
        }
    }
}

