package com.bugsnag.android.performance

/**
 * Defined whether / how a component of your app (such as activity loading) should be auto
 * instrumented.
 *
 * @see PerformanceConfiguration.autoInstrumentActivities
 */
public enum class AutoInstrument {
    /**
     * No automatic instrumentation should be done.
     */
    OFF,

    /**
     * Only start spans automatically, leaving them to be closed manually. In the case of view load
     * spans this is useful when you want to close them after some asynchronous loading is
     * completed.
     *
     * @see BugsnagPerformance.endViewLoadSpan
     */
    START_ONLY,

    /**
     * Full auto instrumentation should be carried out, both opening and closing of the spans
     * for the given setting. This is the default setting.
     */
    FULL,
}
