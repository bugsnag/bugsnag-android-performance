package com.bugsnag.android.performance.internal

import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Attributes {
    private val content = mutableMapOf<String, Any>()

    @get:JvmSynthetic
    internal val entries: Collection<Map.Entry<String, Any>>
        inline get() = content.entries

    public val size: Int
        get() = content.size

    public val keys: Set<String> get() = content.keys.toSet()

    public operator fun set(name: String, value: String?) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(name: String, value: Long) {
        content[name] = value
    }

    public operator fun set(name: String, value: Int) {
        content[name] = value.toLong()
    }

    public operator fun set(name: String, value: Double) {
        content[name] = value
    }

    public operator fun set(name: String, value: Boolean) {
        content[name] = value
    }

    @JvmSynthetic
    internal operator fun get(name: String): Any? {
        return content[name]
    }

    public fun remove(name: String) {
        content.remove(name)
    }

    public operator fun set(name: String, value: Collection<String>?) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }

    public operator fun set(name: String, value: Array<String>?) {
        if (value != null) {
            content[name] = value
        } else {
            content.remove(name)
        }
    }
}
