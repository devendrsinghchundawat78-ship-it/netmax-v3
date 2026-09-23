package com.nuvio.app.features.youtube

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object YouTubeRepository {
    private val log = Logger.withTag("YouTubeRepo")
    private val json = Json { ignoreUnknownKeys = true }
    private val videoIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
    private val fourKRegex = Regex("\\b(4k|2160p|uhd|8k|4320p)\\b", RegexOption.IGNORE_CASE)

    val defaultCategories = listOf(
        YouTubeFeedCategory(
            id = "trending",
            label = "Trending",
            searchQuery = "trending trailers movies 4k",
            queryVariations = listOf(
                "trending trailers movies 4k",
                "latest official movie trailers 4k 2025 2026",
                "trending movies clips 4k hdr dolby",
                "new official movie teasers 4k",
                "trending blockbuster trailers 4k uhd",
                "viral movie cinema clips 4k 60fps",
                "popular new movie previews 4k",
            ),
        ),
        YouTubeFeedCategory(
            id = "trailers",
            label = "4K Trailers",
            searchQuery = "official 4k movie trailers 2025 2026",
            queryVariations = listOf(
                "official 4k movie trailers 2025 2026",
                "new 4k ultra hd movie trailers",
                "latest hollywood bollywood official trailers 4k",
                "upcoming cinema official trailers 4k hdr",
                "official teaser trailer 4k uhd",
                "new action sci-fi trailers 4k 2025",
            ),
        ),
        YouTubeFeedCategory(
            id = "clips",
            label = "Movie Clips",
            searchQuery = "4k hdr movie clips scene dolby",
            queryVariations = listOf(
                "4k hdr movie clips scene dolby",
                "best movie action scenes 4k hdr",
                "cinematic movie moments 4k ultra hd",
                "epic movie scenes 4k 60fps",
                "iconic movie clips 4k dolby vision",
                "4k movie fight scene clips uhd",
            ),
        ),
        YouTubeFeedCategory(
            id = "cinema",
            label = "Cinema & Reviews",
            searchQuery = "cinema movies discussion and trailers",
            queryVariations = listOf(
                "cinema movies discussion and trailers",
                "movie breakdown hidden details easter eggs 4k",
                "cinema review new movie trailer breakdown",
                "film analysis cinema video essay 4k",
                "top upcoming cinema previews 4k",
            ),
        ),
        YouTubeFeedCategory(
            id = "music",
            label = "Music Videos",
            searchQuery = "trending 4k official music videos",
            queryVariations = listOf(
                "trending 4k official music videos",
                "latest official music videos 4k hdr",
                "top hits music videos 4k 2025",
                "new music releases official video 4k",
                "popular music video 4k ultra hd",
            ),
        ),
        YouTubeFeedCategory(
            id = "gaming",
            label = "Gaming",
            searchQuery = "4k gaming trailers walkthrough",
            queryVariations = listOf(
                "4k gaming trailers walkthrough",
                "new gameplay trailer 4k 60fps ps5",
                "latest video game cinematic trailers 4k",
                "unreal engine 5 game trailer 4k",
                "upcoming games trailer 4k 2025 2026",
            ),
        ),
    )

    private val feedCache = mutableMapOf<String, List<YouTubeVideoItem>>()
    private val channelCache = mutableMapOf<String, List<YouTubeVideoItem>>()
    private val categoryIndices = mutableMapOf<String, Int>()
    private val seenVideoIds = LinkedHashSet<String>()
    private const val MAX_SEEN_IDS = 400

    suspend fun fetchFeed(
        query: String,
        forceRefresh: Boolean = false,
        categoryId: String? = null,
    ): List<YouTubeVideoItem> = withContext(Dispatchers.Default) {
        val category = defaultCategories.firstOrNull { it.id == categoryId || it.searchQuery == query }
        val effectiveQuery = if (category != null && category.queryVariations.isNotEmpty()) {
            if (forceRefresh) {
                val nextIdx = ((categoryIndices[category.id] ?: 0) + 1) % category.queryVariations.size
                categoryIndices[category.id] = nextIdx
                category.queryVariations[nextIdx]
            } else {
                val currentIdx = (categoryIndices[category.id] ?: 0) % category.queryVariations.size
                category.queryVariations[currentIdx]
            }
        } else {
            query.trim().ifBlank { "trending 4k trailers" }
        }

        val cacheKey = "${category?.id ?: "custom"}:$effectiveQuery"
        if (!forceRefresh) {
            feedCache[cacheKey]?.let { return@withContext it }
        } else {
            feedCache.remove(cacheKey)
        }

        val fetched = searchInnerTube(effectiveQuery)
        if (fetched.isEmpty()) {
            return@withContext feedCache[cacheKey].orEmpty()
        }

        // Put unseen videos first so each refresh delivers fresh, new content
        val unseen = fetched.filterNot { seenVideoIds.contains(it.id) }
        val seen = fetched.filter { seenVideoIds.contains(it.id) }
        val ordered = if (unseen.isNotEmpty()) unseen + seen else fetched.shuffled()

        fetched.forEach {
            seenVideoIds.add(it.id)
            if (seenVideoIds.size > MAX_SEEN_IDS) {
                val first = seenVideoIds.first()
                seenVideoIds.remove(first)
            }
        }

        feedCache[cacheKey] = ordered
        ordered
    }

    suspend fun searchVideos(query: String): List<YouTubeVideoItem> = withContext(Dispatchers.Default) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext emptyList()
        searchInnerTube(clean)
    }

    suspend fun fetchChannelVideos(channelName: String, excludeVideoId: String? = null): List<YouTubeVideoItem> = withContext(Dispatchers.Default) {
        val cleanName = channelName.trim()
        if (cleanName.isBlank()) return@withContext emptyList()

        channelCache[cleanName]?.let { cached ->
            return@withContext if (excludeVideoId != null) cached.filterNot { it.id == excludeVideoId } else cached
        }

        val query = "$cleanName videos"
        val videos = searchInnerTube(query)
        if (videos.isNotEmpty()) {
            channelCache[cleanName] = videos
        }
        if (excludeVideoId != null) videos.filterNot { it.id == excludeVideoId } else videos
    }

    private val inFlightExtractions = mutableMapOf<String, kotlinx.coroutines.Deferred<List<YouTubeStreamQuality>>>()
    private val extractionMutex = Mutex()

    suspend fun extractStreamQualities(videoId: String): List<YouTubeStreamQuality> = withContext(Dispatchers.Default) {
        if (!videoIdRegex.matches(videoId)) return@withContext emptyList()

        val cached = YouTubePlayerQualityStore.getQualities(videoId)
        if (cached.isNotEmpty()) return@withContext cached

        val deferred = extractionMutex.withLock {
            val alreadyCached = YouTubePlayerQualityStore.getQualities(videoId)
            if (alreadyCached.isNotEmpty()) return@withLock async { alreadyCached }

            inFlightExtractions[videoId]?.let { return@withLock it }

            val newDeferred = async(Dispatchers.Default) {
                performExtractStreamQualities(videoId)
            }
            inFlightExtractions[videoId] = newDeferred
            newDeferred
        }

        try {
            deferred.await()
        } finally {
            extractionMutex.withLock {
                inFlightExtractions.remove(videoId)
            }
        }
    }

    private suspend fun performExtractStreamQualities(videoId: String): List<YouTubeStreamQuality> {
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "20.10.35")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString()

        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US)",
        )

        return try {
            val responseText = httpPostJsonWithHeaders(
                url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                body = requestBody,
                headers = headers,
            )
            if (responseText.isBlank()) return emptyList()

            val root = json.parseToJsonElement(responseText).jsonObject
            val streamingData = root["streamingData"]?.jsonObject ?: return emptyList()
            val adaptive = streamingData["adaptiveFormats"]?.jsonArray.orEmpty()
            val progressive = streamingData["formats"]?.jsonArray.orEmpty()

            val audioFormats = adaptive.mapNotNull { it as? JsonObject }
                .filter { (it["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()).contains("audio/") }
                .mapNotNull { f ->
                    val url = f["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val bitrate = f["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    bitrate to url
                }

            val bestAudioUrl = audioFormats.maxByOrNull { it.first }?.second

            val qualityMap = mutableMapOf<Int, YouTubeStreamQuality>()

            // 1. Process Adaptive video formats (4K, 1440p, 1080p, 720p, etc.)
            adaptive.mapNotNull { it as? JsonObject }
                .filter { (it["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()).contains("video/") }
                .forEach { f ->
                    val url = f["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val height = f["height"]?.jsonPrimitive?.intOrNull ?: 0
                    if (height <= 0) return@forEach
                    val fps = f["fps"]?.jsonPrimitive?.intOrNull ?: 30
                    val bitrate = f["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val rawLabel = f["qualityLabel"]?.jsonPrimitive?.contentOrNull ?: "${height}p"

                    val displayLabel = when {
                        height >= 2160 || rawLabel.contains("2160") -> "4K (2160p)"
                        height >= 1440 || rawLabel.contains("1440") -> "2K (1440p)"
                        height >= 1080 || rawLabel.contains("1080") -> "1080p FHD"
                        height >= 720 || rawLabel.contains("720") -> "720p HD"
                        height >= 480 || rawLabel.contains("480") -> "480p"
                        height >= 360 || rawLabel.contains("360") -> "360p"
                        else -> "${height}p"
                    }

                    val existing = qualityMap[height]
                    if (existing == null || bitrate > existing.bitrate) {
                        qualityMap[height] = YouTubeStreamQuality(
                            label = displayLabel,
                            height = height,
                            videoUrl = url,
                            audioUrl = bestAudioUrl,
                            bitrate = bitrate,
                            fps = fps,
                        )
                    }
                }

            // 2. Process Progressive formats (combined video + audio fallback)
            progressive.mapNotNull { it as? JsonObject }.forEach { f ->
                val url = f["url"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val height = f["height"]?.jsonPrimitive?.intOrNull ?: 0
                if (height <= 0) return@forEach
                val rawLabel = f["qualityLabel"]?.jsonPrimitive?.contentOrNull ?: "${height}p"
                val displayLabel = when {
                    height >= 1080 -> "1080p FHD"
                    height >= 720 -> "720p HD"
                    height >= 480 -> "480p"
                    height >= 360 -> "360p"
                    else -> "${height}p"
                }
                if (!qualityMap.containsKey(height)) {
                    qualityMap[height] = YouTubeStreamQuality(
                        label = displayLabel,
                        height = height,
                        videoUrl = url,
                        audioUrl = null, // Progressive already has integrated audio
                        bitrate = f["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L,
                        fps = f["fps"]?.jsonPrimitive?.intOrNull ?: 30,
                    )
                }
            }

            val sorted = qualityMap.values.sortedByDescending { it.height }
            YouTubePlayerQualityStore.setQualities(videoId, sorted)
            sorted
        } catch (e: Throwable) {
            log.w(e) { "Failed to extract YouTube stream qualities for $videoId" }
            emptyList()
        }
    }

    private suspend fun searchInnerTube(query: String): List<YouTubeVideoItem> {
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "20.10.35")
                    put("hl", "en")
                    put("gl", "US")
                }
            }
            put("query", query)
        }.toString()

        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US)",
        )

        return try {
            val responseText = httpPostJsonWithHeaders(
                url = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
                body = requestBody,
                headers = headers,
            )
            if (responseText.isBlank()) return emptyList()

            val root = json.parseToJsonElement(responseText)
            val extracted = mutableListOf<YouTubeVideoItem>()
            val seenIds = mutableSetOf<String>()

            collectVideoRenderers(root, extracted, seenIds)
            extracted
        } catch (e: Throwable) {
            log.w(e) { "InnerTube search failed for query: $query" }
            emptyList()
        }
    }

    private fun collectVideoRenderers(
        element: JsonElement,
        out: MutableList<YouTubeVideoItem>,
        seen: MutableSet<String>,
    ) {
        when (element) {
            is JsonObject -> {
                val vr = element["videoRenderer"]?.jsonObject
                    ?: element["compactVideoRenderer"]?.jsonObject

                if (vr != null) {
                    val videoId = vr["videoId"]?.jsonPrimitive?.contentOrNull
                    if (videoId != null && videoIdRegex.matches(videoId) && seen.add(videoId)) {
                        val title = parseRunsOrSimpleText(vr["title"])
                        if (title.isNotBlank()) {
                            val channelTitle = parseRunsOrSimpleText(vr["ownerText"])
                                .ifBlank { parseRunsOrSimpleText(vr["shortBylineText"]) }
                                .ifBlank { "YouTube Creator" }

                            val channelId = vr["ownerText"]?.jsonObject
                                ?.get("runs")?.jsonArray?.firstOrNull()?.jsonObject
                                ?.get("navigationEndpoint")?.jsonObject
                                ?.get("browseEndpoint")?.jsonObject
                                ?.get("browseId")?.jsonPrimitive?.contentOrNull

                            val channelAvatar = vr["channelThumbnailSupportedRenderers"]?.jsonObject
                                ?.get("channelThumbnailWithLinkRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject
                                ?.get("thumbnails")?.jsonArray?.lastOrNull()?.jsonObject
                                ?.get("url")?.jsonPrimitive?.contentOrNull

                            val thumbnails = vr["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray.orEmpty()
                            val bestThumbnail = thumbnails.lastOrNull()?.jsonObject
                                ?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

                            val lengthText = vr["lengthText"]?.let(::parseRunsOrSimpleText)
                            val viewCount = vr["viewCountText"]?.let(::parseRunsOrSimpleText)
                                ?: vr["shortViewCountText"]?.let(::parseRunsOrSimpleText)
                            val publishedTime = vr["publishedTimeText"]?.let(::parseRunsOrSimpleText)

                            val badgeList = vr["badges"]?.jsonArray?.mapNotNull { b ->
                                val badgeObj = b.jsonObject["metadataBadgeRenderer"]?.jsonObject
                                badgeObj?.get("label")?.jsonPrimitive?.contentOrNull
                                    ?: badgeObj?.get("style")?.jsonPrimitive?.contentOrNull
                            }.orEmpty()

                            val is4K = fourKRegex.containsMatchIn(title) ||
                                badgeList.any { it.contains("4k", ignoreCase = true) }

                            out.add(
                                YouTubeVideoItem(
                                    id = videoId,
                                    title = title,
                                    thumbnailUrl = bestThumbnail,
                                    channelTitle = channelTitle,
                                    channelId = channelId,
                                    channelAvatarUrl = channelAvatar,
                                    duration = lengthText,
                                    viewCount = viewCount,
                                    publishedTime = publishedTime,
                                    description = null,
                                    is4K = is4K,
                                    badges = badgeList,
                                )
                            )
                        }
                    }
                }

                // Recurse into children
                element.values.forEach { collectVideoRenderers(it, out, seen) }
            }

            is JsonArray -> {
                element.forEach { collectVideoRenderers(it, out, seen) }
            }

            else -> Unit
        }
    }

    private fun parseRunsOrSimpleText(element: JsonElement?): String {
        if (element == null) return ""
        val obj = element as? JsonObject ?: return ""
        obj["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it.trim() }

        val runs = obj["runs"]?.jsonArray ?: return ""
        return runs.joinToString("") { run ->
            run.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
        }.trim()
    }
}
