package com.bugsnag.android.performance.controls

/**
 * Queries for the various spans that have specialised controls available
 * via [BugsnagPerformance.getSpanControls].
 */
public sealed interface SpanType<C> : SpanQuery<C> {

    /**
     * Query used to retrieve the [AppStartSpanControl] for the currently open `AppStart` span
     * via [BugsnagPerformance.getSpanControls].
     * This query is only valid while the `AppStart` span is open and
     * the controls are unavailable (`null`) once the app is considered started.
     */
    public object AppStart : SpanType<AppStartSpanControl>
}
