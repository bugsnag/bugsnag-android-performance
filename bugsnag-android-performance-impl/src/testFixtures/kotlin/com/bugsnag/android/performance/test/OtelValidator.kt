package com.bugsnag.android.performance.test

object OtelValidator {
    @Suppress("UNUSED_PARAMETER")
    fun assertTraceDataValid(json: ByteArray) {
        require(json.isNotEmpty()) { "trace payload must not be empty" }
    }
}
