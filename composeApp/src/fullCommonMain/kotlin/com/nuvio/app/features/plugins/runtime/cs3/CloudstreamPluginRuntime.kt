package com.nuvio.app.features.plugins.runtime.cs3

import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.Qualities
import com.nuvio.app.features.details.MetaDetailsRepository
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginSubtitleResult
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Base64
import kotlin.math.abs

private const val CS3_PLUGIN_TIMEOUT_MS = 60_000L

object CloudstreamPluginRuntime {
    private val log = Logger.withTag("CS3PluginRuntime")

    suspend fun executePlugin(
        code: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
    ): List<PluginRuntimeResult> = withContext(Dispatchers.Default) {
        withTimeout(CS3_PLUGIN_TIMEOUT_MS) {
            executePluginInternal(
                code = code,
                tmdbId = tmdbId,
                mediaType = mediaType,
                season = season,
                episode = episode,
                scraperId = scraperId,
            )
        }
    }

    private suspend fun executePluginInternal(
        code: String,
        tmdbId: String,
        mediaType: String,
        season: Int?,
        episode: Int?,
        scraperId: String,
    ): List<PluginRuntimeResult> {
        val cs3Bytes = runCatching {
            val cleaned = code.filterNot { it.isWhitespace() }
            Base64.getDecoder().decode(cleaned)
        }.recoverCatching {
            Base64.getMimeDecoder().decode(code)
        }.getOrElse {
            code.toByteArray(Charsets.ISO_8859_1)
        }

        val api = CloudstreamPluginLoader.loadApi(scraperId, cs3Bytes)
        if (api == null) {
            log.w { "Failed to get MainAPI for scraper $scraperId" }
            return emptyList()
        }

        val isTmdbProvider = api is TmdbProvider ||
            api.mainUrl.contains("themoviedb.org", ignoreCase = true) ||
            api.name.equals("SoraStream", ignoreCase = true) ||
            api.name.equals("SoraStreamLite", ignoreCase = true) ||
            api.name.equals("SuperStream", ignoreCase = true)

        val activeMeta = MetaDetailsRepository.uiState.value.meta
        val metaName = activeMeta?.name?.takeIf { it.isNotBlank() }
        val metaYear = activeMeta?.releaseInfo?.take(4)?.toIntOrNull()
            ?: activeMeta?.lastAirDate?.take(4)?.toIntOrNull()

        val numericTmdbId = tmdbId.toIntOrNull()
            ?: TmdbService.ensureTmdbId(tmdbId, mediaType)?.toIntOrNull()

        val imdbId: String? = activeMeta?.id?.takeIf { it.startsWith("tt", ignoreCase = true) }
            ?: if (tmdbId.startsWith("tt", ignoreCase = true)) tmdbId
            else numericTmdbId?.let { runCatching { TmdbService.tmdbToImdb(it, mediaType) }.getOrNull() }

        val (tmdbTitle, tmdbYear) = if (numericTmdbId != null) {
            runCatching {
                TmdbService.fetchMediaTitleAndYear(
                    tmdbId = numericTmdbId.toString(),
                    mediaType = mediaType,
                )
            }.getOrNull() ?: (null to null)
        } else (null to null)

        val releaseYear = metaYear ?: tmdbYear
        val cleanTitle = tmdbTitle ?: metaName ?: ""
        val tmdbMediaType = if (season != null || episode != null || mediaType == "tv") "tv" else "movie"

        val synthesizedLinkData = buildLinkDataJson(
            id = numericTmdbId,
            imdbId = imdbId,
            title = cleanTitle,
            year = releaseYear,
            season = season,
            episode = episode,
            type = tmdbMediaType,
        )

        var loadResponse: LoadResponse? = null

        // 1. Direct TMDB load path for TMDB-based providers (bypasses flaky title search)
        if (isTmdbProvider && numericTmdbId != null) {
            val directPayload = """{"id":$numericTmdbId,"type":"$tmdbMediaType"}"""
            log.d { "Direct TMDB load on ${api.name} with: $directPayload" }
            loadResponse = runCatching { api.load(directPayload) }.getOrNull()

            if (loadResponse == null) {
                val directUrl = "https://api.themoviedb.org/3/$tmdbMediaType/$numericTmdbId"
                loadResponse = runCatching { api.load(directUrl) }.getOrNull()
            }
        }

        // 2. Search path (for direct site scrapers or if direct TMDB load didn't resolve)
        if (loadResponse == null) {
            val searchCandidates = listOfNotNull(
                cleanTitle.takeIf { it.isNotBlank() },
                metaName?.takeIf { it.isNotBlank() && it != cleanTitle },
                tmdbTitle?.takeIf { it.isNotBlank() && it != cleanTitle },
            ).distinct()

            if (searchCandidates.isEmpty()) {
                log.w { "No valid title available to search on ${api.name}" }
            } else {
                var searchResults: List<SearchResponse> = emptyList()
                var usedQuery = searchCandidates.first()

                for (candidate in searchCandidates) {
                    log.d { "Searching ${api.name} for '$candidate'" }
                    searchResults = runCatching { api.search(candidate) }.getOrNull().orEmpty()
                    if (searchResults.isEmpty()) {
                        searchResults = runCatching { api.quickSearch(candidate) }.getOrNull().orEmpty()
                    }
                    if (searchResults.isNotEmpty()) {
                        usedQuery = candidate
                        break
                    }

                    // Retry with sanitized alphanumeric title
                    val sanitized = sanitizeSearchQuery(candidate)
                    if (sanitized.isNotBlank() && !sanitized.equals(candidate, ignoreCase = true)) {
                        log.d { "Retrying search on ${api.name} with sanitized query '$sanitized'" }
                        searchResults = runCatching { api.search(sanitized) }.getOrNull().orEmpty()
                        if (searchResults.isEmpty()) {
                            searchResults = runCatching { api.quickSearch(sanitized) }.getOrNull().orEmpty()
                        }
                        if (searchResults.isNotEmpty()) {
                            usedQuery = sanitized
                            break
                        }
                    }
                }

                if (searchResults.isNotEmpty()) {
                    val matchedResult = findBestMatch(
                        results = searchResults,
                        query = usedQuery,
                        year = releaseYear,
                    ) ?: searchResults.first()

                    log.d { "Selected match on ${api.name}: '${matchedResult.name}' (${matchedResult.url})" }
                    loadResponse = runCatching { api.load(matchedResult.url) }.getOrNull()
                }
            }
        }

        // 3. Resolve episodeData
        var episodeData: String? = when (loadResponse) {
            is AnimeLoadResponse -> {
                val allEpisodes = loadResponse.episodes.values.flatten()
                val targetEp = if (episode != null) {
                    allEpisodes.firstOrNull { it.episode == episode }
                } else null
                targetEp?.data ?: allEpisodes.firstOrNull()?.data ?: loadResponse.url
            }
            is TvSeriesLoadResponse -> {
                val targetEp = if (season != null && episode != null) {
                    loadResponse.episodes.firstOrNull { it.season == season && it.episode == episode }
                        ?: loadResponse.episodes.firstOrNull { it.episode == episode }
                } else if (episode != null) {
                    loadResponse.episodes.firstOrNull { it.episode == episode }
                } else null
                targetEp?.data ?: loadResponse.episodes.firstOrNull()?.data ?: loadResponse.url
            }
            is MovieLoadResponse -> {
                loadResponse.dataUrl.ifBlank { loadResponse.url }
            }
            else -> loadResponse?.url?.takeIf { it.isNotBlank() }
        }

        // 4. For TMDB providers: if episodeData is missing or not a JSON object, use synthesized LinkData
        if (isTmdbProvider && (episodeData.isNullOrBlank() || !episodeData.trimStart().startsWith("{"))) {
            log.d { "Using synthesized LinkData for ${api.name}" }
            episodeData = synthesizedLinkData
        }

        if (episodeData.isNullOrBlank()) {
            log.w { "No episode/movie data link found on ${api.name}" }
            return emptyList()
        }

        log.d { "Extracting stream links on ${api.name}..." }

        val results = mutableListOf<PluginRuntimeResult>()
        val subtitles = mutableListOf<PluginSubtitleResult>()

        suspend fun extractLinks(dataToLoad: String) {
            try {
                api.loadLinks(
                    data = dataToLoad,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        if (sub.url.isNotBlank()) {
                            subtitles.add(
                                PluginSubtitleResult(
                                    url = sub.url,
                                    language = sub.lang.ifBlank { "Unknown" },
                                    name = sub.lang.takeIf { it.isNotBlank() },
                                    headers = sub.headers.takeIf { it.isNotEmpty() },
                                )
                            )
                        }
                    },
                    callback = { link ->
                        if (link.url.isNotBlank()) {
                            val linkHeaders = link.getAllHeaders().takeIf { it.isNotEmpty() }
                            results.add(
                                PluginRuntimeResult(
                                    title = link.name.ifBlank { api.name },
                                    name = link.name.takeIf { it.isNotBlank() } ?: api.name,
                                    url = link.url,
                                    quality = Qualities.getStringByInt(link.quality),
                                    provider = api.name,
                                    headers = linkHeaders,
                                    subtitles = null,
                                )
                            )
                        }
                    }
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                log.w(t) { "Error during loadLinks on ${api.name}" }
            }
        }

        extractLinks(episodeData)

        // Retry with synthesized LinkData if initial extraction yielded nothing for TMDB provider
        if (results.isEmpty() && isTmdbProvider && episodeData != synthesizedLinkData) {
            log.d { "Retrying loadLinks on ${api.name} with synthesized LinkData" }
            extractLinks(synthesizedLinkData)
        }

        // Attach parsed subtitles to stream links if any were received
        val finalResults = if (subtitles.isNotEmpty()) {
            val subList = subtitles.toList()
            results.map { res ->
                if (res.subtitles.isNullOrEmpty()) res.copy(subtitles = subList) else res
            }
        } else {
            results
        }

        log.d { "Extracted ${finalResults.size} stream links from ${api.name}" }
        return finalResults
    }

