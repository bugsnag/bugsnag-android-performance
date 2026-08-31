package com.bugsnag.android.performance.internal.metrics

import android.os.SystemClock
import com.bugsnag.android.performance.internal.Attributes
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.TracePayload
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.OtelValidator.assertTraceDataValid
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.withStaticMock
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * ROAD 2233 – Scenario 12 (Android):
 * OTLP payload contains exactly 3 disk attributes with correct keys and structure.
 *
 * Validates resourceSpans.scopeSpans.spans.attributes nesting, case-sensitive key strings,
 * doubleValue encoding, and absence of legacy disk attribute keys.
 */
@RunWith(RobolectricTestRunner::class)
internal class DiskIoMetricsOtlpPayloadTest {
    private lateinit var ioFile: File

    @Before
    fun setup() {
        ioFile = File.createTempFile("diskio-otlp", null)
    }

    @Test
    fun otlpPayloadContainsExactlyThreeDiskIopsAttributes() {
        lateinit var span: SpanImpl

        withStaticMock<SystemClock> { clock ->
            writeIoFile(syscr = 1000L, syscw = 500L)
            clock.`when`<Long>(SystemClock::elapsedRealtimeNanos)
                .thenReturn(
                    NANOS_PER_SECOND,
                    NANOS_PER_SECOND,
                    3L * NANOS_PER_SECOND,
                )

            val source = DiskIoMetricsSource(ProcIoReader(ioFile.absolutePath))
            val startSnapshot = source.createStartMetrics()
            writeIoFile(syscr = 1060L, syscw = 530L)

            span = TestSpanFactory().newSpan(processor = NoopSpanProcessor.INSTANCE)
            source.endMetrics(startSnapshot, span)
            span.end(3L * NANOS_PER_SECOND)
        }

        val payload =
            TracePayload.encodeSpanPayload(
                listOf(span),
                Attributes().also { attrs ->
                    attrs["service.name"] = "Test app"
                    attrs["telemetry.sdk.name"] = "bugsnag.performance.android"
                    attrs["telemetry.sdk.version"] = "0.0.0"
                },
                null,
            )

        assertTraceDataValid(payload)

        val payloadJson = payload.toString(Charsets.UTF_8)
        LEGACY_DISK_KEYS.forEach { legacyKey ->
            assertFalse(
                "Legacy disk key must not appear in payload: $legacyKey",
                payloadJson.contains("\"key\":\"$legacyKey\""),
            )
        }
        RAW_COUNTER_KEYS.forEach { rawKey ->
            assertFalse(
                "Raw counter key must not appear in payload: $rawKey",
                payloadJson.contains("\"key\":\"$rawKey\""),
            )
        }

        val spanAttributes = jsonArrayToList(spanAttributesFromPayload(JSONObject(payloadJson)))
        val diskAttributes =
            spanAttributes.filter { attribute: JSONObject ->
                attribute.getString("key").startsWith(DISK_KEY_PREFIX)
            }

        assertEquals(3, diskAttributes.size)

        val diskKeys = diskAttributes.map { it.getString("key") }.toSet()
        assertEquals(REQUIRED_DISK_KEYS, diskKeys)

        diskAttributes.forEach { attribute: JSONObject ->
            val value = attribute.getJSONObject("value")
            assertTrue(value.has("doubleValue"))
            assertFalse(value.has("intValue"))
            assertFalse(value.has("stringValue"))
            assertFalse(value.has("boolValue"))
            assertTrue(value.getDouble("doubleValue").isFinite())
        }

        assertEquals(
            45.0,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_TOTAL),
            0.001,
        )
        assertEquals(
            30.0,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_READ),
            0.001,
        )
        assertEquals(
            15.0,
            diskAttributes.doubleValueFor(DiskIoMetricsSource.ATTR_IOPS_WRITE),
            0.001,
        )
    }

    private fun jsonArrayToList(array: JSONArray): List<JSONObject> =
        List(array.length()) { index -> array.getJSONObject(index) }

    private fun spanAttributesFromPayload(payload: JSONObject): JSONArray {
        val attributes =
            payload
                .getJSONArray("resourceSpans")
                .getJSONObject(0)
                .getJSONArray("scopeSpans")
                .getJSONObject(0)
                .getJSONArray("spans")
                .getJSONObject(0)
                .getJSONArray("attributes")
        assertNotNull(attributes)
        return attributes
    }

    private fun List<JSONObject>.doubleValueFor(key: String): Double {
        val attribute = first { it.getString("key") == key }
        return attribute.getJSONObject("value").getDouble("doubleValue")
    }

    private fun writeIoFile(
        syscr: Long,
        syscw: Long,
    ) {
        ioFile.writeText(
            """
            rchar: 0
            wchar: 0
            syscr: $syscr
            syscw: $syscw
            read_bytes: 0
            write_bytes: 0
            cancelled_write_bytes: 0
            """.trimIndent(),
        )
    }

    private companion object {
        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val DISK_KEY_PREFIX = "bugsnag.device.disk."

        private val REQUIRED_DISK_KEYS =
            setOf(
                DiskIoMetricsSource.ATTR_IOPS_READ,
                DiskIoMetricsSource.ATTR_IOPS_WRITE,
                DiskIoMetricsSource.ATTR_IOPS_TOTAL,
            )

        private val LEGACY_DISK_KEYS =
            listOf(
                "bugsnag.app.disk.bytes_read",
                "bugsnag.app.disk.bytes_written",
                "bugsnag.app.disk.read_bytes_per_sec",
                "bugsnag.app.disk.write_bytes_per_sec",
                "bugsnag.app.disk.ops_per_sec",
            )

        private val RAW_COUNTER_KEYS =
            listOf(
                "syscr",
                "syscw",
                "read_bytes",
                "write_bytes",
                "rchar",
                "wchar",
            )
    }
}
