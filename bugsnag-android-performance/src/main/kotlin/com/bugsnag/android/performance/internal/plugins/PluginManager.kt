package com.bugsnag.android.performance.internal.plugins

import com.bugsnag.android.performance.Logger
import com.bugsnag.android.performance.PerformanceConfiguration
import com.bugsnag.android.performance.Plugin

internal class PluginManager(
    private val plugins: List<Plugin>,
) {
    private val installedPlugins = LinkedHashMap<Plugin, PluginContextImpl>()

    val installedPluginContexts: Collection<PluginContextImpl> get() = installedPlugins.values

    fun installPlugins(config: PerformanceConfiguration) {
        for (plugin in plugins) {
            if (plugin !in installedPlugins) {
                try {
                    val pluginContext = PluginContextImpl(config)
                    plugin.install(pluginContext)
                    installedPlugins[plugin] = pluginContext
                } catch (failure: Exception) {
                    Logger.w("Plugin ${plugin::class.java.name} failed to install", failure)
                }
            }
        }
    }

    fun startPlugins() {
        installedPlugins.keys.forEach { plugin ->
            try {
                plugin.start()
            } catch (failure: Exception) {
                Logger.w("Plugin ${plugin::class.java.name} failed to start", failure)
            }
        }
    }
}
