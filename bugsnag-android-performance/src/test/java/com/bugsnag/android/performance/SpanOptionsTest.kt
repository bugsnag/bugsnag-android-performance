package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.NoopSpanProcessor
import com.bugsnag.android.performance.test.withStaticMock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn

class SpanOptionsTest {
    private lateinit var spanFactory: SpanFactory

    @Before
    fun newSpanFactory() {
        spanFactory = SpanFactory(NoopSpanProcessor)
        SpanContext.DEFAULT_STORAGE?.clear()
    }

    @Test
    fun testEquals() {
        assertEquals(
            // doesn't actually change the output
            SpanOptions.makeCurrentContext(SpanOptions.DEFAULTS.makeContext),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.makeCurrentContext(true),
            SpanOptions.DEFAULTS,
        )

        assertNotEquals(
            SpanOptions.makeCurrentContext(false),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.startTime(12345L),
            SpanOptions.startTime(12345L),
        )

        assertNotEquals(
            SpanOptions.startTime(12345L),
            SpanOptions.DEFAULTS,
        )

        assertNotEquals(
            SpanOptions.startTime(12345L),
            SpanOptions.startTime(54321L),
        )

        assertEquals(
            SpanOptions.within(SpanContext.invalid),
            SpanOptions.within(SpanContext.invalid),
        )

        assertEquals(
            SpanOptions.setFirstClass(true),
            SpanOptions.setFirstClass(true),
        )

        assertEquals(
            SpanOptions.within(SpanContext.current),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.within(SpanOptions.DEFAULTS.parentContext),
            SpanOptions.DEFAULTS,
        )
    }

    @Test
    fun defaultStartTime() =
        withStaticMock<SystemClock> { clock ->
            val expectedTime = 192837465L
            clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)
            assertEquals(expectedTime, SpanOptions.DEFAULTS.startTime)
        }

    @Test
    fun overrideStartTime() {
        val time = 876123L
        assertEquals(time, SpanOptions.startTime(time).startTime)
        // test multi overrides of the same value
        assertEquals(time, SpanOptions.startTime(0L).startTime(time).startTime)
    }

    @Test
    fun overrideIsFirstClass() =
        withStaticMock<SystemClock> { clock ->
            val expectedTime = 12345L
            clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

            assertEquals(false, SpanOptions.setFirstClass(false).isFirstClass)
            // test multi overrides of the same value
            assertEquals(
                true,
                SpanOptions.setFirstClass(false).setFirstClass(true).isFirstClass,
            )

            // test nested span creation
            spanFactory.createCustomSpan("parent").use { rootSpan ->
                assertEquals(true, rootSpan.attributes["bugsnag.span.first_class"])
                spanFactory.createCustomSpan("child").use { childSpan ->
                    // All Custom spans are first_class
                    assertEquals(true, childSpan.attributes["bugsnag.span.first_class"])
                    spanFactory.createCustomSpan("override", SpanOptions.setFirstClass(true))
                        .use { overrideSpan ->
                            assertEquals(true, overrideSpan.attributes["bugsnag.span.first_class"])
                        }
                }
            }
        }

    @Test
    fun overrideParentContext() =
        withStaticMock<SystemClock> { clock ->
            val expectedTime = 12345L
            clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

            assertNull(SpanOptions.within(null).parentContext)
            // test multi overrides of the same value
            assertNull(SpanOptions.within(SpanContext.current).within(null).parentContext)

            // test nested span creation
            spanFactory.createCustomSpan("parent").use { rootSpan ->
                assertEquals(0L, rootSpan.parentSpanId)
                spanFactory.createCustomSpan("child").use { childSpan ->
                    assertEquals(rootSpan.spanId, childSpan.parentSpanId)
                    spanFactory.createCustomSpan("override", SpanOptions.within(null))
                        .use { overrideSpan ->
                            assertEquals(0L, overrideSpan.parentSpanId)
                        }
                }
            }
        }

    @Test
    fun overrideCurrentContext() =
        withStaticMock<SystemClock> { clock ->
            val expectedTime = 12345L
            clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

            assertFalse(SpanOptions.makeCurrentContext(false).makeContext)
            // test multi overrides of the same value
            assertTrue(
                SpanOptions.makeCurrentContext(false).makeCurrentContext(true).makeContext,
            )

            // test nested span creation
            spanFactory.createCustomSpan("parent").use { defaultSpan ->
                assertSame(SpanContext.current, defaultSpan)
                spanFactory.createCustomSpan("child", SpanOptions.makeCurrentContext(false))
                    .use { overrideSpan ->
                        assertNotSame(SpanContext.current, overrideSpan)
                    }
            }
        }
}
