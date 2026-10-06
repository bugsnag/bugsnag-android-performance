package com.bugsnag.android.performance.apollo

import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.Module
import java.util.regex.Pattern

internal class ApolloModule : Module {
    override fun load(instrumentedAppState: InstrumentedAppState) {
        tracePropagationUrls = instrumentedAppState.tracePropagationUrls
    }

    override fun unload() {
        tracePropagationUrls = emptyList()
    }

    internal companion object {
        var tracePropagationUrls: Collection<Pattern> = emptyList()
    }
}
