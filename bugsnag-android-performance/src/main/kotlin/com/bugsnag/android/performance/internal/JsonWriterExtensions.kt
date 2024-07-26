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
