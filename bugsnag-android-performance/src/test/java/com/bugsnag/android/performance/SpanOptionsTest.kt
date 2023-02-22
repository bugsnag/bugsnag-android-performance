package com.bugsnag.android.performance

import android.os.SystemClock
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.testSpanProcessor
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
        spanFactory = SpanFactory(testSpanProcessor)
    }

    @Test
    fun testEquals() {
        assertEquals(
            // doesn't actually change the output
            SpanOptions.defaults.makeCurrentContext(SpanOptions.defaults.makeContext),
            SpanOptions.defaults,
        )

        assertEquals(
            SpanOptions.defaults.makeCurrentContext(true),
            SpanOptions.defaults,
        )

        assertNotEquals(
            SpanOptions.defaults.makeCurrentContext(false),
            SpanOptions.defaults,
        )

        assertEquals(
            SpanOptions.defaults.setFirstClass(true),
            SpanOptions.defaults,
        )

        assertNotEquals(
            SpanOptions.defaults.setFirstClass(false),
            SpanOptions.defaults,
        )

        assertEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults.startTime(12345L),
        )

        assertNotEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults,
        )

        assertNotEquals(
            SpanOptions.defaults.startTime(12345L),
            SpanOptions.defaults.startTime(54321L),
        )

        assertEquals(
            SpanOptions.defaults.within(SpanContext.invalid),
            SpanOptions.defaults.within(SpanContext.invalid),
        )

        assertEquals(
            SpanOptions.defaults.setFirstClass(true),
            SpanOptions.defaults.setFirstClass(true),
        )

        assertEquals(
            SpanOptions.defaults.within(SpanContext.current),
            SpanOptions.defaults,
        )

        assertEquals(
            SpanOptions.defaults.within(SpanOptions.defaults.parentContext),
            SpanOptions.defaults,
        )
    }

    @Test
    fun defaultStartTime() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 192837465L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)
        assertEquals(expectedTime, SpanOptions.defaults.startTime)
    }

    @Test
    fun overrideStartTime() {
        val time = 876123L
        assertEquals(time, SpanOptions.defaults.startTime(time).startTime)
        // test multi overrides of the same value
        assertEquals(time, SpanOptions.defaults.startTime(0L).startTime(time).startTime)
    }

    @Test
    fun overrideIsFirstClass() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertFalse(SpanOptions.defaults.setFirstClass(false).isFirstClass)
        // test multi overrides of the same value
        assertTrue(SpanOptions.defaults.setFirstClass(false).setFirstClass(true).isFirstClass)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            assertTrue(rootSpan.attributes.contains("bugsnag.span.first_class" to true))
            spanFactory.createCustomSpan("child").use { childSpan ->
                assertTrue(childSpan.attributes.contains("bugsnag.span.first_class" to false))
                spanFactory.createCustomSpan("override", SpanOptions.defaults.setFirstClass(true)).use { overrideSpan ->
                    assertTrue(overrideSpan.attributes.contains("bugsnag.span.first_class" to true))
                }
            }
        }
    }

    @Test
    fun overrideParentContext() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertNull(SpanOptions.defaults.within(null).parentContext)
        // test multi overrides of the same value
        assertNull(SpanOptions.defaults.within(SpanContext.current).within(null).parentContext)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { rootSpan ->
            assertEquals(0L, rootSpan.parentSpanId)
            spanFactory.createCustomSpan("child").use { childSpan ->
                assertEquals(rootSpan.spanId, childSpan.parentSpanId)
                spanFactory.createCustomSpan("override", SpanOptions.defaults.within(null)).use { overrideSpan ->
                    assertEquals(0L, overrideSpan.parentSpanId)
                }
            }
        }
    }

    @Test
    fun overrideCurrentContext() = withStaticMock<SystemClock> { clock ->
        val expectedTime = 12345L
        clock.`when`<Long> { SystemClock.elapsedRealtimeNanos() }.doReturn(expectedTime)

        assertFalse(SpanOptions.defaults.makeCurrentContext(false).makeContext)
        // test multi overrides of the same value
        assertTrue(SpanOptions.defaults.makeCurrentContext(false).makeCurrentContext(true).isFirstClass)

        // test nested span creation
        spanFactory.createCustomSpan("parent").use { defaultSpan ->
            assertSame(SpanContext.current, defaultSpan)
            spanFactory.createCustomSpan("child", SpanOptions.defaults.makeCurrentContext(false)).use { overrideSpan ->
                assertNotSame(SpanContext.current, overrideSpan)
            }
        }
    }
}
