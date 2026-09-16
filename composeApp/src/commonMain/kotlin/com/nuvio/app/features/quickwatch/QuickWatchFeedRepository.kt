package com.nuvio.app.features.quickwatch

import co.touchlab.kermit.Logger
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.tmdb.TmdbHomeCatalogResolver
import com.nuvio.app.features.tmdb.TmdbMetadataService
import com.nuvio.app.features.watchprogress.CurrentDateProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object QuickWatchFeedRepository {
    private val log = Logger.withTag("QuickWatchFeedRepo")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _feed = MutableStateFlow<List<QuickWatchItem>>(emptyList())
    val feed: StateFlow<List<QuickWatchItem>> = _feed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _likedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val likedItemIds: StateFlow<Set<String>> = _likedItemIds.asStateFlow()

    private var currentPage = 1
    private var isFetching = false
    private val seenVideoIds = mutableSetOf<String>()
    private val seenTmdbIds = mutableSetOf<Int>()
    private var hasLoadedSeenFromDisk = false

    fun ensureLoaded() {
        if (!hasLoadedSeenFromDisk) {
            hasLoadedSeenFromDisk = true
            val persistedSeen = runCatching { QuickWatchSettingsStorage.loadSeenVideoIds() }.getOrDefault(emptySet())
            seenVideoIds.addAll(persistedSeen)
        }
        if (_feed.value.isEmpty() && !_isLoading.value) {
            loadFeed(page = 1, reset = true)
        }
    }

    fun refresh() {
        loadFeed(page = 1, reset = true)
    }

    fun loadMore() {
        if (!isFetching && _feed.value.isNotEmpty()) {
            loadFeed(page = currentPage + 1, reset = false)
        }
    }

    fun markVideoAsSeen(videoId: String) {
        if (videoId.isBlank()) return
        if (seenVideoIds.add(videoId)) {
            // Keep at most 200 in persistent storage
            if (seenVideoIds.size > 200) {
                val toRetain = seenVideoIds.toList().takeLast(150).toSet()
                seenVideoIds.clear()
                seenVideoIds.addAll(toRetain)
            }
            scope.launch(Dispatchers.IO) {
                runCatching { QuickWatchSettingsStorage.saveSeenVideoIds(seenVideoIds) }
            }
        }
    }

    fun toggleLike(videoId: String) {
        val current = _likedItemIds.value.toMutableSet()
        val isNowLiked = if (current.contains(videoId)) {
            current.remove(videoId)
            false
        } else {
            current.add(videoId)
            true
        }
        _likedItemIds.value = current

        // Optimistically update feed like count and status
        _feed.value = _feed.value.map { item ->
            if (item.youtubeVideoId == videoId) {
                val newCount = if (isNowLiked) item.likeCount + 1 else (item.likeCount - 1).coerceAtLeast(0)
                item.copy(isLiked = isNowLiked, likeCount = newCount)
            } else {
                item
            }
        }
    }

    private fun loadFeed(page: Int, reset: Boolean) {
        if (isFetching) return
        isFetching = true
        _isLoading.value = true

        scope.launch {
            try {
                if (!hasLoadedSeenFromDisk) {
                    hasLoadedSeenFromDisk = true
                    val persistedSeen = runCatching { QuickWatchSettingsStorage.loadSeenVideoIds() }.getOrDefault(emptySet())
                    seenVideoIds.addAll(persistedSeen)
                }

                val newItems = fetchFeedItems(page)
                withContext(Dispatchers.Main) {
                    if (reset) {
                        seenTmdbIds.clear()
                        seenTmdbIds.addAll(newItems.map { it.tmdbId })
                        _feed.value = newItems
                        currentPage = 1
                    } else {
                        val filtered = newItems.filter { seenTmdbIds.add(it.tmdbId) }
                        _feed.value = _feed.value + filtered
                        currentPage = page
                    }
                }
            } catch (e: Throwable) {
                log.w(e) { "Failed to load Quick Watch feed for page $page" }
            } finally {
                isFetching = false
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchFeedItems(page: Int): List<QuickWatchItem> = withContext(Dispatchers.Default) {
        val allDefinitions = TmdbHomeCatalogResolver.getTmdbCatalogDefinitions()

        // Pick 4 diverse catalog categories to query
        val selectedDefs = if (page == 1) {
            allDefinitions.shuffled().take(4)
        } else {
            allDefinitions.shuffled().take(3)
        }

        // Randomize page offset for initial load / refresh so it never pulls the exact same rank 1..20 movies
        val basePage = if (page == 1) (1..4).random() else page

        val allPreviews = coroutineScope {
            selectedDefs.mapIndexed { idx, def ->
                async {
                    val endpoint = TmdbHomeCatalogResolver.endpointForDefinition(def)
                    val queryParams = TmdbHomeCatalogResolver.queryParamsForDefinition(def)
                    val targetPage = if (page == 1) ((basePage + idx - 1) % 4) + 1 else page
                    runCatching {
                        TmdbHomeCatalogResolver.fetchCatalog(
                            endpoint = endpoint,
                            queryParams = queryParams,
                            mediaType = def.type,
                            page = targetPage,
                        ).items
                    }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }.distinctBy { it.id }.shuffled()

        val todayIso = CurrentDateProvider.todayIsoDate()
        val items = mutableListOf<QuickWatchItem>()
        val localSeenTmdb = mutableSetOf<Int>()

        for (preview in allPreviews) {
            val tmdbId = preview.id.removePrefix("tmdb:").toIntOrNull() ?: continue
            if (seenTmdbIds.contains(tmdbId) || localSeenTmdb.contains(tmdbId)) continue

            val mediaType = if (preview.type == "series" || preview.type == "tv") "tv" else "movie"

            // Fetch TMDB official trailers/teasers/clips in parallel with YouTube Shorts
            val (trailers, shorts) = coroutineScope {
                val trailersDeferred = async {
                    runCatching {
                        TmdbMetadataService.fetchTrailers(
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                            language = "en",
                        )
                    }.getOrDefault(emptyList())
                }
                val shortsDeferred = async {
                    runCatching {
                        MovieShortsResolver.fetchShortsForMovie(preview.name, limit = 2)
                    }.getOrDefault(emptyList())
                }
                trailersDeferred.await() to shortsDeferred.await()
            }

            val validTrailers = trailers.filter {
                it.site.equals("YouTube", ignoreCase = true) &&
                    it.key.isNotBlank() &&
                    !seenVideoIds.contains(it.key)
            }

            val validShorts = shorts.filter {
                it.videoId.isNotBlank() &&
                    !seenVideoIds.contains(it.videoId)
            }

            if (validTrailers.isEmpty() && validShorts.isEmpty()) continue

            val releaseYear = preview.releaseInfo?.take(4)
            val isWatchable = preview.releaseInfo?.let { it <= todayIso } ?: true

            // 1. Add YouTube Shorts for this movie (if found)
            for (short in validShorts.take(1)) {
                items.add(
                    QuickWatchItem(
                        id = "short_${short.videoId}_$tmdbId",
                        youtubeVideoId = short.videoId,
                        youtubeUrl = "https://www.youtube.com/shorts/${short.videoId}",
                        title = short.title.ifBlank { "${preview.name} Shorts" },
                        videoType = "Shorts",
                        movieTitle = preview.name,
                        movieOverview = preview.description.orEmpty(),
                        moviePoster = preview.poster,
                        movieBackdrop = preview.banner ?: preview.poster,
                        movieLogo = preview.logo,
                        releaseDate = preview.releaseInfo,
                        releaseYear = releaseYear,
                        tmdbId = tmdbId,
                        mediaType = preview.type,
                        genres = preview.genres,
                        voteAverage = preview.imdbRating?.toDoubleOrNull(),
                        isWatchable = isWatchable,
                        likeCount = (450..9200).random(),
                        commentCount = (24..680).random(),
                        isLiked = _likedItemIds.value.contains(short.videoId),
                    )
                )
            }

            // 2. Add official trailer or teaser for this movie (if found)
            val bestTrailer = validTrailers.firstOrNull { it.type.equals("Trailer", ignoreCase = true) }
                ?: validTrailers.firstOrNull { it.type.equals("Teaser", ignoreCase = true) }
                ?: validTrailers.firstOrNull { it.type.equals("Clip", ignoreCase = true) }
                ?: validTrailers.firstOrNull()

            if (bestTrailer != null) {
                items.add(
                    QuickWatchItem(
                        id = "trailer_${bestTrailer.key}_$tmdbId",
                        youtubeVideoId = bestTrailer.key,
                        youtubeUrl = "https://www.youtube.com/watch?v=${bestTrailer.key}",
                        title = bestTrailer.name.ifBlank { preview.name },
                        videoType = bestTrailer.type.ifBlank { "Trailer" },
                        movieTitle = preview.name,
                        movieOverview = preview.description.orEmpty(),
                        moviePoster = preview.poster,
                        movieBackdrop = preview.banner ?: preview.poster,
                        movieLogo = preview.logo,
                        releaseDate = preview.releaseInfo,
                        releaseYear = releaseYear,
                        tmdbId = tmdbId,
                        mediaType = preview.type,
                        genres = preview.genres,
                        voteAverage = preview.imdbRating?.toDoubleOrNull(),
                        isWatchable = isWatchable,
                        likeCount = (120..4500).random(),
                        commentCount = (8..340).random(),
                        isLiked = _likedItemIds.value.contains(bestTrailer.key),
                    )
                )
            }

            localSeenTmdb.add(tmdbId)
        }

        // Safety fallback: if user has seen a lot and items count is low, evict older seen IDs
        if (items.size < 5 && seenVideoIds.size > 50) {
            val toKeep = seenVideoIds.toList().takeLast(30).toSet()
            seenVideoIds.clear()
            seenVideoIds.addAll(toKeep)
            runCatching { QuickWatchSettingsStorage.saveSeenVideoIds(seenVideoIds) }
        }

        // Shuffle items so YouTube Shorts and Trailers naturally interleave
        items.shuffled()
    }
}
