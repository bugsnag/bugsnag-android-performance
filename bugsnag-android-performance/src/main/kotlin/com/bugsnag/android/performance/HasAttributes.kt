package com.bugsnag.android.performance

interface HasAttributes {
    val attributes: Attributes

    fun setAttribute(key: String, value: String?) {
        attributes[key] = value
    }

    fun setAttribute(key: String, value: Long) {
        attributes[key] = value
    }

    fun setAttribute(key: String, value: Int) {
        attributes[key] = value
    }

    fun setAttribute(key: String, value: Double) {
        attributes[key] = value
    }

    fun setAttribute(key: String, value: Boolean) {
        attributes[key] = value
    }
}
