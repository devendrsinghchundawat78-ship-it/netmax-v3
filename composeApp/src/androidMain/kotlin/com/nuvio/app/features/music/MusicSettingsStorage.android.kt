package com.nuvio.app.features.music

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object MusicSettingsStorage {
    private const val PREFS_NAME = "nuvio_music_settings"
    private const val KEY_ENABLED = "music_enabled"
    private const val KEY_STREAMING_QUALITY = "music_streaming_quality"
    private const val KEY_DOWNLOAD_QUALITY = "music_download_quality"
    private const val KEY_AUTO_PLAY_NEXT = "music_auto_play_next"
    private const val KEY_VOLUME_NORMALIZATION = "music_volume_normalization"
    private const val KEY_PREFERRED_SOURCE = "music_preferred_source"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadSettings(): MusicSettings {
        val prefs = preferences ?: return MusicSettings()
        return MusicSettings(
            enabled = prefs.getBoolean(ProfileScopedKey.of(KEY_ENABLED), true),
            streamingQuality = prefs.getString(ProfileScopedKey.of(KEY_STREAMING_QUALITY), "320kbps") ?: "320kbps",
            downloadQuality = prefs.getString(ProfileScopedKey.of(KEY_DOWNLOAD_QUALITY), "320kbps") ?: "320kbps",
            autoPlayNext = prefs.getBoolean(ProfileScopedKey.of(KEY_AUTO_PLAY_NEXT), true),
            volumeNormalization = prefs.getBoolean(ProfileScopedKey.of(KEY_VOLUME_NORMALIZATION), true),
            preferredSource = prefs.getString(ProfileScopedKey.of(KEY_PREFERRED_SOURCE), "JioSaavn") ?: "JioSaavn",
        )
    }

    actual fun saveSettings(settings: MusicSettings) {
        preferences?.edit()
            ?.putBoolean(ProfileScopedKey.of(KEY_ENABLED), settings.enabled)
            ?.putString(ProfileScopedKey.of(KEY_STREAMING_QUALITY), settings.streamingQuality)
            ?.putString(ProfileScopedKey.of(KEY_DOWNLOAD_QUALITY), settings.downloadQuality)
            ?.putBoolean(ProfileScopedKey.of(KEY_AUTO_PLAY_NEXT), settings.autoPlayNext)
            ?.putBoolean(ProfileScopedKey.of(KEY_VOLUME_NORMALIZATION), settings.volumeNormalization)
            ?.putString(ProfileScopedKey.of(KEY_PREFERRED_SOURCE), settings.preferredSource)
            ?.apply()
    }
}
