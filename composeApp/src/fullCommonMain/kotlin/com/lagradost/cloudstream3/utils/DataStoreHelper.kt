@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.utils

/**
 * Providers use the host "continue watching" entries to deep link into a show.
 * This app keeps that state in its own database, so the shapes exist for linking
 * and the host side simply reports nothing.
 */
object DataStoreHelper {
    data class ResumeWatchingResult(
        val id: Int?,
        val parentId: Int?,
        val title: String,
        val duration: Int,
        val position: Int,
        val parentTitle: String? = null,
        val posterUrl: String? = null,
        val epId: Int? = null
    )

    fun getResumeWatching(): List<ResumeWatchingResult> = emptyList()
}
