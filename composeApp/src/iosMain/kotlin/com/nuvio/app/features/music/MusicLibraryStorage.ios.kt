package com.nuvio.app.features.music

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

internal actual object MusicLibraryStorage {
    private const val KEY_LIKED_TRACKS = "music_liked_tracks"
    private const val KEY_RECENT_TRACKS = "music_recent_tracks"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    actual fun loadLikedTracks(): List<MusicTrack> = runCatching {
        val def = NSUserDefaults.standardUserDefaults
        val raw = def.stringForKey(KEY_LIKED_TRACKS) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveLikedTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            val def = NSUserDefaults.standardUserDefaults
            def.setObject(encoded, forKey = KEY_LIKED_TRACKS)
        }
    }

    actual fun loadRecentTracks(): List<MusicTrack> = runCatching {
        val def = NSUserDefaults.standardUserDefaults
        val raw = def.stringForKey(KEY_RECENT_TRACKS) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveRecentTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            val def = NSUserDefaults.standardUserDefaults
            def.setObject(encoded, forKey = KEY_RECENT_TRACKS)
        }
    }
}