    private fun buildLinkDataJson(
        id: Int?,
        imdbId: String?,
        title: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        type: String,
    ): String {
        val map = mutableMapOf<String, Any?>()
        if (id != null) map["id"] = id
        if (!imdbId.isNullOrBlank()) map["imdbId"] = imdbId
        if (!title.isNullOrBlank()) map["title"] = title
        if (year != null) map["year"] = year
        if (season != null) map["season"] = season
        if (episode != null) map["episode"] = episode
        map["type"] = type
        map["isAnime"] = false
        return AppUtils.toJson(map)
    }

    private fun sanitizeSearchQuery(query: String): String =
        query.replace("&", " and ")
            .replace(Regex("[:\\-–—_()\\[\\]/.]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun findBestMatch(
        results: List<SearchResponse>,
        query: String,
        year: Int?,
    ): SearchResponse? {
        val cleanQuery = cleanName(query)
        if (cleanQuery.isBlank()) return results.firstOrNull()

        // 1. Exact title match + year match
        if (year != null) {
            val exactYearMatch = results.firstOrNull { res ->
                cleanName(res.name) == cleanQuery && res.searchYear() != null && abs(res.searchYear()!! - year) <= 1
            }
            if (exactYearMatch != null) return exactYearMatch
        }

        // 2. Exact title match
        val exactMatch = results.firstOrNull { cleanName(it.name) == cleanQuery }
        if (exactMatch != null) return exactMatch

        // 3. Contains title match + year match
        if (year != null) {
            val containsYearMatch = results.firstOrNull { res ->
                val name = cleanName(res.name)
                (name.contains(cleanQuery) || cleanQuery.contains(name)) &&
                    res.searchYear() != null && abs(res.searchYear()!! - year) <= 1
            }
            if (containsYearMatch != null) return containsYearMatch
        }

        // 4. Contains title match
        val containsMatch = results.firstOrNull {
            val name = cleanName(it.name)
            name.contains(cleanQuery) || cleanQuery.contains(name)
        }
        if (containsMatch != null) return containsMatch

        return results.firstOrNull()
    }

    private fun SearchResponse.searchYear(): Int? = when (this) {
        is MovieSearchResponse -> year
        is TvSeriesSearchResponse -> year
        is AnimeSearchResponse -> year
        else -> null
    }

    private fun cleanName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
