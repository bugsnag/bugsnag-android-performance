package com.example.bugsnag.performance

import android.app.Application
import android.util.Log
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.EnabledMetrics
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.context.HybridSpanContextStorage

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

        // Debug-only: log span attributes to logcat so you can verify the OTLP payload contract
        // without needing to inspect the backend.
        config.addOnSpanEndCallback { span ->
            try {
                // SpanImpl.attributes is a Kotlin val — backing field is private, so we use
                // getDeclaredField (not getField) and force accessibility.
                val attrsField = span.javaClass.getDeclaredField("attributes")
                attrsField.isAccessible = true
                val attrs = attrsField.get(span)

                val sb = StringBuilder()
                sb.append("\n┌─── [SpanEnd] name=${span.name}")
                sb.append("\n│  attributes:")

                // Read each known key via the get(String) operator on Attributes
                val getMethod = attrs?.javaClass?.getMethod("get", String::class.java)
                for (key in SPAN_ATTRIBUTE_KEYS) {
                    val value = getMethod?.invoke(attrs, key)
                    if (value != null) sb.append("\n│    $key = $value")
                }

                sb.append("\n└────────────────────────────")
                Log.d(SPAN_DEBUG_TAG, sb.toString())
            } catch (e: Exception) {
                Log.w(SPAN_DEBUG_TAG, "Failed to read span attributes: ${e.message}")
            }
            true // always deliver the span
        }

        BugsnagPerformance.start(config)
    }
}