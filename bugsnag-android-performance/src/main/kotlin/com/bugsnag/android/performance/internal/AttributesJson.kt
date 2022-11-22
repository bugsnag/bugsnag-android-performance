@file:JvmName("AttributesJson")

package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import com.bugsnag.android.performance.Attributes

internal fun JsonWriter.value(attributes: Attributes): JsonWriter {
    beginArray()

    attributes.forEach { (key, value) ->
        beginObject() // attribute
            .name("key").value(key)
            .name("value")

        toAttributeJson(value, this)

        endObject() // attribute
    }

    return endArray()
}

private fun toAttributeJson(value: Any, json: JsonWriter) {
    json.beginObject()
    when (value) {
        is String -> json.name("stringValue").value(value)
        is Float -> json.name("doubleValue").value(value.toDouble())
        is Double -> json.name("doubleValue").value(value)
        is Boolean -> json.name("boolValue").value(value)
        // int64 is JSON encoded as a String
        is Long, Int, Short, Byte -> json.name("intValue").value(value.toString())
    }
    json.endObject()
}
