package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.extractorApis

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CloudstreamPlugin

open class BasePlugin {
    var activity: Any? = null
    var context: Any? = null
    var openSettings: ((Any) -> Unit)? = null

    val registeredApis = mutableListOf<MainAPI>()
    val registeredExtractors = mutableListOf<ExtractorApi>()

    open fun load() {}

    open fun load(context: Any) {
        this.context = context
        load()
    }

    fun registerMainAPI(api: MainAPI) {
        registeredApis.add(api)
    }

    fun registerExtractorAPI(extractor: ExtractorApi) {
        registeredExtractors.add(extractor)
        if (!extractorApis.contains(extractor)) {
            extractorApis.add(extractor)
        }
    }

    fun addExtractor(extractor: ExtractorApi) {
        registerExtractorAPI(extractor)
    }
}

// Backward compatibility alias
typealias Plugin = BasePlugin
