package com.bugsnag.android.performance

public enum class SpanKind(
    @JvmField
    internal val otelName: String,
    @JvmField
    internal val otelOrdinal: Int,
) {
    INTERNAL("SPAN_KIND_INTERNAL", 1),
    SERVER("SPAN_KIND_SERVER", 2),
    CLIENT("SPAN_KIND_CLIENT", 3),
    PRODUCER("SPAN_KIND_PRODUCER", 4),
    CONSUMER("SPAN_KIND_CONSUMER", 5),
}
