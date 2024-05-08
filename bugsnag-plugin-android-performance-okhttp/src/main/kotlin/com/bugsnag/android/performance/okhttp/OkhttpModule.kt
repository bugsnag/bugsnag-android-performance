package com.bugsnag.android.performance.okhttp

import com.bugsnag.android.performance.internal.InstrumentedAppState
import com.bugsnag.android.performance.internal.Module
import okhttp3.OkHttpClient
import java.util.regex.Pattern

internal class OkhttpModule : Module {
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
