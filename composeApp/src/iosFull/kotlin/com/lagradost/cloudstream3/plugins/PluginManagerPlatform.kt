package com.lagradost.cloudstream3.plugins

import com.lagradost.api.Log

/** iOS twin: this platform cannot dex load, so only the read side exists. */
object PluginManager {
    private const val TAG = "PluginManager"

    fun getPluginsOnline(): Array<PluginData> = emptyArray()

    fun getPlugins(): Map<String, PluginWrapper> = emptyMap()

    suspend fun loadSinglePlugin(context: Any?, internalName: String): Boolean = false

    fun unloadPlugin(internalName: String) {
        Log.i(TAG, "unloadPlugin($internalName) is a no-op on this platform")
    }
}
