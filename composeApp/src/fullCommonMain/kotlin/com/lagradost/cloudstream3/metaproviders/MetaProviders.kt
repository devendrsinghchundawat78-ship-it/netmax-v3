@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

/**
 * Providers use the upstream meta providers to borrow known good metadata. This
 * app ships its own TMDB/Trakt integration, so these host classes exist to keep
 * linking working and answer with nothing instead of crashing a scrape.
 */
open class TmdbProvider : MainAPI() {
    override var name = "Tmdb"
    override var mainUrl = "https://www.themoviedb.org"
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest) = null
    override suspend fun search(query: String): List<SearchResponse>? = null
}

open class TraktProvider : MainAPI() {
    override var name = "Trakt"
    override var mainUrl = "https://trakt.tv"
    override var lang = "en"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest) = null
    override suspend fun search(query: String): List<SearchResponse>? = null
}
