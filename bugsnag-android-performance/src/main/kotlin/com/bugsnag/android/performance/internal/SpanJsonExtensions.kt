@file:JvmName("SpanJson")

package com.bugsnag.android.performance.internal

import android.util.JsonWriter
import com.bugsnag.android.performance.Span

@JvmName("-toJson")
internal fun Span.toJson(json: JsonWriter) {
    json.beginObject()
        .name("name").value(name)
        .name("kind").value(kind.otelName)
        .name("spanId").value(id.toHexString())
        .name("traceId").value(traceId.toHexString())
        .name("startTimeUnixNano").value(BugsnagClock.elapsedNanosToUnixTime(startTime).toString())
        .name("endTimeUnixNano").value(BugsnagClock.elapsedNanosToUnixTime(endTime).toString())
        .endObject()
}
