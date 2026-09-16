package com.nuvio.app.features.music

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

data class MusicLyrics(
    val trackId: String,
    val isSynced: Boolean,
    val lines: List<LyricLine>,
    val plainText: String,
)

object MusicLyricsService {
    private const val LRCLIB_BASE = "https://lrclib.net"
    private const val USER_AGENT = "NetMaxTV/3.1.1 (https://github.com/netmax)"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val lyricsCache = mutableMapOf<String, MusicLyrics?>()

    // Title cleaning regex patterns ported from Convx LrcLib
    private val titleCleanupPatterns = listOf(
        Regex("""\s*\(.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\[.*?(official|video|audio|lyrics|lyric|visualizer|hd|hq|4k|remaster|remix|live|acoustic|version|edit|extended|radio|clean|explicit).*?\]""", RegexOption.IGNORE_CASE),
        Regex("""\s*【.*?】"""),
        Regex("""\s*\|.*$"""),
        Regex("""\s*-\s*(official|video|audio|lyrics|lyric|visualizer).*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(feat\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(ft\..*?\)""", RegexOption.IGNORE_CASE),
        Regex("""\s*feat\..*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*ft\..*$""", RegexOption.IGNORE_CASE),
    )

    private val artistSeparators = listOf(" & ", " and ", ", ", " x ", " X ", " feat. ", " feat ", " ft. ", " ft ", " featuring ", " with ")

    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()
        for (pattern in titleCleanupPatterns) {
            cleaned = cleaned.replace(pattern, "")
        }
        return cleaned.trim()
    }

    private fun cleanArtist(artist: String): String {
        var cleaned = artist.trim()
        for (separator in artistSeparators) {
            if (cleaned.contains(separator, ignoreCase = true)) {
                cleaned = cleaned.split(separator, ignoreCase = true, limit = 2)[0]
                break
            }
        }
        return cleaned.trim()
    }

    private fun urlEncode(value: String): String {
        return value.replace(" ", "%20")
            .replace("&", "%26")
            .replace("?", "%3F")
            .replace("#", "%23")
            .replace("+", "%2B")
    }

    suspend fun getLyrics(track: MusicTrack): Result<MusicLyrics> = withContext(Dispatchers.IO) {
        runCatching {
            lyricsCache[track.id]?.let { return@runCatching it }

            val cleanedTitle = cleanTitle(track.title)
            val cleanedArtist = cleanArtist(track.artist)

            // 1. Try exact match query
            val exactUrl = buildString {
                append("$LRCLIB_BASE/api/get?")
                append("track_name=${urlEncode(cleanedTitle)}")
                append("&artist_name=${urlEncode(cleanedArtist)}")
                if (track.durationSeconds > 0) {
                    append("&duration=${track.durationSeconds}")
                }
            }

            var response = httpRequestRaw(
                method = "GET",
                url = exactUrl,
                headers = mapOf("User-Agent" to USER_AGENT),
                body = "",
            )

            var lyricsData: MusicLyrics? = null

            if (response.status in 200..299 && response.body.isNotBlank()) {
                lyricsData = parseLrcLibResponse(track.id, response.body)
            }

            // 2. Fallback to search if exact match returned 404 or empty
            if (lyricsData == null || lyricsData.lines.isEmpty()) {
                val searchUrl = "$LRCLIB_BASE/api/search?q=${urlEncode("$cleanedTitle $cleanedArtist")}"
                response = httpRequestRaw(
                    method = "GET",
                    url = searchUrl,
                    headers = mapOf("User-Agent" to USER_AGENT),
                    body = "",
                )

                if (response.status in 200..299 && response.body.isNotBlank()) {
                    lyricsData = parseLrcLibSearchResponse(track.id, response.body, track.durationSeconds)
                }
            }

            if (lyricsData == null || lyricsData.lines.isEmpty()) {
                throw NoSuchElementException("No lyrics found for ${track.title}")
            }

            lyricsCache[track.id] = lyricsData
            lyricsData
        }
    }

    private fun parseLrcLibResponse(trackId: String, rawJson: String): MusicLyrics? = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val synced = root["syncedLyrics"]?.jsonPrimitive?.content
        val plain = root["plainLyrics"]?.jsonPrimitive?.content

        when {
            !synced.isNullOrBlank() -> {
                val parsedLines = parseSyncedLyrics(synced)
                MusicLyrics(
                    trackId = trackId,
                    isSynced = true,
                    lines = parsedLines,
                    plainText = plain ?: parsedLines.joinToString("\n") { it.text },
                )
            }
            !plain.isNullOrBlank() -> {
                val parsedLines = plain.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1L, it) }
                MusicLyrics(
                    trackId = trackId,
                    isSynced = false,
                    lines = parsedLines,
                    plainText = plain,
                )
            }
            else -> null
        }
    }.getOrNull()

    private fun parseLrcLibSearchResponse(trackId: String, rawJson: String, targetDurationSec: Int): MusicLyrics? = runCatching {
        val array = json.parseToJsonElement(rawJson).jsonArray
        if (array.isEmpty()) return null

        // Find best matching track (prioritizing syncedLyrics and closest duration)
        var bestObj = array.first().jsonObject
        var minDiff = Int.MAX_VALUE

        for (el in array) {
            val obj = el.jsonObject
            val hasSynced = !obj["syncedLyrics"]?.jsonPrimitive?.content.isNullOrBlank()
            val dur = obj["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 0
            val diff = if (targetDurationSec > 0) abs(dur - targetDurationSec) else 0

            if (hasSynced && diff < minDiff) {
                minDiff = diff
                bestObj = obj
            }
        }

        val synced = bestObj["syncedLyrics"]?.jsonPrimitive?.content
        val plain = bestObj["plainLyrics"]?.jsonPrimitive?.content

        when {
            !synced.isNullOrBlank() -> {
                val parsedLines = parseSyncedLyrics(synced)
                MusicLyrics(
                    trackId = trackId,
                    isSynced = true,
                    lines = parsedLines,
                    plainText = plain ?: parsedLines.joinToString("\n") { it.text },
                )
            }
            !plain.isNullOrBlank() -> {
                val parsedLines = plain.lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { LyricLine(-1L, it) }
                MusicLyrics(
                    trackId = trackId,
                    isSynced = false,
                    lines = parsedLines,
                    plainText = plain,
                )
            }
            else -> null
        }
    }.getOrNull()

    private val LINE_REGEX = "((\\[\\d{1,2}:\\d{2}\\.\\d{2,3}\\]\\s*)+)(.*)".toRegex()
    private val TIME_REGEX = "\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\]".toRegex()

    private fun parseSyncedLyrics(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        for (rawLine in lrcContent.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") || trimmed.startsWith("[al:")) continue

            val match = LINE_REGEX.matchEntire(trimmed) ?: continue
            val timePart = match.groupValues[1]
            val text = match.groupValues[3].trim()

            for (timeMatch in TIME_REGEX.findAll(timePart)) {
                val minutes = timeMatch.groupValues[1].toLongOrNull() ?: 0L
                val seconds = timeMatch.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = timeMatch.groupValues[3]
                val fracMs = if (fracStr.length == 2) (fracStr.toLongOrNull() ?: 0L) * 10L else fracStr.toLongOrNull() ?: 0L
                val timeMs = minutes * 60_000L + seconds * 1000L + fracMs

                if (text.isNotBlank()) {
                    lines.add(LyricLine(timeMs = timeMs, text = text))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}
