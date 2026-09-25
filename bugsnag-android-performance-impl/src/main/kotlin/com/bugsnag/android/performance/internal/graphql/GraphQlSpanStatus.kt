package com.bugsnag.android.performance.internal.graphql

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.SpanStatusCode
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl

/**
 * Applies GraphQL span status using the same rules as the iOS SDK:
 * HTTP 200 with a non-empty `errors` array is an error; HTTP >= 400 is an error;
 * transport failures (timeouts) are errors.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object GraphQlSpanStatus {
    private const val HTTP_CLIENT_ERROR = 400

    public fun applyHttpStatus(
        span: SpanImpl,
        statusCode: Int,
    ) {
        if (span.category != SpanCategory.GRAPHQL) {
            return
        }
        span.setStatus(
            if (statusCode >= HTTP_CLIENT_ERROR) {
                SpanStatusCode.ERROR
            } else {
                SpanStatusCode.OK
            },
        )
    }

    public fun applyResponseBody(
        span: SpanImpl,
        body: String?,
    ) {
        if (span.category != SpanCategory.GRAPHQL) {
            return
        }
        if (GraphQlRequestClassifier.hasNonEmptyErrorsArray(body)) {
            span.setStatus(SpanStatusCode.ERROR)
        }
    }

    public fun applyFailure(span: SpanImpl) {
        if (span.category != SpanCategory.GRAPHQL) {
            return
        }
        span.setStatus(SpanStatusCode.ERROR)
    }
}
