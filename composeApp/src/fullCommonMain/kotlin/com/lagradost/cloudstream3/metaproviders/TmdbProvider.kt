package com.lagradost.cloudstream3.metaproviders

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType

open class TmdbProvider : MainAPI() {
    override var name = "TMDB"
    override var mainUrl = "https://api.themoviedb.org/3"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    open var useMetaLoadResponse = false
}
