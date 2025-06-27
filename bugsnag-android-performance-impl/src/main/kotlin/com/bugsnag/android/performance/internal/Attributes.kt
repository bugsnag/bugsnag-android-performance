package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Attributes {
    private val content = mutableMapOf<String, Any>()

    public val entries: Collection<Map.Entry<String, Any>>
        get() = content.entries

    public val size: Int
        get() = content.size

    public val keys: Set<String> get() = content.keys.toSet()

    public operator fun set(
        name: String,
        value: String?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(
        name: String,
        value: Long,
    ) {
        content[name] = value
    }

    public operator fun set(
        name: String,
        value: Int,
    ) {
        content[name] = value.toLong()
    }

    public operator fun set(
        name: String,
        value: Double,
    ) {
        content[name] = value
    }

    public operator fun set(
        name: String,
        value: Boolean,
    ) {
        content[name] = value
    }

    public operator fun contains(name: String): Boolean {
        return content.containsKey(name)
    }

    public operator fun get(name: String): Any? {
        return content[name]
    }

    public fun remove(name: String) {
        content.remove(name)
    }

    public operator fun set(
        name: String,
        value: Array<String>?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(
        name: String,
        value: Collection<Any>?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(
        name: String,
        value: IntArray?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(
        name: String,
        value: LongArray?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(
        name: String,
        value: DoubleArray?,
    ) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }
}
