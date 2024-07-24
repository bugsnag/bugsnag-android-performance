@file:JvmName("AttributesJson")

package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import kotlin.collections.forEach

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
            is Collection<*> -> json.name("arrayValue").obj {
                json.name("values").array {
                    value.forEach { item ->
                        item?.let {
                            toAttributeJson(item, json)
                        }
                    }
                }
            }

            is Array<*> -> json.name("arrayValue").obj {
                json.name("values").array {
                    value.forEach { item ->
                        item?.let { toAttributeJson(it, json) }
                    }
                }
            }

            is IntArray -> json.name("array").obj {
                json.name("values").array {
                    value.forEach { intValue ->
                        json.obj {
                            name("intValue").value(intValue.toString())
                        }
                    }
                }
            }

            is LongArray -> json.name("array").obj {
                json.name("values").array {
                    value.forEach { longValue ->
                        json.obj {
                            name("longValue").value(longValue.toString())
                        }
                    }
                }
            }

            is DoubleArray -> json.name("array").obj {
                json.name("values").array {
                    value.forEach { doubleArray ->
                        json.obj {
                            name("doubleValue").value(doubleArray.toString())
                        }
                    }
                }
            }
        }
    }
}
