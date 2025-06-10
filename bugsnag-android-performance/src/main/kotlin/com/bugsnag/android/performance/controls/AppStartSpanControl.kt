package com.bugsnag.android.performance.controls

/**
 * Controls for the `AppStart` span, accessible using [BugsnagPerformance.getSpanControls] and [SpanType.AppStart].
 */
public interface AppStartSpanControl {

    /**
     * Set the type of app start being measured to allow better filtering. The `appStartType` string is used to
     * split the app start spans further than simple "Cold", "Warm" and "Hot" starts and can be used when
     * additional overhead is expected (for example when a updating a database schema, or downloading updated
     * content). This is typically a short descriptive constant such as `"First Run"` or `"Schema Upgrade"`.
     */
    public fun setType(appStartType: String?)

    /**
     * Clear the app start type that was previously set with [setType]. This is the same as calling `setType(null)`.
     */
    public fun clearType() {
        setType(null)
    }
}
