@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.plugins

/**
 * Upstream's settings-screen plugin entry. Only the fields providers read are
 * meaningful here; the UI itself lives in this app's plugin screen.
 */
data class SitePlugin(
    val filePath: String?,
    val status: Int,
    val version: Int?,
    val apiVersion: Int?,
    val name: String?,
    val internalName: String?,
    val authors: List<String>?,
    val fileName: String?,
    val repositoryUrl: String?,
    val shortDescription: String?,
    val iconUrl: String?,
    val fileSize: Long?,
    val fileHash: String?,
) {
    companion object {
        const val PROVIDER_STATUS_OK = 1
        const val PROVIDER_STATUS_OUTDATED = 2
        const val PROVIDER_STATUS_FAILED = 4
        const val PROVIDER_STATUS_DOWNLOADED = 8
    }

    val url: String?
        get() = repositoryUrl

    fun copyInternalName(): String = internalName ?: ""
}
