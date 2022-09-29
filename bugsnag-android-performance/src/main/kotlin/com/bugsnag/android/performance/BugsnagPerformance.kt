package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.internal.Delivery
import com.bugsnag.android.performance.internal.Tracer
import java.util.UUID

object BugsnagPerformance {
    private lateinit var tracer: Tracer

    @JvmStatic
    fun start(configuration: BugsnagPerformanceConfiguration) {
        tracer = Tracer(
            Delivery(configuration.endpoint.toString()),
            configuration.context.packageName
        )

        Thread(tracer, "Bugsnag Tracer").apply {
            isDaemon = true
            start()
        }
    }

    @JvmStatic
    @JvmOverloads
    fun startSpan(name: String, startTime: Long = SystemClock.elapsedRealtimeNanos()): Span =
        Span(name, SpanKind.INTERNAL, startTime, UUID.randomUUID(), processor = tracer)
}

inline fun <R> measureSpan(name: String, block: () -> R): R {
    return BugsnagPerformance.startSpan(name).use { block() }
}
