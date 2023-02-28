package com.bugsnag.android.performance

import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.Module

class AppCompatModule : Module {
    private lateinit var instrumentedAppState: InstrumentedAppState
    private lateinit var fragmentActivityLifecycleCallbacks: FragmentActivityLifecycleCallbacks
    override fun load(instrumentedAppState: InstrumentedAppState) {
        this.instrumentedAppState = instrumentedAppState
        this.fragmentActivityLifecycleCallbacks =
            FragmentActivityLifecycleCallbacks(instrumentedAppState.spanTracker)

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
