@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

/** Mirrors the upstream wrapper a provider walks when it looks at sibling plugins. */
class PluginWrapper(
    val plugin: SitePlugin? = null,
    val repositoryData: RepositoryData? = null,
    val fileName: String? = null
)
