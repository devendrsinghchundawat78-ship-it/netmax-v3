@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

/**
 * Upstream reads plugin lists straight out of the repository json. This app owns
 * that flow (PluginRepository), so only the read side is implemented here and the
 * repository list stays empty for provider code.
 */
object RepositoryManager {
    const val BASE_URL = "https://recloudstream.github.io/repos"
    const val DEFAULT_REPOS = BASE_URL

    private val customRepositories = mutableListOf<RepositoryData>()

    fun getRepositories(): Array<RepositoryData> =
        synchronized(customRepositories) { customRepositories.toTypedArray() }

    fun addDefinedRepo(index: Int, repoType: Int, url: String?, description: String? = null) {
        if (url.isNullOrBlank()) return
        synchronized(customRepositories) {
            if (customRepositories.none { it.url == url }) {
                customRepositories.add(RepositoryData(url, description ?: ""))
            }
        }
    }

    suspend fun getRepoPlugins(repositoryData: RepositoryData?): String? {
        val url = repositoryData?.url ?: return null
        return runCatching { app.get(url).text }.getOrNull()
    }
}
