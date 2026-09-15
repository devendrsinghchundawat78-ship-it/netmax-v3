package com.nuvio.app.features.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal actual object MusicLibraryStorage {
    private const val PREFS_NAME = "nuvio_music_library"
    private const val KEY_LIKED_TRACKS = "music_liked_tracks"
    private const val KEY_RECENT_TRACKS = "music_recent_tracks"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadLikedTracks(): List<MusicTrack> = runCatching {
        val prefs = preferences ?: return emptyList()
        val raw = prefs.getString(ProfileScopedKey.of(KEY_LIKED_TRACKS), null) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveLikedTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            preferences?.edit()?.putString(ProfileScopedKey.of(KEY_LIKED_TRACKS), encoded)?.apply()
        }
    }

    actual fun loadRecentTracks(): List<MusicTrack> = runCatching {
        val prefs = preferences ?: return emptyList()
        val raw = prefs.getString(ProfileScopedKey.of(KEY_RECENT_TRACKS), null) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveRecentTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            preferences?.edit()?.putString(ProfileScopedKey.of(KEY_RECENT_TRACKS), encoded)?.apply()
        }
    }
}
