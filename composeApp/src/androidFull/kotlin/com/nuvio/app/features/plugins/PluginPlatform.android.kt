package com.nuvio.app.features.plugins

import android.content.Context
import android.content.SharedPreferences

internal object PluginStorage {
    private const val preferencesName = "nuvio_plugins"
    private const val pluginsStateKey = "plugins_state"
    private const val scraperCodeDirectoryName = "nuvio_plugin_scrapers"

    private var preferences: SharedPreferences? = null
    private var scraperCodeStore: PluginScraperCodeFileStore? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        scraperCodeStore = PluginScraperCodeFileStore(context.filesDir.resolve(scraperCodeDirectoryName))
        val targetDir = context.codeCacheDir ?: context.cacheDir
        com.nuvio.app.features.plugins.runtime.cs3.CloudstreamPluginLoader.init(targetDir, context.applicationContext)
    }

    private fun ensureInitialized() {
        if (preferences != null && scraperCodeStore != null) return
        val ctx = runCatching {
            val threadCls = Class.forName("android.app.ActivityThread")
            val method = threadCls.getMethod("currentApplication")
            (method.invoke(null) as? Context)?.applicationContext
        }.getOrNull()
        if (ctx != null) {
            initialize(ctx)
        }
    }

    fun loadState(profileId: Int): String? {
        ensureInitialized()
        return preferences?.getString("${pluginsStateKey}_$profileId", null)
    }

    fun saveState(profileId: Int, payload: String) {
        ensureInitialized()
        preferences
            ?.edit()
            ?.putString("${pluginsStateKey}_$profileId", payload)
            ?.apply()
    }

    fun hasScraperCode(profileId: Int, scraperId: String): Boolean {
        ensureInitialized()
        return scraperCodeStore?.contains(profileId, scraperId) == true
    }

    fun loadScraperCode(profileId: Int, scraperId: String): String? {
        ensureInitialized()
        return scraperCodeStore?.load(profileId, scraperId)
    }

    fun saveScraperCode(
        profileId: Int,
        scraperId: String,
        code: String,
        overwrite: Boolean,
    ): Boolean {
        ensureInitialized()
        return scraperCodeStore?.save(profileId, scraperId, code, overwrite) == true
    }

    fun loadScraperSettings(scraperId: String): String? {
        ensureInitialized()
        return preferences?.getString("settings_${scraperId}", null)
    }

    fun saveScraperSettings(scraperId: String, payload: String) {
        ensureInitialized()
        preferences
            ?.edit()
            ?.putString("settings_${scraperId}", payload)
            ?.apply()
    }
}

internal fun currentPluginPlatform(): String = "android"

internal fun currentEpochMillis(): Long = System.currentTimeMillis()
