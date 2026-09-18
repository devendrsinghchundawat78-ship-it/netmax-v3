package com.nuvio.app.features.youtube

import co.touchlab.kermit.Logger
import com.nuvio.app.features.addons.httpPostJsonWithHeaders
import kotlinx.coroutines.Dispatchers
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
        YouTubeFeedCategory("trending", "Trending", "trending trailers movies 4k"),
        YouTubeFeedCategory("trailers", "4K Trailers", "official 4k movie trailers 2025 2026"),
        YouTubeFeedCategory("clips", "Movie Clips", "4k hdr movie clips scene dolby"),
        YouTubeFeedCategory("cinema", "Cinema & Reviews", "cinema movies discussion and trailers"),
        YouTubeFeedCategory("music", "Music Videos", "trending 4k official music videos"),
        YouTubeFeedCategory("gaming", "Gaming", "4k gaming trailers walkthrough"),
    )

    private val feedCache = mutableMapOf<String, List<YouTubeVideoItem>>()
    private val channelCache = mutableMapOf<String, List<YouTubeVideoItem>>()

    suspend fun fetchFeed(query: String, forceRefresh: Boolean = false): List<YouTubeVideoItem> = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().ifBlank { "trending 4k trailers" }
        if (!forceRefresh) {
            feedCache[cleanQuery]?.let { return@withContext it }
        }

        val videos = searchInnerTube(cleanQuery)
        if (videos.isNotEmpty()) {
            feedCache[cleanQuery] = videos
        }
        videos
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

    suspend fun extractStreamQualities(videoId: String): List<YouTubeStreamQuality> = withContext(Dispatchers.Default) {
        if (!videoIdRegex.matches(videoId)) return@withContext emptyList()

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

        try {
            val responseText = httpPostJsonWithHeaders(
                url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                body = requestBody,
                headers = headers,
            )
            if (responseText.isBlank()) return@withContext emptyList()

            val root = json.parseToJsonElement(responseText).jsonObject
            val streamingData = root["streamingData"]?.jsonObject ?: return@withContext emptyList()
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
