package com.bugsnag.android.performance

enum class SpanKind(
    @JvmField
    @JvmSynthetic
    internal val otelName: String
) {
    INTERNAL("SPAN_KIND_INTERNAL"),
    SERVER("SPAN_KIND_SERVER"),
    CLIENT("SPAN_KIND_CLIENT"),
    PRODUCER("SPAN_KIND_PRODUCER"),
    CONSUMER("SPAN_KIND_CONSUMER"),
}
