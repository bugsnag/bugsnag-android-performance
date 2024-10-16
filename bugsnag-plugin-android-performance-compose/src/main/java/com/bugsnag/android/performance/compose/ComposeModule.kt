package com.bugsnag.android.performance.compose

import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.Module

public class ComposeModule : Module {
    private lateinit var instrumentedAppState: InstrumentedAppState

    override fun load(instrumentedAppState: InstrumentedAppState) {
        this.instrumentedAppState = instrumentedAppState
        this.instrumentedAppState.app.registerActivityLifecycleCallbacks(
            ComposeActivityLifecycleCallbacks,
        )
    }

    override fun unload() {
        instrumentedAppState.app.unregisterActivityLifecycleCallbacks(
            ComposeActivityLifecycleCallbacks,
        )
    }
}
