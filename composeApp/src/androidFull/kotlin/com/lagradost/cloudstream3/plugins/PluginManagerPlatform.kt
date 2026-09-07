package com.lagradost.cloudstream3.plugins

import android.content.Context
import com.lagradost.api.Log
import java.io.File

/**
 * Providers inspect / unload host plugins through this. This app owns plugin
 * storage in its own PluginRepository, so the manager reports what the
 * CloudStream loader actually loaded and never installs or unloads anything
 * from here. Lives on the Android source set because the paths are Context
 * keyed.
 */
object PluginManager {
    private const val TAG = "PluginManager"

    private val onlinePlugins = mutableListOf<PluginData>()

    fun getPluginsOnline(): Array<PluginData> =
        synchronized(onlinePlugins) { onlinePlugins.toTypedArray() }

    fun getPlugins(): Map<String, PluginWrapper> =
        getPluginsOnline().associate { data ->
            data.internalName to PluginWrapper(
                plugin = data.toSitePlugin(),
                repositoryData = data.url?.let {
                    com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData(it)
                }
            )
        }

    fun getPluginPath(context: Context?, internalName: String, repositoryUrl: String?): File? {
        val ctx = context ?: return null
        val bucket = (repositoryUrl ?: "local").hashCode().toString()
        return File(File(ctx.filesDir, "plugin_$bucket"), "$internalName.cs3")
    }

    suspend fun loadSinglePlugin(context: Context?, internalName: String): Boolean {
        // The host dex loads a .cs3 while resolving streams, so nothing to do here.
        return getPluginsOnline().any { it.internalName == internalName }
    }

    fun unloadPlugin(internalName: String) {
        Log.i(TAG, "unloadPlugin($internalName) is a no-op in this build")
    }

    /** Called by the host loader once a .cs3 has been dex loaded. */
    fun registerOnline(data: PluginData) {
        synchronized(onlinePlugins) {
            if (onlinePlugins.none { it.internalName == data.internalName }) onlinePlugins.add(data)
        }
    }
}
