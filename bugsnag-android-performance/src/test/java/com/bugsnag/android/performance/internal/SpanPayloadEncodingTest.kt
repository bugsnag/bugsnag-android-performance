package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import com.bugsnag.android.performance.test.assertJsonEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SpanPayloadEncodingTest {
    @Test
    @Suppress("LongMethod")
    fun testDeliver() {
        val span1 = SpanImpl(
            "test span",
            SpanCategory.CUSTOM,
            SpanKind.INTERNAL,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xdecafbad,
            0L,
            false,
            null,
            null,
            TestTimeoutExecutor(),
            NoopSpanProcessor,
        )
        span1.end(1L)
        val span2 = SpanImpl(
            "second span",
            SpanCategory.CUSTOM,
            SpanKind.INTERNAL,
            10L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            0xbaddecaf,
            0L,
            false,
            null,
            null,
            TestTimeoutExecutor(),
            NoopSpanProcessor,
        )
        span2.end(11L)
        val spans = listOf(span1, span2)

        val content = TracePayload.encodeSpanPayload(
            spans,
            Attributes().also { attrs ->
                attrs["service.name"] = "Test app"
                attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                attrs["telemetry.sdk.version"] = "0.0.0"
            },
            null,
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
                              "name": "test span",
                              "kind": 1,
                              "spanId": "00000000decafbad",
                              "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                              "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span1.startTime)}",
                              "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span1.endTime)}",
                              "attributes": [
                                {
                                    "key": "bugsnag.sampling.p",
                                    "value": { "doubleValue": 1.0 }
                                },
                                {
                                    "key": "bugsnag.span.category",
                                    "value": { "stringValue": "custom" }
                                }
                              ]
                            },
                            {
                              "name": "second span",
                              "kind": 1,
                              "spanId": "00000000baddecaf",
                              "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                              "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span2.startTime)}",
                              "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span2.endTime)}",
                              "attributes": [
                                {
                                    "key": "bugsnag.sampling.p",
                                    "value": { "doubleValue": 1.0 }
                                },
                                {
                                    "key": "bugsnag.span.category",
                                    "value": { "stringValue": "custom" }
                                }
                              ]
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            content.toString(Charsets.UTF_8),
        )
    }
}
