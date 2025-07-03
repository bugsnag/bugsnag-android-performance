package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.test.NoopSpanProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppStartTest {
    @Test
    fun fullAppStartScenario() {
        val spans = SpanFactory(NoopSpanProcessor)

        spans.createAppStartSpan("Cold").use { appStart ->
            assertNull(appStart.attributes["bugsnag.view.type"])
            assertNull(appStart.attributes["bugsnag.app_start.first_view_name"])
            spans.createViewLoadSpan(ViewType.ACTIVITY, "DeepLinkedActivity").use {
                assertEquals(
                    "activity",
                    appStart.attributes["bugsnag.view.type"],
                )

                assertEquals(
                    "DeepLinkedActivity",
                    appStart.attributes["bugsnag.app_start.first_view_name"],
                )

                spans.createViewLoadSpan(ViewType.FRAGMENT, "IndexFragment").end()
            }
        }
    }
}
