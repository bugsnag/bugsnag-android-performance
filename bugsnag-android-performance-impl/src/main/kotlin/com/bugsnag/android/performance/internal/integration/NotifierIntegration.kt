package com.bugsnag.android.performance.internal.integration

import androidx.annotation.RestrictTo
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.performance.SpanContext
import com.bugsnag.android.performance.internal.SpanImpl

/**
 * Integration into the `bugsnag-android` error handling SDK. When `bugsnag-android` is available
 * this object will automatically set the `Event.traceCorrelation` value to the active [SpanContext]
 * when an error is being reported, allowing the errors to be linked to the active trace.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object NotifierIntegration {
    internal var linked = false
        private set

    private var topLevelSpan: SpanImpl? = null

    public fun link() {
        // we suppress the errors to avoid breaking when the SDKs are not compatible
        @Suppress("SwallowedException")
        try {
            if (Bugsnag.isStarted()) {
                Bugsnag.addOnError { event ->
                    // we suppress the errors to avoid breaking when the SDKs are not compatible
                    @Suppress("SwallowedException")
                    try {
                        spanContextForError()?.let { context ->
                            event.setTraceCorrelation(context.traceId, context.spanId)
                        }
                    } catch (noMethod: NoSuchMethodError) {
                        // do nothing, the error SDK and performance-SDK are not compatible
                    }
                    true
                }

                linked = true
            }
        } catch (noClass: NoClassDefFoundError) {
            // do nothing, the error SDK is not in use
        }
    }

    public fun onSpanStarted(span: SpanImpl) {
        if (!linked) return
        if (span.attributes["bugsnag.span.first_class"] == true) {
            topLevelSpan = span
        }
    }

    public fun onSpanEnded(span: SpanImpl) {
        if (!linked) return

        if (topLevelSpan === span) {
            topLevelSpan = null
        }
    }

    private fun spanContextForError(): SpanContext? {
        return SpanContext.current.takeUnless { it == SpanContext.invalid } ?: topLevelSpan
    }
}
