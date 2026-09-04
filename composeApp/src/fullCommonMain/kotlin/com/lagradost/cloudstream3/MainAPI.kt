package com.lagradost.cloudstream3

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.EnumSet

val mapper: JsonMapper = JsonMapper.builder()
    .addModule(kotlinModule())
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .build()

abstract class MainAPI {
    open var name: String = "Unnamed"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open val hasMainPage: Boolean = false
    open val hasQuickSearch: Boolean = false
    open val hasDownloadSupport: Boolean = true
    open val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var vpnStatus: Int = 0
    open val mainPage: List<MainPageData> = emptyList()

    open suspend fun search(query: String): List<SearchResponse>? = null

    open suspend fun quickSearch(query: String): List<SearchResponse>? = search(query)

    open suspend fun load(url: String): LoadResponse? = null

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit = {}
    ): Boolean = false

    open suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? = null
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("//")) return "https:$url"
    if (url.startsWith("/")) return mainUrl.removeSuffix("/") + url
    return url
}

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url == null) return null
    return fixUrl(url)
}

fun mainPageOf(vararg elements: Pair<String, String>): List<MainPageData> =
    elements.map { MainPageData(name = it.second, data = it.first) }

fun MainAPI.newMovieSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    fix: Boolean = true,
    builder: MovieSearchResponse.() -> Unit = {}
): MovieSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    val response = MovieSearchResponse(
        name = name,
        url = fixedUrl,
        apiName = this.name,
        type = type,
    )
    response.builder()
    return response
}

fun MainAPI.newAnimeSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    builder: AnimeSearchResponse.() -> Unit = {}
): AnimeSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    val response = AnimeSearchResponse(
        name = name,
        url = fixedUrl,
        apiName = this.name,
        type = type,
    )
    response.builder()
    return response
}

fun MainAPI.newTvSeriesSearchResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    fix: Boolean = true,
    builder: TvSeriesSearchResponse.() -> Unit = {}
): TvSeriesSearchResponse {
    val fixedUrl = if (fix) fixUrl(url) else url
    val response = TvSeriesSearchResponse(
        name = name,
        url = fixedUrl,
        apiName = this.name,
        type = type,
    )
    response.builder()
    return response
}

suspend fun MainAPI.newMovieLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Movie,
    dataUrl: String = url,
    builder: suspend MovieLoadResponse.() -> Unit = {}
): MovieLoadResponse {
    val response = MovieLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        dataUrl = dataUrl,
    )
    response.builder()
    return response
}

suspend fun MainAPI.newTvSeriesLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.TvSeries,
    episodes: List<Episode> = emptyList(),
    builder: suspend TvSeriesLoadResponse.() -> Unit = {}
): TvSeriesLoadResponse {
    val response = TvSeriesLoadResponse(
        name = name,
        url = fixUrl(url),
        apiName = this.name,
        type = type,
        episodes = episodes,
    )
    response.builder()
    return response
}

suspend fun MainAPI.newAnimeLoadResponse(
    name: String,
    url: String,
    type: TvType = TvType.Anime,
    fix: Boolean = true,
    builder: suspend AnimeLoadResponse.() -> Unit = {}
): AnimeLoadResponse {
    val response = AnimeLoadResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        type = type,
    )
    response.builder()
    return response
}

fun MainAPI.newEpisode(
    data: Any,
    builder: Episode.() -> Unit = {}
): Episode {
    val episode = Episode(data = if (data is String) data else AppUtils.toJson(data))
    episode.builder()
    return episode
}

fun getQualityFromString(string: String?): SearchQuality? {
    if (string == null) return null
    val lower = string.lowercase()
    return when {
        lower.contains("4k") -> SearchQuality.FourK
        lower.contains("uhd") -> SearchQuality.UHD
        lower.contains("hdr") -> SearchQuality.HDR
        lower.contains("bluray") || lower.contains("blueray") -> SearchQuality.BlueRay
        lower.contains("hq") -> SearchQuality.HQ
        lower.contains("hd") || lower.contains("1080") || lower.contains("720") -> SearchQuality.HD
        lower.contains("sd") || lower.contains("480") || lower.contains("360") -> SearchQuality.SD
        lower.contains("cam") || lower.contains("ts") || lower.contains("telesync") -> SearchQuality.CAM
        lower.contains("webrip") || lower.contains("web-dl") || lower.contains("web") -> SearchQuality.WebRip
        else -> null
    }
}

fun base64Decode(string: String): String = String(base64DecodeArray(string))
fun base64DecodeArray(string: String): ByteArray = java.util.Base64.getDecoder().decode(string.trim().replace("\n", "").replace("\r", ""))
fun base64Encode(bytes: ByteArray): String = java.util.Base64.getEncoder().encodeToString(bytes)
fun base64Encode(string: String): String = java.util.Base64.getEncoder().encodeToString(string.toByteArray())

fun AnimeLoadResponse.addEpisodes(status: DubStatus, episodes: List<Episode>) {
    this.episodes = this.episodes + (status to episodes)
}

fun Episode.addDate(date: String?, format: String? = null) {
    this.date = date
}

fun Episode.addPoster(url: String?) {
    this.posterUrl = url
}
