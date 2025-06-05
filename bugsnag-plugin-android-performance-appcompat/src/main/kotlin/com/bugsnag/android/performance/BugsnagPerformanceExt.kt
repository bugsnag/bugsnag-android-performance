package com.bugsnag.android.performance

import androidx.fragment.app.Fragment
import com.bugsnag.android.performance.internal.BugsnagPerformanceInternals
import com.bugsnag.android.performance.internal.SpanFactory
import com.bugsnag.android.performance.internal.SpanImpl

internal fun viewNameForFragment(fragment: Fragment): String {
    return fragment.javaClass.simpleName
}

internal fun SpanFactory.createViewLoadSpan(
    fragment: Fragment,
    options: SpanOptions = SpanOptions.DEFAULTS,
): SpanImpl {
    val span =
        createViewLoadSpan(
            ViewType.FRAGMENT,
            viewNameForFragment(fragment),
            options,
        )

    val tag = fragment.tag
    if (tag != null) {
        span.attributes["bugsnag.view.fragment_tag"] = tag
    }

    return span
}

/**
 * Open a ViewLoad span to measure the time taken to load a fragment. This function can be used
 * when the automated instrumentation is not well suited to your app.
 *
 * @param fragment the fragment load being measured
 * @param options the optional configuration for the span
 */
public fun BugsnagPerformance.startViewLoadSpan(
    fragment: Fragment,
    options: SpanOptions = SpanOptions.DEFAULTS,
): Span {
    return BugsnagPerformanceInternals.spanFactory.createViewLoadSpan(
        fragment,
        options,
    )
}
