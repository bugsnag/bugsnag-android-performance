package com.example.bugsnag.performance

import android.app.Application
import android.os.SystemClock
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.context.HybridSpanContextStorage
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PerformanceApplication : Application() {
    companion object {
        private const val SPAN_DEBUG_TAG = "BsgSpanDebug"

        /**
         * Attribute keys we want to surface in the debug span log.
         * These match the OTLP payload contract for network and GraphQL spans.
         */
        private val SPAN_ATTRIBUTE_KEYS = listOf(
            "bugsnag.span.category",
            "bugsnag.span.first_class",
            "http.method",
            "http.url",
            "http.status_code",
            "http.request_content_length",
            "http.response_content_length",
            "graphql.operation.type",
            "graphql.operation.name",
            "bugsnag.sampling.p",
        )

        init {
            // To simplify span parenting the Example uses the HybridSpanContextStorage which
            // has a single global stack of spans while allowing threads to optionally create
            // a ThreadLocal SpanContext store for work that needs to be isolated from the main
            // app process.
            SpanContext.defaultStorage = HybridSpanContextStorage()

            // While calling reportApplicationClassLoaded in the static initializer isn't required
            // it does slightly improve the quality of the AppStart spans by having them start
            // before the ContentProviders are initialized
            BugsnagPerformance.reportApplicationClassLoaded()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val config = PerformanceConfiguration.load(this)
        config.enabledMetrics = EnabledMetrics(true)
        // Disable automatic session management for manual testing
        config.appSessionConfig.autoStartSession = false
        config.appSessionConfig.backgroundTimeoutMs = 0L // No automatic timeout

        // Debug-only: log span attributes to logcat so you can verify the OTLP payload contract
        // without needing to inspect the backend.
        config.addOnSpanEndCallback { span ->
            try {
                val attrs = readField(span, "attributes")
                val getMethod = attrs?.javaClass?.getMethod("get", String::class.java)

                val attributesJson = JSONArray()
                var statusCode: Int? = null
                for (key in SPAN_ATTRIBUTE_KEYS) {
                    val value = getMethod?.invoke(attrs, key) ?: continue
                    if (key == "http.status_code") {
                        statusCode = when (value) {
                            is Number -> value.toInt()
                            is String -> value.toIntOrNull()
                            else -> null
                        }
                    }

                    attributesJson.put(
                        JSONObject()
                            .put("key", key)
                            .put("value", toOtlpValue(value)),
                    )
                }

                val startTime = readField(span, "startTime") as? Long
                val endTime = readField(span, "endTime") as? Long
                if (startTime == null || endTime == null) {
                    Log.w(SPAN_DEBUG_TAG, "Skipping span log because start/end time is unavailable")
                    return@addOnSpanEndCallback true
                }
                val parentSpanId = (readField(span, "parentSpanId") as? Long) ?: 0L
                val kind = readField(span, "kind") as? SpanKind

                val spanJson =
                    JSONObject()
                        .put("traceId", formatTraceId(span.traceId))
                        .put("spanId", formatSpanId(span.spanId))
                        .put("parentSpanId", formatParentSpanId(parentSpanId))
                        .put("name", span.name)
                        .put("startTimeUnixNano", elapsedNanosToUnixNanos(startTime))
                        .put("endTimeUnixNano", elapsedNanosToUnixNanos(endTime))
                        .put("kind", kindToOtelName(kind))
                        .put("attributes", attributesJson)
                        .put(
                            "status",
                            JSONObject().put("code", statusCodeToOtelCode(statusCode)),
                        )

                val spansJson = JSONObject().put("spans", JSONArray().put(spanJson))
                Log.d(SPAN_DEBUG_TAG, spansJson.toString(2))
            } catch (e: Exception) {
                Log.w(SPAN_DEBUG_TAG, "Failed to build OTLP-style span log: ${e.message}")
            }
            true // always deliver the span
        }

        BugsnagPerformance.start(config)
    }

    private fun readField(target: Any, name: String): Any? {
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            val field = runCatching { currentClass.getDeclaredField(name) }.getOrNull()
            if (field != null) {
                field.isAccessible = true
                return field.get(target)
            }
            currentClass = currentClass.superclass
        }
        return null
    }

    private fun toOtlpValue(value: Any): JSONObject {
        return when (value) {
            is Boolean -> JSONObject().put("boolValue", value)
            is Byte -> JSONObject().put("intValue", value.toLong())
            is Short -> JSONObject().put("intValue", value.toLong())
            is Int -> JSONObject().put("intValue", value.toLong())
            is Long -> JSONObject().put("intValue", value)
            is Float -> JSONObject().put("doubleValue", value.toDouble())
            is Double -> JSONObject().put("doubleValue", value)
            else -> JSONObject().put("stringValue", value.toString())
        }
    }

    private fun elapsedNanosToUnixNanos(elapsedNanos: Long): Long {
        val nowUnixNanos = System.currentTimeMillis() * 1_000_000L
        val nowElapsedNanos = SystemClock.elapsedRealtimeNanos()
        val offset = nowUnixNanos - nowElapsedNanos
        return offset + elapsedNanos
    }

    private fun formatTraceId(traceId: UUID): String {
        return traceId.toString().replace("-", "")
    }

    private fun formatSpanId(spanId: Long): String {
        return java.lang.Long.toUnsignedString(spanId, 16).padStart(16, '0')
    }

    private fun formatParentSpanId(parentSpanId: Long): String {
        return if (parentSpanId == 0L) {
            ""
        } else {
            java.lang.Long.toUnsignedString(parentSpanId, 16).padStart(16, '0')
        }
    }

    private fun kindToOtelName(kind: SpanKind?): String {
        return when (kind) {
            SpanKind.INTERNAL -> "SPAN_KIND_INTERNAL"
            SpanKind.SERVER -> "SPAN_KIND_SERVER"
            SpanKind.CLIENT -> "SPAN_KIND_CLIENT"
            SpanKind.PRODUCER -> "SPAN_KIND_PRODUCER"
            SpanKind.CONSUMER -> "SPAN_KIND_CONSUMER"
            null -> "SPAN_KIND_INTERNAL"
        }
    }

    private fun statusCodeToOtelCode(httpStatusCode: Int?): String {
        return when {
            httpStatusCode == null -> "STATUS_CODE_UNSET"
            httpStatusCode >= 400 -> "STATUS_CODE_ERROR"
            else -> "STATUS_CODE_OK"
        }
    }
}