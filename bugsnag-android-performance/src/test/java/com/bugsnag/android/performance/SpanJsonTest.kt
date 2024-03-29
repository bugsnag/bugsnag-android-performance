package com.bugsnag.android.performance

import android.util.JsonWriter
import com.bugsnag.android.performance.internal.BugsnagClock
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.assertJsonEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.StringWriter
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SpanJsonTest {
    @Test
    fun testJsonEncoding() {
        val currentTime = System.currentTimeMillis()

        val span = SpanImpl(
            "test span",
            SpanCategory.CUSTOM,
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            123L,
            NoopSpanProcessor,
            false,
        )

        span.setAttribute("fps.average", 61.9)
        span.setAttribute("frameTime.minimum", 1)
        span.setAttribute("release", true)
        span.setAttribute("my.custom.attribute", "Computer, belay that order.")

        span.end(currentTime)

        val json = StringWriter()
            .apply { use { sw -> JsonWriter(sw).use { jsonw -> span.toJson(jsonw) } } }
            .toString()

        assertJsonEquals(
            """
                {
                    "name": "test span",
                    "kind": 1,
                    "spanId": "00000000decafbad",
                    "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                    "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(0)}",
                    "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(currentTime)}",
                    "parentSpanId": "000000000000007b",
                    "attributes": [
                        {
                            "key": "bugsnag.sampling.p",
                            "value": { "doubleValue": 1.0 }
                        },
                        {
                            "key": "fps.average",
                            "value": { "doubleValue": 61.9 }
                        },
                        {
                            "key": "frameTime.minimum",
                            "value": { "intValue": "1" }
                        },
                        {
                            "key": "release",
                            "value": { "boolValue": true }
                        },
                        {
                            "key": "my.custom.attribute",
                            "value": { "stringValue": "Computer, belay that order." }
                        }
                    ]
                }
            """.trimIndent(),
            json,
        )
    }
}
