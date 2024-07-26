@file:JvmName("AttributesJson")

package com.bugsnag.android.performance.internal

import android.util.JsonWriter

internal fun JsonWriter.value(attributes: Attributes): JsonWriter {
    return array {
        attributes.entries.forEach { (key, value) ->
            obj {
                name("key").value(key)

                name("value")
                toAttributeJson(value, this)
            }
        }
    }
}

private fun toAttributeJson(value: Any, json: JsonWriter) {
    json.obj {
        when (value) {
            is String -> json.name("stringValue").value(value)
            is Float -> json.name("doubleValue").value(value.toDouble())
            is Double -> json.name("doubleValue").value(value)
            is Boolean -> json.name("boolValue").value(value)
            // int64 is JSON encoded as a String
            is Long, is Int, is Short, is Byte -> {
                json.name("intValue").value(value.toString())
            }
            is Collection<*> -> writeArray(json, value.iterator())
            is Array<*> -> writeArray(json, value.iterator())
            is IntArray -> writeIntArray(json, value)
            is LongArray -> writeLongArray(json, value)
            is DoubleArray -> writeDoubleArray(json, value)
        }
    }
}

private fun writeArray(json: JsonWriter, value: Iterator<Any?>) {
    json.name("arrayValue").obj {
        json.name("values").array {
            value.forEach { item ->
                item?.let {
                    toAttributeJson(item, json)
                }
            }
        }
    }
}


private fun writeIntArray(json: JsonWriter, value: IntArray) {
    json.name("arrayValue").obj {
        json.name("values").array {
            value.forEach { intValue ->
                json.obj {
                    name("intValue").value(intValue.toString())
                }
            }
        }
    }
}

private fun writeDoubleArray(json: JsonWriter, value: DoubleArray) {
    json.name("arrayValue").obj {
        json.name("values").array {
            value.forEach { doubleArray ->
                json.obj {
                    name("doubleValue").value(doubleArray.toString())
                }
            }
        }
    }
}

private fun writeLongArray(json: JsonWriter, value: LongArray) {
    json.name("arrayValue").obj {
        json.name("values").array {
            value.forEach { longValue ->
                json.obj {
                    name("intValue").value(longValue.toString())
                }
            }
        }
    }
}
