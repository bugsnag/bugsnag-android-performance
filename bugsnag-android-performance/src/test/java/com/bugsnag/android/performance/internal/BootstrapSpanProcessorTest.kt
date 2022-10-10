package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.test.CollectingSpanProcessor
import com.bugsnag.android.performance.test.TestSpanFactory
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class BootstrapSpanProcessorTest {
    private lateinit var spanFactory: TestSpanFactory

    @Before
    fun createSpanFactory() {
        spanFactory = TestSpanFactory()
    }

    @Test
    fun bootstrapSpansDrainToDelegate() {
        val bootstrapSpanProcessor = BootstrapSpanProcessor()
        val spans = spanFactory.newSpans(4, bootstrapSpanProcessor)

        val collector = CollectingSpanProcessor()
        bootstrapSpanProcessor.drainTo(collector)

        // this will be ended against bootstrapSpanProcessor, which *should* drain directly into
        // collector (instead of collecting it) since it drainTo has been called
        val extraSpan = spanFactory.newSpan("Extra Span", processor = bootstrapSpanProcessor)

        val collectedSpans = collector.toList()
        assertEquals(
            spans + extraSpan,
            collectedSpans
        )
    }
}
