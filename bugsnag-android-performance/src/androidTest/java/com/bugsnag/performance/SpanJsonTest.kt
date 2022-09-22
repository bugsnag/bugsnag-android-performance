package com.bugsnag.performance

import android.util.JsonWriter
import com.bugsnag.performance.test.testSpanProcessor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter
import java.util.UUID

class SpanJsonTest {
    @Test
    fun testJsonEncoding() {
        val currentTime = System.currentTimeMillis()

        val span = SpanImpl(
            "test span",
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            testSpanProcessor,
        )

        val json = StringWriter()
            .apply { use { sw -> JsonWriter(sw).use { jsonw -> span.toJson(jsonw) } } }
            .toString()

        assertJsonEquals(
            """
                {
                    "name": "test span",
                    "kind": "SPAN_KIND_INTERNAL",
                    "spanId": "decafbad",
                    "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                    "startTimeUnixNano": "0",
                    "endTimeUnixNano": "$currentTime"
                }
            """.trimIndent(),
            json
        )
    }

    private fun assertJsonEquals(expected: String, actual: String) {
        val expectedObject = JSONObject(expected).toMap()
        val actualObject = JSONObject(actual).toMap()

        assertEquals(expectedObject, actualObject)
    }
}

private fun JSONObject.toMap(): Map<String, Any> {
    val keys = names()!!
    val content = HashMap<String, Any>(keys.length())

    for (i in 0 until keys.length()) {
        val key = keys.getString(i)
        content[key] = get(key)
    }

    return content
}
