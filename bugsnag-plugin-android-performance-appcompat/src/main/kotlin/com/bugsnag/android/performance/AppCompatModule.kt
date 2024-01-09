package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.Module

public class AppCompatModule : Module {
    private lateinit var instrumentedAppState: InstrumentedAppState
    private lateinit var fragmentActivityLifecycleCallbacks: FragmentActivityLifecycleCallbacks
    override fun load(instrumentedAppState: InstrumentedAppState) {
        this.instrumentedAppState = instrumentedAppState
        this.fragmentActivityLifecycleCallbacks = FragmentActivityLifecycleCallbacks(
            instrumentedAppState.spanTracker,
            instrumentedAppState.spanFactory,
            instrumentedAppState.autoInstrumentationCache,
        )

        instrumentedAppState.app.registerActivityLifecycleCallbacks(
            fragmentActivityLifecycleCallbacks,
        )
    }

    override fun unload() {
        instrumentedAppState.app.unregisterActivityLifecycleCallbacks(
            fragmentActivityLifecycleCallbacks,
        )
    }
}
