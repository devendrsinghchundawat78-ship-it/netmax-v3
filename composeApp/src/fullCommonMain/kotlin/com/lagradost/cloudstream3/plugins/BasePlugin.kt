@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.plugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.api.Log
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PLUGIN_TAG = "PluginInstance"

abstract class BasePlugin {

    /**
     * MainAPI instances this plugin registered. The host reads this right after
     * calling load() to pick the provider out of a .cs3.
     */
    val registeredApis: List<MainAPI>
        get() = synchronized(_registeredApis) { _registeredApis.toList() }

    /** ExtractorApi instances registered through [registerExtractorAPI]. */
    val registeredExtractors: List<ExtractorApi>
        get() = synchronized(_registeredExtractors) { _registeredExtractors.toList() }

    private val _registeredApis = mutableListOf<MainAPI>()
    private val _registeredExtractors = mutableListOf<ExtractorApi>()

    /**
     * Used to register providers instances of MainAPI
     * @param element MainAPI provider you want to register
     */
    fun registerMainAPI(element: MainAPI) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) MainAPI")
        element.sourcePlugin = this.filename
        synchronized(_registeredApis) { _registeredApis.add(element) }
        APIHolder.allProviders.add(element)
        APIHolder.addPluginMapping(element)
    }

    /**
     * Used to register extractor instances of ExtractorApi
     * @param element ExtractorApi provider you want to register
     */
    fun registerExtractorAPI(element: ExtractorApi) {
        Log.i(PLUGIN_TAG, "Adding ${element.name} (${element.mainUrl}) ExtractorApi")
        element.sourcePlugin = this.filename
        synchronized(_registeredExtractors) { _registeredExtractors.add(element) }
        extractorApis.add(element)
    }

    /**
     * Called when your Plugin is being unloaded
     */
    @Throws(Throwable::class)
    open fun beforeUnload() {
    }

    /**
     * Called when your Plugin is loaded
     */
    @Throws(Throwable::class)
    open fun load() {
    }

    /** Full file path to the plugin. */
    @Deprecated(
        "Renamed to `filename` to follow conventions",
        replaceWith = ReplaceWith("filename"),
        level = DeprecationLevel.ERROR
    )
    var __filename: String?
        get() = filename
        set(value) {
            filename = value
        }
    var filename: String? = null

    @Serializable
    class Manifest {
        @JsonProperty("name") @SerialName("name")
        var name: String? = null

        @JsonProperty("pluginClassName") @SerialName("pluginClassName")
        var pluginClassName: String? = null

        @JsonProperty("requiresResources") @SerialName("requiresResources")
        var requiresResources: Boolean = false

        @JsonProperty("version") @SerialName("version")
        var version: Int? = null
    }
}
