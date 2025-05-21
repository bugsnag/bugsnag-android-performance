package com.bugsnag.android.performance

public interface Plugin {
    /**
     * Called when the plugin is loaded. This is where you can set up any necessary resources or
     * configurations for the plugin. This is called synchronously as part of
     * [BugsnagPerformance.start] to configure any callbacks and hooks that the plugin needs to
     * perform its work.
     *
     * @param ctx The context in which the plugin is being loaded
     * @see start
     */
    public fun install(ctx: PluginContext) {}

    /**
     * Start the plugin. This is called after all plugins have been installed and is where you can
     * start any background tasks or other operations that the plugin needs to perform. This is
     * called asynchronously after [BugsnagPerformance.start] to allow the plugin to perform
     * any necessary work without blocking the main thread.
     */
    public fun start() {}
}

public interface PluginContext {
    /**
     * The user provided configuration for the performance monitoring library. Changes made by
     * the plugin to this configuration may be *ignored* by the library, so plugins should not
     * modify this configuration directly (instead making any changes via the [PluginContext]).
     */
    public val configuration: PerformanceConfiguration

    /**
     * Add a [OnSpanStartCallback] to the list of callbacks that will be called when a span is
     * started. The priority of the callback determines the order in which it will be called,
     * with lower numbers being called first. The default priority is [NORM_PRIORITY] (which
     * is also the priority of the [OnSpanStartCallback] added in
     * [PerformanceConfiguration.addOnSpanStartCallback]).
     *
     * @see PerformanceConfiguration.addOnSpanStartCallback
     */
    public fun addOnSpanStartCallback(priority: Int = NORM_PRIORITY, sb: OnSpanStartCallback)

    /**
     * Add a [OnSpanEndCallback] to the list of callbacks that will be called when a span is
     * ended. The priority of the callback determines the order in which it will be called,
     * with lower numbers being called first. The default priority is [NORM_PRIORITY] (which
     * is also the priority of the [OnSpanEndCallback] added in
     * [PerformanceConfiguration.addOnSpanEndCallback]).
     *
     * @see PerformanceConfiguration.addOnSpanEndCallback
     */
    public fun addOnSpanEndCallback(priority: Int = NORM_PRIORITY, sb: OnSpanEndCallback)

    public companion object {
        /**
         * A priority value for actions that should be called before [NORM_PRIORITY] actions.
         */
        public const val HIGH_PRIORITY: Int = 0

        /**
         * The "normal" priority value for actions that should be called in the order they are
         * added. This is the priority of items added in [PerformanceConfiguration]. Anything
         * with priority lower than this will be called before these, and priority values higher
         * than
         */
        public const val NORM_PRIORITY: Int = 10_000
        public const val LOW_PRIORITY: Int = 100_000
    }
}
