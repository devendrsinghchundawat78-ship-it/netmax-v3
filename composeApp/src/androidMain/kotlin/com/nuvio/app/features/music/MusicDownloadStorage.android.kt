package com.nuvio.app.features.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal actual object MusicDownloadStorage {
    private const val PREFS_NAME = "nuvio_music_downloads"
    private const val KEY_DOWNLOADS = "music_downloaded_tracks"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadDownloadedTracks(): List<MusicTrack> = runCatching {
        val prefs = preferences ?: return emptyList()
        val raw = prefs.getString(ProfileScopedKey.of(KEY_DOWNLOADS), null) ?: return emptyList()
        json.decodeFromString<List<MusicTrack>>(raw)
    }.getOrDefault(emptyList())

    actual fun saveDownloadedTracks(tracks: List<MusicTrack>) {
        runCatching {
            val encoded = json.encodeToString(tracks)
            preferences?.edit()?.putString(ProfileScopedKey.of(KEY_DOWNLOADS), encoded)?.apply()
        }
    }
}
