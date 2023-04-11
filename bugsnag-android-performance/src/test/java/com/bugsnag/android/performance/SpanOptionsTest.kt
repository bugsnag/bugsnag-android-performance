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
    }

    @Test
    fun testEquals() {
        assertEquals(
            // doesn't actually change the output
            SpanOptions.DEFAULTS.makeCurrentContext(SpanOptions.DEFAULTS.makeContext),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.DEFAULTS.makeCurrentContext(true),
            SpanOptions.DEFAULTS,
        )

        assertNotEquals(
            SpanOptions.DEFAULTS.makeCurrentContext(false),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.DEFAULTS.setFirstClass(true),
            SpanOptions.DEFAULTS,
        )

        assertNotEquals(
            SpanOptions.DEFAULTS.setFirstClass(false),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.DEFAULTS.startTime(12345L),
            SpanOptions.DEFAULTS.startTime(12345L),
        )

        assertNotEquals(
            SpanOptions.DEFAULTS.startTime(12345L),
            SpanOptions.DEFAULTS,
        )

        assertNotEquals(
            SpanOptions.DEFAULTS.startTime(12345L),
            SpanOptions.DEFAULTS.startTime(54321L),
        )

        assertEquals(
            SpanOptions.DEFAULTS.within(SpanContext.invalid),
            SpanOptions.DEFAULTS.within(SpanContext.invalid),
        )

        assertEquals(
            SpanOptions.DEFAULTS.setFirstClass(true),
            SpanOptions.DEFAULTS.setFirstClass(true),
        )

        assertEquals(
            SpanOptions.DEFAULTS.within(SpanContext.current),
            SpanOptions.DEFAULTS,
        )

        assertEquals(
            SpanOptions.DEFAULTS.within(SpanOptions.DEFAULTS.parentContext),
            SpanOptions.DEFAULTS,
        )
    }

    @Test
    fun defaultStartTime() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 192837465L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)
        assertEquals(expectedTime, SpanOptions.DEFAULTS.startTime)
    }

    @Test
    fun overrideStartTime() {
        val time = 876123L
        assertEquals(time, SpanOptions.DEFAULTS.startTime(time).startTime)
        // test multi overrides of the same value
        assertEquals(time, SpanOptions.DEFAULTS.startTime(0L).startTime(time).startTime)
    }

    @Test
    fun overrideIsFirstClass() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertFalse(SpanOptions.DEFAULTS.setFirstClass(false).isFirstClass)
        // test multi overrides of the same value
        assertTrue(SpanOptions.DEFAULTS.setFirstClass(false).setFirstClass(true).isFirstClass)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            assertTrue(rootSpan.attributes.contains("bugsnag.span.first_class" to true))
            spanFactory.createCustomSpan("child").use { childSpan ->
                assertTrue(childSpan.attributes.contains("bugsnag.span.first_class" to false))
                spanFactory.createCustomSpan("override", SpanOptions.DEFAULTS.setFirstClass(true)).use { overrideSpan ->
                    assertTrue(overrideSpan.attributes.contains("bugsnag.span.first_class" to true))
                }
            }
        }
    }

    @Test
    fun overrideParentContext() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertNull(SpanOptions.DEFAULTS.within(null).parentContext)
        // test multi overrides of the same value
        assertNull(SpanOptions.DEFAULTS.within(SpanContext.current).within(null).parentContext)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            assertEquals(0L, rootSpan.parentSpanId)
            spanFactory.createCustomSpan("child").use { childSpan ->
                assertEquals(rootSpan.spanId, childSpan.parentSpanId)
                spanFactory.createCustomSpan("override", SpanOptions.DEFAULTS.within(null)).use { overrideSpan ->
                    assertEquals(0L, overrideSpan.parentSpanId)
                }
            }
        }
    }

    @Test
    fun overrideCurrentContext() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertFalse(SpanOptions.DEFAULTS.makeCurrentContext(false).makeContext)
        // test multi overrides of the same value
        assertTrue(SpanOptions.DEFAULTS.makeCurrentContext(false).makeCurrentContext(true).isFirstClass)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { defaultSpan ->
            assertSame(SpanContext.current, defaultSpan)
            spanFactory.createCustomSpan("child", SpanOptions.DEFAULTS.makeCurrentContext(false)).use { overrideSpan ->
                assertNotSame(SpanContext.current, overrideSpan)
            }
        }
    }
}
