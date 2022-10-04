package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.SpanProcessor
import org.json.JSONObject
import org.junit.Assert.assertEquals

val testSpanProcessor = SpanProcessor { }

fun assertJsonEquals(expected: String, actual: String) {
    val expectedObject = JSONObject(expected).toString()
    val actualObject = JSONObject(actual).toString()

    assertEquals(expectedObject, actualObject)
}
