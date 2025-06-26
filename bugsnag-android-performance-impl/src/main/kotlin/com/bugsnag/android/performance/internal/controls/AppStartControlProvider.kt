package com.bugsnag.android.performance.internal.controls

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.controls.AppStartSpanControl
import com.bugsnag.android.performance.controls.SpanControlProvider
import com.bugsnag.android.performance.controls.SpanQuery
import com.bugsnag.android.performance.controls.SpanType
import com.bugsnag.android.performance.internal.AppStartTracker
import com.bugsnag.android.performance.internal.SpanImpl
import com.bugsnag.android.performance.internal.SpanTracker

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class AppStartControlProvider(
    private val spanTracker: SpanTracker,
) : SpanControlProvider<AppStartSpanControl> {
    override fun <Q : SpanQuery<AppStartSpanControl>> get(query: Q): AppStartSpanControl? {
        if (query == SpanType.AppStart) {
            return spanTracker[AppStartTracker.appStartToken]
                ?.let { AppStartSpanControlImpl(it) }
        }
        return null
    }

    private class AppStartSpanControlImpl(private val span: SpanImpl) : AppStartSpanControl {
        override fun setType(name: String?) {
            span.setAttribute("bugsnag.app_start.name", name)
            val terminator = span.name.indexOf(']')
            if (terminator < 0) return
            val spanNamePrefix = span.name.substring(0, terminator + 1)
            span.name = spanNamePrefix + name.orEmpty()
        }
    }
}
