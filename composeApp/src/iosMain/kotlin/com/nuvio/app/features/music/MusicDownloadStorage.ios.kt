package com.nuvio.app.features.music

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object MusicDownloadStorage {
    private const val KEY_DOWNLOADS = "music_downloaded_tracks"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    actual fun loadDownloadedTracks(): List<MusicTrack> = runCatching {
        val def = NSUserDefaults.standardUserDefaults
        val raw = def.stringForKey(KEY_DOWNLOADS) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveDownloadedTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            val def = NSUserDefaults.standardUserDefaults
            def.setObject(encoded, forKey = KEY_DOWNLOADS)
        }
    }
}
