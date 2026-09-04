package com.bugsnag.android.performance.internal.graphql

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.SpanStatusCode
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class GraphQlSpanStatusTest {
    @Test
    fun http200IsOkUntilErrorsAreDetected() {
        val span = graphQlSpan()
        GraphQlSpanStatus.applyHttpStatus(span, 200)
        assertEquals(SpanStatusCode.OK, span.statusCode)

        GraphQlSpanStatus.applyResponseBody(
            span,
            """{"data":null,"errors":[{"message":"User not found"}]}""",
        )
        assertEquals(SpanStatusCode.ERROR, span.statusCode)
    }

    @Test
    fun emptyErrorsArrayDoesNotUpgradeStatus() {
        val span = graphQlSpan()
        GraphQlSpanStatus.applyHttpStatus(span, 200)
        GraphQlSpanStatus.applyResponseBody(
            span,
            """{"data":{"user":{"id":"1"}},"errors":[]}""",
        )
        assertEquals(SpanStatusCode.OK, span.statusCode)
    }

    @Test
    fun http500IsError() {
        val span = graphQlSpan()
        GraphQlSpanStatus.applyHttpStatus(span, 500)
        assertEquals(SpanStatusCode.ERROR, span.statusCode)
    }

    @Test
    fun failureSetsError() {
        val span = graphQlSpan()
        GraphQlSpanStatus.applyFailure(span)
        assertEquals(SpanStatusCode.ERROR, span.statusCode)
    }

    private fun graphQlSpan(): SpanImpl {
        return SpanImpl(
            "GraphQL api.example.com/graphql - query:GetUser",
            SpanCategory.GRAPHQL,
            SpanKind.CLIENT,
            0L,
            UUID.fromString("4ee26661-4650-4c7f-a35f-00f007cd24e7"),
            1L,
            0L,
            false,
            null,
            null,
            TestTimeoutExecutor(),
            NoopSpanProcessor.INSTANCE,
        )
    }
}
