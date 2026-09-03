package com.nuvio.app.features.tmdb

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.catalog.CatalogPage
import com.nuvio.app.features.home.HomeCatalogDefinition
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

object TmdbHomeCatalogResolver {
    private val log = Logger.withTag("TmdbHomeCatalogResolver")
    private val json = Json { ignoreUnknownKeys = true }

    fun getTmdbCatalogDefinitions(): List<HomeCatalogDefinition> = listOf(
        HomeCatalogDefinition(
            key = "tmdb:trending_movies",
            defaultTitle = "Trending Movies",
            catalogName = "Trending Movies",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://trending_movies",
            type = "movie",
            catalogId = "trending_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:trending_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:trending_series",
            defaultTitle = "Trending Series",
            catalogName = "Trending Series",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://trending_series",
            type = "series",
            catalogId = "trending_series",
            supportsPagination = true,
            descriptorSignature = "tmdb:trending_series",
        ),
        HomeCatalogDefinition(
            key = "tmdb:popular_movies",
            defaultTitle = "Popular Movies",
            catalogName = "Popular Movies",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://popular_movies",
            type = "movie",
            catalogId = "popular_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:popular_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:popular_series",
            defaultTitle = "Popular Series",
            catalogName = "Popular Series",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://popular_series",
            type = "series",
            catalogId = "popular_series",
            supportsPagination = true,
            descriptorSignature = "tmdb:popular_series",
        ),
        HomeCatalogDefinition(
            key = "tmdb:top_rated_movies",
            defaultTitle = "Top Rated Movies",
            catalogName = "Top Rated Movies",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://top_rated_movies",
            type = "movie",
            catalogId = "top_rated_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:top_rated_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:top_rated_series",
            defaultTitle = "Top Rated Series",
            catalogName = "Top Rated Series",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://top_rated_series",
            type = "series",
            catalogId = "top_rated_series",
            supportsPagination = true,
            descriptorSignature = "tmdb:top_rated_series",
        ),
        HomeCatalogDefinition(
            key = "tmdb:upcoming_movies",
            defaultTitle = "Upcoming Movies",
            catalogName = "Upcoming Movies",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://upcoming_movies",
            type = "movie",
            catalogId = "upcoming_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:upcoming_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:action_movies",
            defaultTitle = "Action & Adventure",
            catalogName = "Action & Adventure",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://action_movies",
            type = "movie",
            catalogId = "action_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:action_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:scifi_movies",
            defaultTitle = "Sci-Fi & Fantasy",
            catalogName = "Sci-Fi & Fantasy",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://scifi_movies",
            type = "movie",
            catalogId = "scifi_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:scifi_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:animation_movies",
            defaultTitle = "Animation & Anime",
            catalogName = "Animation & Anime",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://animation_movies",
            type = "movie",
            catalogId = "animation_movies",
            supportsPagination = true,
            descriptorSignature = "tmdb:animation_movies",
        ),
        HomeCatalogDefinition(
            key = "tmdb:on_the_air_series",
            defaultTitle = "On The Air Series",
            catalogName = "On The Air Series",
            addonName = "NetMax TMDB",
            manifestUrl = "tmdb://on_the_air_series",
            type = "series",
            catalogId = "on_the_air_series",
            supportsPagination = true,
            descriptorSignature = "tmdb:on_the_air_series",
        ),
    )

    fun endpointForDefinition(definition: HomeCatalogDefinition): String =
        when (definition.catalogId) {
            "trending_movies" -> "trending/movie/day"
            "trending_series" -> "trending/tv/day"
            "popular_movies" -> "movie/popular"
            "popular_series" -> "tv/popular"
            "top_rated_movies" -> "movie/top_rated"
            "top_rated_series" -> "tv/top_rated"
            "upcoming_movies" -> "movie/upcoming"
            "action_movies", "scifi_movies", "animation_movies" -> "discover/movie"
            "on_the_air_series" -> "tv/on_the_air"
            else -> if (definition.type == "series") "tv/popular" else "movie/popular"
        }

    fun queryParamsForDefinition(definition: HomeCatalogDefinition): Map<String, String> =
        when (definition.catalogId) {
            "action_movies" -> mapOf("with_genres" to "28", "sort_by" to "popularity.desc")
            "scifi_movies" -> mapOf("with_genres" to "878", "sort_by" to "popularity.desc")
            "animation_movies" -> mapOf("with_genres" to "16", "sort_by" to "popularity.desc")
            else -> emptyMap()
        }

    suspend fun fetchCatalogForDefinition(definition: HomeCatalogDefinition, page: Int = 1): CatalogPage {
        val endpoint = endpointForDefinition(definition)
        val queryParams = queryParamsForDefinition(definition)
        return fetchCatalog(
            endpoint = endpoint,
            queryParams = queryParams,
            mediaType = definition.type,
            page = page,
        )
    }

