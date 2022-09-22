package com.bugsnag.performance

enum class SpanKind(val otel: String) {
    INTERNAL("SPAN_KIND_INTERNAL"),
    SERVER("SPAN_KIND_SERVER"),
    CLIENT("SPAN_KIND_CLIENT"),
    PRODUCER("SPAN_KIND_PRODUCER"),
    CONSUMER("SPAN_KIND_CONSUMER"),
}
