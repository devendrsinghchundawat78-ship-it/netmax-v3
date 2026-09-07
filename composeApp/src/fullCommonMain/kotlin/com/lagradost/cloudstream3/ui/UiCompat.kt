@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.ui.settings.extensions

/** A repository as shown in the upstream settings screen; this app stores its own list. */
data class RepositoryData(
    val url: String,
    val description: String = "",
    var repositoryPassword: String? = null
)
