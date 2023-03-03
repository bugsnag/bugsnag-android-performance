package com.bugsnag.android.performance

import androidx.fragment.app.Fragment
import com.bugsnag.android.performance.internal.SpanImpl

private fun spanNameForFragment(fragment: Fragment): String {
    return fragment.javaClass.simpleName
}

/**
 * Open a ViewLoad span to measure the time taken to load a fragment. This function can be used
 * when the automated instrumentation is not well suited to your app.
 *
 * @param fragment the fragment load being measured
 * @param options the optional configuration for the span
 */
fun BugsnagPerformance.startViewLoadSpan(
    fragment: Fragment,
    options: SpanOptions = SpanOptions.DEFAULTS,
): Span {
    val span = startViewLoadSpan(
        ViewType.FRAGMENT,
        spanNameForFragment(fragment),
        options,
    )

    val tag = fragment.tag
    if (tag != null && span is SpanImpl) {
        span.setAttribute("bugsnag.view.fragment_tag", tag)
    }

    return span
}
