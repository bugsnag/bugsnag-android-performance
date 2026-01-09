package com.bugsnag.android.performance

public object FragmentInstrumentation {
    /**
     * Enable or disable automatic Fragment instrumentation.
     *
     * By default, this is enabled. Disabling this will prevent automatic
     * creation of ViewLoad spans for Fragments.
     */
    @JvmStatic
    public var enabled: Boolean = true
}
