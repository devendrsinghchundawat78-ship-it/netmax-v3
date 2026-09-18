package com.nuvio.app.features.music

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ClashFlacService {
    private const val BASE_URL = "https://clashflac.kanjijewels.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun searchFlac(query: String, limit: Int = 20): Result<List<MusicTrack>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@runCatching emptyList()

        val encodedQuery = encodeQuery(trimmed)
        val url = "$BASE_URL/api/search?q=$encodedQuery&limit=$limit"

        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
            ),
            body = "",
        )

        if (response.status !in 200..299 || response.body.isBlank()) {
            return@runCatching emptyList()
        }

        parseSearchResponse(response.body)
    }

    suspend fun resolveFlacStream(track: MusicTrack): String? = runCatching {
        val targetInput = track.flacAsin
            ?: if (track.id.startsWith("clashflac_")) track.id.removePrefix("clashflac_")
            else "${track.title} ${track.artist}".trim()

        if (targetInput.isBlank()) return@runCatching null

        // 1. First attempt: Primary /api/resolve endpoint (Amazon/Qobuz Hi-Res Master FLAC)
        val resolveUrl = "$BASE_URL/api/resolve"
        val requestJson = """{"input":"${escapeJson(targetInput)}"}"""

        val response = httpRequestRaw(
            method = "POST",
            url = resolveUrl,
            headers = mapOf(
                "Content-Type" to "application/json",
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
            ),
            body = requestJson,
        )

        if (response.status in 200..299 && response.body.isNotBlank()) {
            val streamUrl = parseStreamUrlFromResolve(response.body)
            if (!streamUrl.isNullOrBlank()) {
                return@runCatching streamUrl
            }
        }

        // 2. Second attempt: Qobuz direct stream if ASIN is available
        val asin = track.flacAsin ?: (if (track.id.startsWith("clashflac_")) track.id.removePrefix("clashflac_") else null)
        if (!asin.isNullOrBlank()) {
            val qobuzUrl = "$BASE_URL/api/qobuz/stream/${encodeQuery(asin)}"
            val qobuzResp = httpRequestRaw(
                method = "GET",
                url = qobuzUrl,
                headers = mapOf(
                    "User-Agent" to USER_AGENT,
                    "Accept" to "application/json",
                ),
                body = "",
            )
            if (qobuzResp.status in 200..299 && qobuzResp.body.isNotBlank()) {
                val streamUrl = parseStreamUrlFromResolve(qobuzResp.body)
                if (!streamUrl.isNullOrBlank()) {
                    return@runCatching streamUrl
                }
            }
        }

        null
    }.getOrNull()

    private fun parseSearchResponse(rawJson: String): List<MusicTrack> = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonArray
        root.mapNotNull { elem ->
            val obj = elem.jsonObject
            val asin = obj["asin"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Unknown Title"
            val artist = obj["artist"]?.jsonPrimitive?.contentOrNull ?: "Unknown Artist"
            val album = obj["album"]?.jsonPrimitive?.contentOrNull ?: ""
            val durationSec = obj["duration_sec"]?.jsonPrimitive?.intOrNull ?: 0
            val thumb = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull ?: ""
            val streamUrl = obj["stream_url"]?.jsonPrimitive?.contentOrNull

            MusicTrack(
                id = "clashflac_$asin",
                title = title,
                artist = artist,
                album = album,
                artworkUrl = thumb,
                durationSeconds = durationSec,
                streamUrl = streamUrl,
                bitrate320Available = true,
                isFlac = true,
                flacAsin = asin,
                currentQuality = "Hi-Res FLAC",
            )
        }
    }.getOrDefault(emptyList())

    private fun parseStreamUrlFromResolve(rawJson: String): String? = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        root["stream_url"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    private fun escapeJson(input: String): String {
        return buildString {
            for (c in input) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
    }

    private fun encodeQuery(query: String): String {
        return buildString {
            for (char in query) {
                when (char) {
                    ' ' -> append("+")
                    in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '*' -> append(char)
                    else -> {
                        val bytes = char.toString().encodeToByteArray()
                        for (b in bytes) {
                            append('%')
                            append(((b.toInt() shr 4) and 0xF).toString(16).uppercase())
                            append((b.toInt() and 0xF).toString(16).uppercase())
                        }
                    }
                }
            }
        }
    }
}
