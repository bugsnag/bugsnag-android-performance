package com.bugsnag.android.performance

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.bugsnag.android.performance.internal.AutoInstrumentationCache
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FragmentActivityLifecycleCallbacksTest {
    private lateinit var spanCollector: MutableList<SpanImpl>

    private lateinit var spanTracker: SpanTracker

    private lateinit var lifecycleCallbacks: FragmentActivityLifecycleCallbacks

    private lateinit var fm: FragmentManager
    private lateinit var fragment1: Fragment
    private lateinit var fragment2: Fragment
    private lateinit var autoInstrumentationCache: AutoInstrumentationCache

    @Before
    fun setup() {
        spanCollector = ArrayList()
        spanTracker = SpanTracker()
        autoInstrumentationCache = AutoInstrumentationCache()
        lifecycleCallbacks =
            FragmentActivityLifecycleCallbacks(
                spanTracker,
                SpanFactory({ spanCollector.add(it as SpanImpl) }),
                autoInstrumentationCache,
            )

        fm = object : FragmentManager() {}
        fragment1 =
            mock {
                whenever(it.isAdded) doReturn true
            }
        fragment2 =
            mock {
                whenever(it.isAdded) doReturn true
            }
    }

    @After
    fun shutdown() {
        SpanContext.defaultStorage?.clear()
    }

    /**
     * Test that when two fragments are being started interleaved within the same transaction,
     * the SpanContext is what we expect it to be
     */
    @Test
    @Suppress("DestructuringDeclarationWithTooManyEntries")
    fun testInterleavedLifecycles() {
        lifecycleCallbacks.onFragmentPreCreated(fm, fragment1, null)
        assertNotEquals(
            "Fragment.onCreate should have a valid SpanContext",
            SpanContext.invalid,
            SpanContext.current,
        )
        lifecycleCallbacks.onFragmentCreated(fm, fragment1, null)

        lifecycleCallbacks.onFragmentPreCreated(fm, fragment2, null)
        assertNotEquals(
            "Fragment.onCreate should have a valid SpanContext",
            SpanContext.invalid,
            SpanContext.current,
        )
        lifecycleCallbacks.onFragmentCreated(fm, fragment2, null)

        assertEquals(
            "Once completed Fragment.onCreate should leave no SpanContext",
            SpanContext.invalid,
            SpanContext.current,
        )

        lifecycleCallbacks.onFragmentStarted(fm, fragment1)
        lifecycleCallbacks.onFragmentStarted(fm, fragment2)

        lifecycleCallbacks.onFragmentResumed(fm, fragment1)
        lifecycleCallbacks.onFragmentResumed(fm, fragment2)

        assertEquals(4, spanCollector.size)

        val (f1Create, f2Create, f1ViewLoad, f2ViewLoad) = spanCollector
        assertEquals(f1ViewLoad.spanId, f1Create.parentSpanId)
        assertEquals(f2ViewLoad.spanId, f2Create.parentSpanId)

        // 0 is invalid SpanId, ViewLoads in this test should not be nested
        assertEquals(0L, f1ViewLoad.parentSpanId)
        assertEquals(0L, f2ViewLoad.parentSpanId)
    }

    @Test
    fun testDoNotAutoInstrumentFragment() {
        @DoNotAutoInstrument
        class DoNotAutoInstrumentFragment : Fragment()

        val doNotAutoInstrumentFragment = DoNotAutoInstrumentFragment()
        lifecycleCallbacks.onFragmentPreCreated(fm, doNotAutoInstrumentFragment, null)

        assertEquals(0, spanCollector.size)

        lifecycleCallbacks.onFragmentCreated(fm, doNotAutoInstrumentFragment, null)
        lifecycleCallbacks.onFragmentStarted(fm, doNotAutoInstrumentFragment)
        lifecycleCallbacks.onFragmentResumed(fm, doNotAutoInstrumentFragment)

        assertEquals(0, spanCollector.size)
    }
}
