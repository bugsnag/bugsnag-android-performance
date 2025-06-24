package com.bugsnag.android.performance.internal.plugins

import androidx.annotation.RestrictTo
import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Plugin
import com.bugsnag.android.performance.internal.processing.TimeoutExecutor

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PluginManager(
    private val plugins: Collection<Plugin>,
    private val timeoutExecutor: TimeoutExecutor,
) {
    private val installedPlugins = ArrayList<Plugin>()

    /**
     * The complete context after all plugins have been installed.
     * This is used to retrieve the final configuration. This is only valid after [installPlugins]
     * has returned.
     */
    public var completeContext: PluginContextImpl? = null
        private set

    public fun installPlugins(config: PerformanceConfiguration) {
        val mergedContexts = PluginContextImpl(config, timeoutExecutor)

        for (plugin in plugins) {
            if (plugin !in installedPlugins) {
                try {
                    val pluginContext = PluginContextImpl(config, timeoutExecutor)
                    plugin.install(pluginContext)

                    mergedContexts.mergeFrom(pluginContext)
                    installedPlugins.add(plugin)
                } catch (failure: Exception) {
                    Logger.w("Plugin ${plugin::class.java.name} failed to install", failure)
                }
            }
        }

        completeContext = mergedContexts
    }

    public fun startPlugins() {
        installedPlugins.forEach { plugin ->
            try {
                plugin.start()
            } catch (failure: Exception) {
                Logger.w("Plugin ${plugin::class.java.name} failed to start", failure)
            }
        }
    }
}
