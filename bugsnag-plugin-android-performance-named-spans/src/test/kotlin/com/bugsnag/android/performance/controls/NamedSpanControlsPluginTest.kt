package com.bugsnag.android.performance.controls

import com.bugsnag.android.performance.test.TestSpanFactory
import com.bugsnag.android.performance.test.TestTimeoutExecutor
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class NamedSpanControlsPluginTest {
    private lateinit var timeoutExecutor: TestTimeoutExecutor
    private lateinit var controls: NamedSpanControlProvider
    private lateinit var spanFactory: TestSpanFactory

    private var timeoutCallbackCalled = false

    @Before
    fun setUp() {
        timeoutExecutor = TestTimeoutExecutor()
        spanFactory = TestSpanFactory()
        controls =
            NamedSpanControlProvider(timeoutExecutor, 10, TimeUnit.MINUTES) {
                timeoutCallbackCalled = true
            }

        timeoutCallbackCalled = false
    }

    @Test
    fun testLookupByName() {
        val spanName = "testSpan"
        val span = spanFactory.namedSpan(spanName)
        controls.onSpanStart(span)

        val foundSpan = controls[NamedSpanQuery(spanName)]
        assertSame(span, foundSpan)
        val notFoundSpan = controls[NamedSpanQuery("nonExistentSpan")]
        assertNull(notFoundSpan)

        controls.onSpanEnd(span)
        val afterEndSpan = controls[NamedSpanQuery(spanName)]
        assertNull(afterEndSpan)
    }

    @Test
    fun testTimeout() {
        val spanName = "testSpan"
        val span = spanFactory.namedSpan(spanName)
        controls.onSpanStart(span)
        timeoutExecutor.runAllTimeouts()
        assertNull(controls[NamedSpanQuery(spanName)])
    }
}
