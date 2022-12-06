package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.internal.SpanProcessor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.mockito.MockedStatic
import org.mockito.Mockito

val testSpanProcessor = SpanProcessor { }

fun assertJsonEquals(expected: String, actual: String) {
    val expectedObject = JSONObject(expected).toString()
    val actualObject = JSONObject(actual).toString()

    assertEquals(expectedObject, actualObject)
}

/**
 * Utility to simplify using [Mockito#mockStatic] with Kotlin. Mostly used to mock `SystemClock`
 */
inline fun <reified S> withStaticMock(block: (MockedStatic<S>) -> Unit) {
    val static = Mockito.mockStatic(S::class.java)
    try {
        block(static)
    } finally {
        static.close()
    }
}

fun endedSpans(vararg spans: Span): Collection<Span> {
    spans.forEach { it.end() }
    return spans.asList()
}
