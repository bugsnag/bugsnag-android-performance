package com.bugsnag.android.performance.internal

import com.bugsnag.android.performance.SpanKind
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.TestAttributeLimits
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class CustomSpanAttributeLimitsTest {
    @Test
    fun limitCustomAttributeCount() {
        val spanImpl =
            SpanImpl(
                "Test Span",
                SpanCategory.CUSTOM,
                SpanKind.INTERNAL,
                0L,
                UUID.randomUUID(),
                1L,
                0L,
                false,
                TestAttributeLimits(attributeCountLimit = 1),
                null,
                TestTimeoutExecutor(),
                NoopSpanProcessor,
            )

        spanImpl.attributes["system.attribute"] = "value"
        spanImpl.setAttribute("custom.attr.1", "test value")
        // a second "custom" attribute, that will need to be dropped (limit = 1)
        spanImpl.setAttribute("custom.attr.2", "another test value")
        spanImpl.setAttribute("custom.attr.1", "overwrite test value")
        // attempt to overwrite the second "custom" attribute, which will also be counted as dropped
        spanImpl.setAttribute("custom.attr.2", "not included")
        spanImpl.attributes["system.attribute2"] = "more values"

        assertEquals(2, spanImpl.droppedAttributesCount)
        assertEquals(5, spanImpl.attributes.size)

        assertEquals("value", spanImpl.attributes["system.attribute"])
        assertEquals("more values", spanImpl.attributes["system.attribute2"])
        assertEquals("overwrite test value", spanImpl.attributes["custom.attr.1"])
        assertEquals(1.0, spanImpl.attributes["bugsnag.sampling.p"] as Double, 0.001)
    }
}
