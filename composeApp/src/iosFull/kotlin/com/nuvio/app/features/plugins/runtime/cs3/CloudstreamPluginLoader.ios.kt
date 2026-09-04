package com.nuvio.app.features.plugins.runtime.cs3

import com.lagradost.cloudstream3.MainAPI

actual object CloudstreamPluginLoader {
    actual suspend fun loadApi(scraperId: String, cs3Data: ByteArray): MainAPI? = null
    actual fun clearCache() {}
}
