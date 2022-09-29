package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.Attributes
import com.bugsnag.android.performance.Span
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.assertJsonEquals
import com.bugsnag.android.performance.test.testSpanProcessor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class DeliveryTest {
    @Test
    fun testEncodedSpanChain() {
        val span = Span(
            "test span",
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            testSpanProcessor,
        ).let { first ->
            first.end(1L)
            Span(
                "second span",
                SpanKind.INTERNAL,
                10L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0xbaddecaf,
                testSpanProcessor,
            ).also {
                it.end(11L)
                it.previous = first
            }
        }

        val delivery = Delivery("")
        val content = delivery.encodeSpanPayload(
            span,
            Attributes().also { attrs ->
                attrs["service.name"] = "Test app"
                attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                attrs["telemetry.sdk.version"] = "0.0.0"
            }
        )

        assertTraceDataValid(content)
        assertJsonEquals(
            """
                {
                  "resourceSpans": [
                    {
                      "resource": {
                        "attributes": [
                          {
                            "key": "service.name",
                            "value": {
                              "stringValue": "Test app"
                            }
                          },
                          {
                            "key": "telemetry.sdk.name",
                            "value": {
                              "stringValue": "bugsnag.performance.android"
                            }
                          },
                          {
                            "key": "telemetry.sdk.version",
                            "value": {
                              "stringValue": "0.0.0"
                            }
                          }
                        ]
                      },
                      "scopeSpans": [
                        {
                          "spans": [
                            {
                              "name": "second span",
                              "kind": "SPAN_KIND_INTERNAL",
                              "spanId": "00000000baddecaf",
                              "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                              "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.startTime)}",
                              "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.endTime)}"
                            },
                            {
                              "name": "test span",
                              "kind": "SPAN_KIND_INTERNAL",
                              "spanId": "00000000decafbad",
                              "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                              "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.previous!!.startTime)}",
                              "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.previous!!.endTime)}"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            content.toString(Charsets.UTF_8)
        )
    }
}
