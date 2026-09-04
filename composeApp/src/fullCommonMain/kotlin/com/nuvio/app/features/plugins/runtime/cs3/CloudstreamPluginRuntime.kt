package com.nuvio.app.features.plugins.runtime.cs3

import co.touchlab.kermit.Logger
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.Qualities
import com.nuvio.app.features.plugins.PluginRuntimeResult
import com.nuvio.app.features.plugins.PluginSubtitleResult
import com.nuvio.app.features.tmdb.TmdbService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Base64

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
            Base64.getDecoder().decode(code.trim())
        }.getOrElse {
            code.toByteArray(Charsets.ISO_8859_1)
        }

        val api = CloudstreamPluginLoader.loadApi(scraperId, cs3Bytes)
        if (api == null) {
            log.w { "Failed to get MainAPI for scraper $scraperId" }
            return emptyList()
        }

        val (mediaTitle, releaseYear) = TmdbService.fetchMediaTitleAndYear(
            tmdbId = tmdbId,
            mediaType = mediaType,
        ) ?: (null to null)

        val searchQuery = mediaTitle ?: tmdbId
        log.d { "Searching ${api.name} for '$searchQuery'" }

        val searchResults = runCatching {
            api.search(searchQuery)
        }.getOrNull() ?: emptyList()

        if (searchResults.isEmpty()) {
            log.d { "No search results found on ${api.name} for '$searchQuery'" }
            return emptyList()
        }

        val matchedResult = findBestMatch(
            results = searchResults,
            query = searchQuery,
            year = releaseYear,
        ) ?: searchResults.first()

        log.d { "Selected match: '${matchedResult.name}' (${matchedResult.url})" }

        val loadResponse = runCatching {
            api.load(matchedResult.url)
        }.getOrNull() ?: return emptyList()

        val episodeData: String? = when (loadResponse) {
            is AnimeLoadResponse -> {
                val allEpisodes = loadResponse.episodes.values.flatten()
                val targetEp = if (episode != null) {
                    allEpisodes.firstOrNull { it.episode == episode }
                } else null
                targetEp?.data ?: allEpisodes.firstOrNull()?.data
            }
            is TvSeriesLoadResponse -> {
                val targetEp = if (season != null && episode != null) {
                    loadResponse.episodes.firstOrNull { it.season == season && it.episode == episode }
                        ?: loadResponse.episodes.firstOrNull { it.episode == episode }
                } else if (episode != null) {
                    loadResponse.episodes.firstOrNull { it.episode == episode }
                } else null
                targetEp?.data ?: loadResponse.episodes.firstOrNull()?.data
            }
            is MovieLoadResponse -> {
                loadResponse.dataUrl.ifBlank { loadResponse.url }
            }
            else -> null
        }

        if (episodeData.isNullOrBlank()) {
            log.d { "No episode/movie data link found in load response" }
            return emptyList()
        }

        log.d { "Extracting stream links for episode/movie data..." }

        val results = mutableListOf<PluginRuntimeResult>()
        val subtitles = mutableListOf<PluginSubtitleResult>()

        runCatching {
            api.loadLinks(
                data = episodeData,
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
                        results.add(
                            PluginRuntimeResult(
                                title = link.name.ifBlank { api.name },
                                name = link.name.takeIf { it.isNotBlank() } ?: api.name,
                                url = link.url,
                                quality = Qualities.getStringByInt(link.quality),
                                provider = api.name,
                                headers = link.headers.takeIf { it.isNotEmpty() },
                                subtitles = subtitles.toList().takeIf { it.isNotEmpty() },
                            )
                        )
                    }
                }
            )
        }.onFailure {
            log.w(it) { "Error during loadLinks on ${api.name}" }
        }

        log.d { "Extracted ${results.size} stream links from ${api.name}" }
        return results
    }

    private fun findBestMatch(
        results: List<SearchResponse>,
        query: String,
        year: Int?,
    ): SearchResponse? {
        val cleanQuery = cleanName(query)
        val exactMatch = results.firstOrNull { cleanName(it.name) == cleanQuery }
        if (exactMatch != null) return exactMatch

        val containsMatch = results.firstOrNull {
            val name = cleanName(it.name)
            name.contains(cleanQuery) || cleanQuery.contains(name)
        }
        if (containsMatch != null) return containsMatch

        return results.firstOrNull()
    }

    private fun cleanName(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
}
