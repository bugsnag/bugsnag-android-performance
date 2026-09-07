package com.bugsnag.android.performance

/**
 * OpenTelemetry span status codes.
 *
 * @see <a href="https://opentelemetry.io/docs/specs/otel/trace/api/#set-status">OTel Span Status</a>
 */
public enum class SpanStatusCode {
    UNSET,
    OK,
    ERROR,
}
