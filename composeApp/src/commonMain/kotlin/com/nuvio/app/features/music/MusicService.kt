package com.nuvio.app.features.music

import com.nuvio.app.features.addons.httpRequestRaw
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MusicService {
    private const val BASE_URL = "https://www.jiosaavn.com/api.php"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private fun cleanHtml(text: String): String = text
        .replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .trim()

    suspend fun searchSongs(query: String, page: Int = 1, limit: Int = 25): Result<List<MusicTrack>> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@runCatching emptyList()

        val encodedQuery = encodeQuery(trimmed)
        val url = "$BASE_URL?__call=search.getResults&_format=json&_marker=0&cc=in&p=$page&n=$limit&q=$encodedQuery"

        val response = httpRequestRaw(
            method = "GET",
            url = url,
            headers = mapOf("User-Agent" to USER_AGENT),
            body = "",
        )

        if (response.status !in 200..299) {
            throw IllegalStateException("Saavn search returned status ${response.status}")
        }

        parseSongsFromResponse(response.body)
    }

    suspend fun getTrendingSongs(): Result<List<MusicTrack>> = runCatching {
        // First try India Superhits Top 50 playlist
        val playlistUrl = "$BASE_URL?__call=playlist.getDetails&_format=json&_marker=0&cc=in&listid=1134543272"
        val response = httpRequestRaw(
            method = "GET",
            url = playlistUrl,
            headers = mapOf("User-Agent" to USER_AGENT),
            body = "",
        )

        var tracks = emptyList<MusicTrack>()
        if (response.status in 200..299 && response.body.isNotBlank()) {
            tracks = parsePlaylistSongsFromResponse(response.body)
        }

        // Fallback to top hits search if playlist was empty
        if (tracks.isEmpty()) {
            val fallbackUrl = "$BASE_URL?__call=search.getResults&_format=json&_marker=0&cc=in&p=1&n=30&q=Top+50+Hits"
            val fallbackResponse = httpRequestRaw(
                method = "GET",
                url = fallbackUrl,
                headers = mapOf("User-Agent" to USER_AGENT),
                body = "",
            )
            if (fallbackResponse.status in 200..299) {
                tracks = parseSongsFromResponse(fallbackResponse.body)
            }
        }

        tracks
    }

    suspend fun resolveStreamUrlAsync(track: MusicTrack, preferredQuality: String = "320kbps"): String? {
        if (!track.localFilePath.isNullOrBlank()) {
            return track.localFilePath
        }

        val isFlacRequested = preferredQuality.equals("flac", ignoreCase = true) ||
            preferredQuality.contains("lossless", ignoreCase = true) ||
            track.isFlac

        if (isFlacRequested) {
            val flacStreamUrl = ClashFlacService.resolveFlacStream(track)
            if (!flacStreamUrl.isNullOrBlank()) {
                return flacStreamUrl
            }
        }

        return resolveStreamUrl(track, preferredQuality)
    }

    fun resolveStreamUrl(track: MusicTrack, preferredQuality: String = "320kbps"): String? {
        if (!track.localFilePath.isNullOrBlank()) {
            return track.localFilePath
        }

        if (!track.streamUrl.isNullOrBlank()) {
            return track.streamUrl
        }

        val encUrl = track.encryptedMediaUrl ?: return null
        val decrypted = DesDecrypter.decrypt(encUrl) ?: return null

        return adjustQuality(decrypted, preferredQuality, track.bitrate320Available)
    }

    private fun adjustQuality(rawUrl: String, quality: String, has320: Boolean): String {
        val targetSuffix = when {
            quality.contains("320") && has320 -> "_320.mp4"
            quality.contains("160") -> "_160.mp4"
            quality.contains("96") -> "_96.mp4"
            has320 -> "_320.mp4"
            else -> "_160.mp4"
        }

        return rawUrl
            .replace("_96.mp4", targetSuffix)
            .replace("_160.mp4", targetSuffix)
            .replace("_320.mp4", targetSuffix)
    }

    private fun parseSongsFromResponse(rawJson: String): List<MusicTrack> = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val resultsArray = root["results"]?.jsonArray ?: return emptyList()

        resultsArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val songName = obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "Unknown Track"
            val singers = obj["singers"]?.jsonPrimitive?.content
                ?: obj["primary_artists"]?.jsonPrimitive?.content
                ?: obj["music"]?.jsonPrimitive?.content
                ?: "Unknown Artist"
            val album = obj["album"]?.jsonPrimitive?.content ?: ""
            val image = obj["image"]?.jsonPrimitive?.content ?: ""
            val duration = obj["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val encMediaUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
            val is320 = obj["320kbps"]?.jsonPrimitive?.let { it.content == "true" || it.booleanOrNull == true } ?: true

            MusicTrack(
                id = id,
                title = cleanHtml(songName),
                artist = cleanHtml(singers),
                album = cleanHtml(album),
                artworkUrl = image,
                durationSeconds = duration,
                encryptedMediaUrl = encMediaUrl,
                bitrate320Available = is320,
            )
        }
    }.getOrDefault(emptyList())

    private fun parsePlaylistSongsFromResponse(rawJson: String): List<MusicTrack> = runCatching {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val listArray = root["songs"]?.jsonArray ?: root["list"]?.jsonArray ?: return emptyList()

        listArray.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val songName = obj["song"]?.jsonPrimitive?.content ?: obj["title"]?.jsonPrimitive?.content ?: "Unknown Track"
            val singers = obj["singers"]?.jsonPrimitive?.content
                ?: obj["primary_artists"]?.jsonPrimitive?.content
                ?: obj["music"]?.jsonPrimitive?.content
                ?: "Unknown Artist"
            val album = obj["album"]?.jsonPrimitive?.content ?: ""
            val image = obj["image"]?.jsonPrimitive?.content ?: ""
            val duration = obj["duration"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val encMediaUrl = obj["encrypted_media_url"]?.jsonPrimitive?.content
            val is320 = obj["320kbps"]?.jsonPrimitive?.let { it.content == "true" || it.booleanOrNull == true } ?: true

            MusicTrack(
                id = id,
                title = cleanHtml(songName),
                artist = cleanHtml(singers),
                album = cleanHtml(album),
                artworkUrl = image,
                durationSeconds = duration,
                encryptedMediaUrl = encMediaUrl,
                bitrate320Available = is320,
            )
        }
    }.getOrDefault(emptyList())

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
