package com.bugsnag.android.performance.controls

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.internal.SpanCategory
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test

class NamedSpanControlProviderTest {
    private lateinit var testTimeoutExecutor: TestTimeoutExecutor
    private lateinit var namedSpanControlProvider: NamedSpanControlProvider
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun setUp() {
        testTimeoutExecutor = TestTimeoutExecutor()
        namedSpanControlProvider =
            NamedSpanControlProvider(
                timeoutExecutor = testTimeoutExecutor,
                timeout = 1000L,
                timeoutUnit = java.util.concurrent.TimeUnit.MILLISECONDS,
            )
        spanFactory = TestSpanFactory()
    }

    @Test
    fun duplicateNamedSpansCancelRedundantTimeouts() {
        val span1 =
            spanFactory.newSpan(
                "span1",
                SpanKind.INTERNAL,
                1L,
                null,
                null,
                0L,
                0L,
                SpanCategory.CATEGORY_CUSTOM,
                NoopSpanProcessor.INSTANCE,
            )

        val span1Duplicate =
            spanFactory.newSpan(
                "span1",
                SpanKind.INTERNAL,
                1L,
                null,
                null,
                0L,
                0L,
                SpanCategory.CATEGORY_CUSTOM,
                NoopSpanProcessor.INSTANCE,
            )

        namedSpanControlProvider.onSpanStart(span1)
        assertEquals(1, testTimeoutExecutor.timeouts.size)
        val timeout1 = testTimeoutExecutor.timeouts.first()
        namedSpanControlProvider.onSpanStart(span1Duplicate)
        assertEquals(1, testTimeoutExecutor.timeouts.size)
        assertNotSame(timeout1, testTimeoutExecutor.timeouts.first())
        namedSpanControlProvider.onSpanEnd(span1)
        assertEquals(1, testTimeoutExecutor.timeouts.size)
        namedSpanControlProvider.onSpanEnd(span1Duplicate)
    }
}
