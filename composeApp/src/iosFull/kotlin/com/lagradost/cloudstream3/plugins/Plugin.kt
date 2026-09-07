@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.plugins

abstract class Plugin : BasePlugin() {
    var url: String? = null
    var repoUrl: String? = null

    var resources: Any? = null
}
