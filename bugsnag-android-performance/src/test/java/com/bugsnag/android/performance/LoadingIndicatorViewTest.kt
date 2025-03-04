package com.bugsnag.android.performance.test

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.LoadingIndicatorView
import com.bugsnag.android.performance.ViewType
import com.bugsnag.android.performance.internal.SpanImpl
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

private const val VIEW_LOAD_EXTENDED_TIME = 100L

@RunWith(AndroidJUnit4::class)
class LoadingIndicatorViewTest {

    private val loadingIndicatorView: LoadingIndicatorView =
        LoadingIndicatorView(ApplicationProvider.getApplicationContext<Application>())

    private val viewLoadSpan: SpanImpl = BugsnagPerformance.startViewLoadSpan(
        ViewType.ACTIVITY,
        "TestView",
    ) as SpanImpl

    @Test
    fun testOnAttachedToWindow() {

        loadingIndicatorView.invoke<Unit>("onAttachedToWindow")

        viewLoadSpan.end()
        Assert.assertTrue("ViewLoad should have ended", viewLoadSpan.isEnded())

        Thread.sleep(VIEW_LOAD_EXTENDED_TIME)

        loadingIndicatorView.invoke<Unit>("onDetachedFromWindow")
        val duration: Long = durationOf(viewLoadSpan)

        Assert.assertTrue(
            "LoadingIndicatorView should have extended the ViewLoad ",
            duration >= VIEW_LOAD_EXTENDED_TIME,
        )
    }

    private fun durationOf(span: SpanImpl): Long {
        val endTime = span.endTime
        if (endTime <= 0) {
            return 0
        }
        return endTime - span.startTime
    }

    fun <V> Any.invoke(method: String, vararg args: Any?): V {
        val clazz = javaClass
        val m = clazz.declaredMethods.find { it.name == method }!!
        m.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return m.invoke(this, *args) as V
    }

}