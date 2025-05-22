package com.bugsnag.android.performance

import com.bugsnag.android.performance.controls.SpanControlProvider

/**
 * A plugin interface that provides a way to extend the functionality of the performance monitoring
 * library. Plugins are added to the library via the [PerformanceConfiguration.addPlugin]
 * method, and are called when the library is started.
 */
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
    public fun addOnSpanStartCallback(priority: Int = NORM_PRIORITY, callback: OnSpanStartCallback)

    /**
     * Add a [OnSpanStartCallback] to the list of callbacks that will be called when a span is
     * started. This is a convenience method that is the same as calling
     * [addOnSpanStartCallback] with the default priority of [NORM_PRIORITY].
     */
    public fun addOnSpanStartCallback(callback: OnSpanStartCallback) {
        addOnSpanStartCallback(NORM_PRIORITY, callback)
    }

    /**
     * Add a [OnSpanEndCallback] to the list of callbacks that will be called when a span is
     * ended. The priority of the callback determines the order in which it will be called,
     * with lower numbers being called first. The default priority is [NORM_PRIORITY] (which
     * is also the priority of the [OnSpanEndCallback] added in
     * [PerformanceConfiguration.addOnSpanEndCallback]).
     *
     * @see PerformanceConfiguration.addOnSpanEndCallback
     */
    public fun addOnSpanEndCallback(priority: Int = NORM_PRIORITY, callback: OnSpanEndCallback)

    /**
     * Add a [OnSpanEndCallback] to the list of callbacks that will be called when a span is
     * ended. This is a convenience method that is the same as calling
     * [addOnSpanEndCallback] with the default priority of [NORM_PRIORITY].
     */
    public fun addOnSpanEndCallback(callback: OnSpanEndCallback) {
        addOnSpanEndCallback(NORM_PRIORITY, callback)
    }

    /**
     * Add a [SpanControlProvider] to the list of providers that can be queried via
     * [BugsnagPerformance.getSpanControls]. The priority of the provider determines the order
     * in which it will be queried, with higher priorities being queried first.
     *
     * @see BugsnagPerformance.getSpanControls
     */
    public fun addSpanControlProvider(
        priority: Int = NORM_PRIORITY,
        provider: SpanControlProvider<*>
    )

    /**
     * Add a [SpanControlProvider] to the list of providers that can be queried via
     * [BugsnagPerformance.getSpanControls]. This is a convenience method that is the same as
     * calling [addSpanControlProvider] with the default priority of [NORM_PRIORITY].
     *
     * @see BugsnagPerformance.getSpanControls
     */
    public fun addSpanControlProvider(provider: SpanControlProvider<*>) {
        addSpanControlProvider(NORM_PRIORITY, provider)
    }

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

        /**
         * A priority value for actions that should be called after [NORM_PRIORITY] actions.
         */
        public const val LOW_PRIORITY: Int = 100_000
    }
}
