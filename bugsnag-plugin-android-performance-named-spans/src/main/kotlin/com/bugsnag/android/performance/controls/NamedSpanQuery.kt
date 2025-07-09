package com.bugsnag.android.performance.controls

import com.bugsnag.android.performance.BugsnagPerformance
import com.bugsnag.android.performance.Span

/**
 * A [SpanQuery] that retrieves a [Span] by its name from [BugsnagPerformance.getSpanControls].
 * This can only be used if the [NamedSpanControlsPlugin] has been installed in the
 * [startup configuration](com.bugsnag.android.performance.PerformanceConfiguration.addPlugin).
 *
 * This query can be used to access spans that have been started and not yet ended (or discarded
 * due to timeout).
 *
 * @see [NamedSpanControlsPlugin] for the implementation that provides this functionality.
 */
public data class NamedSpanQuery(val name: String) : SpanQuery<Span>