    suspend fun fetchCatalog(
        endpoint: String,
        queryParams: Map<String, String> = emptyMap(),
        mediaType: String,
        page: Int = 1,
    ): CatalogPage = withContext(Dispatchers.Default) {
        val settings = TmdbSettingsRepository.snapshot()
        val apiKey = settings.apiKey.trim().takeIf { it.isNotBlank() } ?: "netmax-managed"
        val language = normalizeTmdbLanguage(settings.language)

        val query = mutableMapOf<String, String>()
        query["language"] = language
        query["page"] = page.toString()
        query.putAll(queryParams)

        val url = buildTmdbUrl(endpoint = endpoint, apiKey = apiKey, query = query)

        runCatching {
            val responseText = httpGetText(url)
            val response = json.decodeFromString<TmdbHomeResponse>(responseText)
            val items = response.results.mapNotNull { it.toMetaPreview(mediaType) }
            val totalPages = response.totalPages ?: 1
            CatalogPage(
                items = items,
                rawItemCount = items.size,
                nextSkip = if (page < totalPages && items.isNotEmpty()) page + 1 else null,
            )
        }.getOrElse { error ->
            log.w { "Failed to fetch TMDB catalog for $endpoint: ${error.message}" }
            CatalogPage(items = emptyList(), rawItemCount = 0, nextSkip = null)
        }
    }

    suspend fun searchMulti(query: String, page: Int = 1): CatalogPage = withContext(Dispatchers.Default) {
        if (query.isBlank()) return@withContext CatalogPage(items = emptyList(), rawItemCount = 0, nextSkip = null)
        val settings = TmdbSettingsRepository.snapshot()
        val apiKey = settings.apiKey.trim().takeIf { it.isNotBlank() } ?: "netmax-managed"
        val language = normalizeTmdbLanguage(settings.language)

        val queryMap = mapOf(
            "query" to query.trim(),
            "language" to language,
            "page" to page.toString(),
            "include_adult" to "false",
        )
        val url = buildTmdbUrl(endpoint = "search/multi", apiKey = apiKey, query = queryMap)

        runCatching {
            val responseText = httpGetText(url)
            val response = json.decodeFromString<TmdbHomeResponse>(responseText)
            val items = response.results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .mapNotNull { it.toMetaPreview(it.mediaType ?: "movie") }
            val totalPages = response.totalPages ?: 1
            CatalogPage(
                items = items,
                rawItemCount = items.size,
                nextSkip = if (page < totalPages && items.isNotEmpty()) page + 1 else null,
            )
        }.getOrElse { error ->
            log.w { "Failed TMDB search for $query: ${error.message}" }
            CatalogPage(items = emptyList(), rawItemCount = 0, nextSkip = null)
        }
    }
}

@Serializable
private data class TmdbHomeResponse(
    val page: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
    @SerialName("total_results") val totalResults: Int? = null,
    val results: List<TmdbItemDto> = emptyList(),
)

@Serializable
private data class TmdbItemDto(
    val id: Int,
    @SerialName("media_type") val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("original_name") val originalName: String? = null,
    val overview: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("vote_average") val voteAverage: Double? = null,
    @SerialName("vote_count") val voteCount: Int? = null,
    val popularity: Double? = null,
) {
    fun toMetaPreview(defaultType: String): MetaPreview? {
        val resolvedType = when (mediaType?.lowercase()) {
            "tv" -> "series"
            "movie" -> "movie"
            else -> if (defaultType == "tv" || defaultType == "series") "series" else "movie"
        }
        val itemTitle = title?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: originalTitle?.takeIf { it.isNotBlank() }
            ?: originalName?.takeIf { it.isNotBlank() }
            ?: return null

        val posterUrl = posterPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500$it" }
            ?: backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w780$it" }
        val bannerUrl = backdropPath?.takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w1280$it" }

        val release = when (resolvedType) {
            "series", "tv" -> firstAirDate?.take(4)
            else -> releaseDate?.take(4)
        }
        val rawDate = when (resolvedType) {
            "series", "tv" -> firstAirDate
            else -> releaseDate
        }

        return MetaPreview(
            id = "tmdb:$id",
            type = resolvedType,
            name = itemTitle,
            poster = posterUrl,
            banner = bannerUrl,
            posterShape = PosterShape.Poster,
            description = overview?.takeIf { it.isNotBlank() },
            releaseInfo = release,
            rawReleaseDate = rawDate,
            popularity = popularity,
            voteCount = voteCount,
            imdbRating = voteAverage?.let { ((it * 10).roundToInt() / 10.0).toString() },
        )
    }
}
