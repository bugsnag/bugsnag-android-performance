package com.bugsnag.android.performance.test

import com.bugsnag.android.performance.internal.SpanImpl
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.util.concurrent.Future
import java.util.concurrent.FutureTask

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

fun endedSpans(vararg spans: SpanImpl): Collection<SpanImpl> {
    spans.forEach { it.end() }
    return spans.asList()
}

/**
 * Run [block] on a background thread and return a `Future` handle to access its result (or exception)
 */
fun <V> task(block: () -> V): Future<V> {
    val task = FutureTask { block() }
    Thread(task).start()
    return task
}
