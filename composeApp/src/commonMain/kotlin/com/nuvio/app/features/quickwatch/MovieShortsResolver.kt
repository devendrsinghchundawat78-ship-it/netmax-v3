package com.nuvio.app.features.quickwatch

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
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class MovieShortVideo(
    val videoId: String,
    val title: String,
    val isShort: Boolean = true,
)

/**
 * Searches and extracts authentic YouTube Shorts and scene clips for movies using InnerTube.
 */
object MovieShortsResolver {
    private val log = Logger.withTag("MovieShortsResolver")
    private val json = Json { ignoreUnknownKeys = true }
    private val videoIdRegex = Regex("^[a-zA-Z0-9_-]{11}$")
    private val cache = mutableMapOf<String, List<MovieShortVideo>>()

    suspend fun fetchShortsForMovie(movieTitle: String, limit: Int = 3): List<MovieShortVideo> = withContext(Dispatchers.Default) {
        val cleanTitle = movieTitle.trim()
        if (cleanTitle.isBlank()) return@withContext emptyList()

        synchronized(cache) {
            cache[cleanTitle]?.let { return@withContext it }
        }

        val query = "$cleanTitle movie #shorts"
        val requestBody = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "ANDROID")
                    put("clientVersion", "20.10.35")
                    put("hl", "en")
                    put("gl", "IN")
                }
            }
            put("query", query)
        }.toString()

        val headers = mapOf(
            "Content-Type" to "application/json",
            "User-Agent" to "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US)",
        )

        try {
            val responseText = httpPostJsonWithHeaders(
                url = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false",
                body = requestBody,
                headers = headers,
            )
            if (responseText.isBlank()) return@withContext emptyList()

            val root = json.parseToJsonElement(responseText)
            val extracted = mutableListOf<MovieShortVideo>()
            val seenIds = mutableSetOf<String>()

            collectVideoCandidates(root, extracted, seenIds)

            val results = extracted.take(limit)
            synchronized(cache) {
                if (cache.size > 80) {
                    val firstKey = cache.keys.firstOrNull()
                    if (firstKey != null) cache.remove(firstKey)
                }
                cache[cleanTitle] = results
            }
            results
        } catch (e: Throwable) {
            log.w(e) { "Failed to search movie shorts for $cleanTitle" }
            emptyList()
        }
    }

    private fun collectVideoCandidates(
        element: JsonElement,
        out: MutableList<MovieShortVideo>,
        seen: MutableSet<String>,
    ) {
        when (element) {
            is JsonObject -> {
                // Check if this object is a video or reel renderer
                val cvr = element["compactVideoRenderer"]?.jsonObject
                    ?: element["videoRenderer"]?.jsonObject
                    ?: element["reelItemRenderer"]?.jsonObject

                if (cvr != null) {
                    val vid = cvr["videoId"]?.jsonPrimitive?.contentOrNull
                    if (vid != null && videoIdRegex.matches(vid) && seen.add(vid)) {
                        val title = extractText(cvr["title"])
                            ?: extractText(cvr["headline"])
                            ?: "Movie Scene"

                        val length = extractLengthText(cvr)
                        val isShort = isShortDuration(length) ||
                            title.contains("#shorts", ignoreCase = true) ||
                            title.contains("shorts", ignoreCase = true) ||
                            title.contains("scene", ignoreCase = true) ||
                            title.contains("clip", ignoreCase = true)

                        if (isShort) {
                            out.add(MovieShortVideo(videoId = vid, title = title, isShort = true))
                        }
                    }
                }

                for ((_, child) in element) {
                    collectVideoCandidates(child, out, seen)
                }
            }
            is JsonArray -> {
                for (child in element) {
                    collectVideoCandidates(child, out, seen)
                }
            }
            else -> Unit
        }
    }

    private fun extractText(element: JsonElement?): String? {
        if (element == null) return null
        if (element is JsonObject) {
            element["simpleText"]?.jsonPrimitive?.contentOrNull?.let { return it }
            val runs = element["runs"]?.jsonArray
            if (runs != null && runs.isNotEmpty()) {
                val sb = StringBuilder()
                for (r in runs) {
                    r.jsonObject["text"]?.jsonPrimitive?.contentOrNull?.let { sb.append(it) }
                }
                if (sb.isNotEmpty()) return sb.toString()
            }
        }
        return null
    }

    private fun extractLengthText(cvr: JsonObject): String? {
        extractText(cvr["lengthText"])?.let { return it }
        val overlays = cvr["thumbnailOverlays"]?.jsonArray ?: return null
        for (overlay in overlays) {
            val renderer = overlay.jsonObject["thumbnailOverlayTimeStatusRenderer"]?.jsonObject
            if (renderer != null) {
                extractText(renderer["text"])?.let { return it }
            }
        }
        return null
    }

    private fun isShortDuration(lengthText: String?): Boolean {
        if (lengthText == null) return true // default to true for reel items which often lack lengthText
        val parts = lengthText.split(":")
        return when (parts.size) {
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: 0
                minutes <= 2 // under 3 minutes
            }
            1 -> {
                val seconds = parts[0].toIntOrNull() ?: 0
                seconds <= 180
            }
            else -> false
        }
    }
}
