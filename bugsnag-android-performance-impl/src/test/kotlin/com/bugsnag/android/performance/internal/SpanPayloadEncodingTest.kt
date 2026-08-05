package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import com.bugsnag.android.performance.test.assertJsonEquals
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class SpanPayloadEncodingTest {
    @Test
    @Suppress("LongMethod")
    fun testDeliver() {
        val span1 =
            SpanImpl(
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
                NoopSpanProcessor.INSTANCE,
            )
        span1.end(1L)
        val span2 =
            SpanImpl(
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
                NoopSpanProcessor.INSTANCE,
            )
        span2.end(11L)
        val spans = listOf(span1, span2)

        val content =
            TracePayload.encodeSpanPayload(
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

    @Test
    fun testGraphQlSpanPayloadIncludesExpectedGraphQlAndHttpAttributes() {
        val span =
            SpanImpl(
                "[GraphQL] query:GetUserProfile",
                SpanCategory.GRAPHQL,
                SpanKind.CLIENT,
                100L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0xaaaabbbb,
                0L,
                false,
                null,
                null,
                TestTimeoutExecutor(),
                NoopSpanProcessor,
            )
        span.setAttribute("http.url", "https://api.example.com/graphql")
        span.setAttribute("http.method", "POST")
        span.setAttribute("http.status_code", 200)
        span.setAttribute("graphql.operation.type", "query")
        span.setAttribute("graphql.operation.name", "GetUserProfile")
        span.end(101L)

        val content =
            TracePayload.encodeSpanPayload(
                listOf(span),
                Attributes().also { attrs ->
                    attrs["service.name"] = "Test app"
                    attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                    attrs["telemetry.sdk.version"] = "0.0.0"
                },
                null,
            )

        assertTraceDataValid(content)

        val spanObject = firstSpan(JSONObject(content.toString(Charsets.UTF_8)))
        val attributes = attributesByKey(spanObject)

        assertEquals("[GraphQL] query:GetUserProfile", spanObject.getString("name"))
        assertEquals("graphql", attributes["bugsnag.span.category"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("https://api.example.com/graphql", attributes["http.url"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("POST", attributes["http.method"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("200", attributes["http.status_code"]?.getJSONObject("value")?.getString("intValue"))
        assertEquals("query", attributes["graphql.operation.type"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("GetUserProfile", attributes["graphql.operation.name"]?.getJSONObject("value")?.getString("stringValue"))
    }

    @Test
    fun testGraphQlSpanPayloadMatchesGoldenSnapshot() {
        val span =
            SpanImpl(
                "[GraphQL] query:GetUserProfile",
                SpanCategory.GRAPHQL,
                SpanKind.CLIENT,
                300L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0x11112222,
                0L,
                false,
                null,
                null,
                TestTimeoutExecutor(),
                NoopSpanProcessor,
            )
        span.setAttribute("http.url", "https://api.example.com/graphql")
        span.setAttribute("http.method", "POST")
        span.setAttribute("http.status_code", 200)
        span.setAttribute("graphql.operation.type", "query")
        span.setAttribute("graphql.operation.name", "GetUserProfile")
        span.end(301L)

        val content =
            TracePayload.encodeSpanPayload(
                listOf(span),
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
                          "name": "[GraphQL] query:GetUserProfile",
                          "kind": 3,
                          "spanId": "0000000011112222",
                          "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                          "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.startTime)}",
                          "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.endTime)}",
                          "attributes": [
                            {
                              "key": "bugsnag.sampling.p",
                              "value": { "doubleValue": 1.0 }
                            },
                            {
                              "key": "bugsnag.span.category",
                              "value": { "stringValue": "graphql" }
                            },
                            {
                              "key": "http.url",
                              "value": { "stringValue": "https://api.example.com/graphql" }
                            },
                            {
                              "key": "http.method",
                              "value": { "stringValue": "POST" }
                            },
                            {
                              "key": "http.status_code",
                              "value": { "intValue": "200" }
                            },
                            {
                              "key": "graphql.operation.type",
                              "value": { "stringValue": "query" }
                            },
                            {
                              "key": "graphql.operation.name",
                              "value": { "stringValue": "GetUserProfile" }
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

    @Test
    fun testNetworkSpanPayloadRemainsHttpAndOmitsGraphQlAttributes() {
        val span =
            SpanImpl(
                "[HTTP/POST]",
                SpanCategory.NETWORK,
                SpanKind.CLIENT,
                200L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0xccccdddd,
                0L,
                false,
                null,
                null,
                TestTimeoutExecutor(),
                NoopSpanProcessor,
            )
        span.setAttribute("http.url", "https://api.example.com/rest/users")
        span.setAttribute("http.method", "POST")
        span.setAttribute("http.status_code", 201)
        span.end(201L)

        val content =
            TracePayload.encodeSpanPayload(
                listOf(span),
                Attributes().also { attrs ->
                    attrs["service.name"] = "Test app"
                    attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                    attrs["telemetry.sdk.version"] = "0.0.0"
                },
                null,
            )

        assertTraceDataValid(content)

        val spanObject = firstSpan(JSONObject(content.toString(Charsets.UTF_8)))
        val attributes = attributesByKey(spanObject)

        assertEquals("[HTTP/POST]", spanObject.getString("name"))
        assertEquals("network", attributes["bugsnag.span.category"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("https://api.example.com/rest/users", attributes["http.url"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("POST", attributes["http.method"]?.getJSONObject("value")?.getString("stringValue"))
        assertEquals("201", attributes["http.status_code"]?.getJSONObject("value")?.getString("intValue"))

        assertFalse(attributes.containsKey("graphql.operation.type"))
        assertFalse(attributes.containsKey("graphql.operation.name"))
    }

    @Test
    fun testNetworkSpanPayloadMatchesGoldenSnapshot() {
        val span =
            SpanImpl(
                "[HTTP/POST]",
                SpanCategory.NETWORK,
                SpanKind.CLIENT,
                400L,
                UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
                0x33334444,
                0L,
                false,
                null,
                null,
                TestTimeoutExecutor(),
                NoopSpanProcessor,
            )
        span.setAttribute("http.url", "https://api.example.com/rest/users")
        span.setAttribute("http.method", "POST")
        span.setAttribute("http.status_code", 201)
        span.setAttribute("http.request_content_length", 64)
        span.setAttribute("http.response_content_length", 512)
        span.end(401L)

        val content =
            TracePayload.encodeSpanPayload(
                listOf(span),
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
                          "name": "[HTTP/POST]",
                          "kind": 3,
                          "spanId": "0000000033334444",
                          "traceId": "4ee2666146504c7fa35f00f007cd24e7",
                          "startTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.startTime)}",
                          "endTimeUnixNano": "${BugsnagClock.elapsedNanosToUnixTime(span.endTime)}",
                          "attributes": [
                            {
                              "key": "bugsnag.sampling.p",
                              "value": { "doubleValue": 1.0 }
                            },
                            {
                              "key": "bugsnag.span.category",
                              "value": { "stringValue": "network" }
                            },
                            {
                              "key": "http.url",
                              "value": { "stringValue": "https://api.example.com/rest/users" }
                            },
                            {
                              "key": "http.method",
                              "value": { "stringValue": "POST" }
                            },
                            {
                              "key": "http.status_code",
                              "value": { "intValue": "201" }
                            },
                            {
                              "key": "http.request_content_length",
                              "value": { "intValue": "64" }
                            },
                            {
                              "key": "http.response_content_length",
                              "value": { "intValue": "512" }
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

    private fun firstSpan(root: JSONObject): JSONObject {
        val resourceSpans = root.getJSONArray("resourceSpans")
        assertFalse(resourceSpans.length() == 0)
        val firstResourceSpan = resourceSpans.getJSONObject(0)
        val scopeSpans = firstResourceSpan.getJSONArray("scopeSpans")
        assertFalse(scopeSpans.length() == 0)
        val firstScopeSpan = scopeSpans.getJSONObject(0)
        val spans = firstScopeSpan.getJSONArray("spans")
        assertFalse(spans.length() == 0)
        return spans.getJSONObject(0)
    }

    private fun attributesByKey(spanObject: JSONObject): Map<String, JSONObject> {
        val attributesArray = spanObject.getJSONArray("attributes")
        val attributesByKey = linkedMapOf<String, JSONObject>()

        for (index in 0 until attributesArray.length()) {
            val attribute = attributesArray.getJSONObject(index)
            val key = attribute.getString("key")
            attributesByKey[key] = attribute
        }

        assertNotNull(attributesByKey["bugsnag.sampling.p"])
        return attributesByKey
    }
}
