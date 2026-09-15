package com.nuvio.app.features.music

import platform.Foundation.NSUserDefaults

internal actual object MusicSettingsStorage {
    private const val KEY_ENABLED = "music_enabled"
    private const val KEY_STREAMING_QUALITY = "music_streaming_quality"
    private const val KEY_DOWNLOAD_QUALITY = "music_download_quality"
    private const val KEY_AUTO_PLAY_NEXT = "music_auto_play_next"
    private const val KEY_VOLUME_NORMALIZATION = "music_volume_normalization"
    private const val KEY_PREFERRED_SOURCE = "music_preferred_source"

    actual fun loadSettings(): MusicSettings {
        val def = NSUserDefaults.standardUserDefaults
        val hasEnabled = def.objectForKey(KEY_ENABLED) != null
        val enabled = if (hasEnabled) def.boolForKey(KEY_ENABLED) else true
        val streamingQuality = def.stringForKey(KEY_STREAMING_QUALITY) ?: "320kbps"
        val downloadQuality = def.stringForKey(KEY_DOWNLOAD_QUALITY) ?: "320kbps"
        val hasAutoPlay = def.objectForKey(KEY_AUTO_PLAY_NEXT) != null
        val autoPlayNext = if (hasAutoPlay) def.boolForKey(KEY_AUTO_PLAY_NEXT) else true
        val hasVolNorm = def.objectForKey(KEY_VOLUME_NORMALIZATION) != null
        val volumeNormalization = if (hasVolNorm) def.boolForKey(KEY_VOLUME_NORMALIZATION) else true
        val preferredSource = def.stringForKey(KEY_PREFERRED_SOURCE) ?: "JioSaavn"

        return MusicSettings(
            enabled = enabled,
            streamingQuality = streamingQuality,
            downloadQuality = downloadQuality,
            autoPlayNext = autoPlayNext,
            volumeNormalization = volumeNormalization,
            preferredSource = preferredSource,
        )
    }

    actual fun saveSettings(settings: MusicSettings) {
        val def = NSUserDefaults.standardUserDefaults
        def.setBool(settings.enabled, forKey = KEY_ENABLED)
        def.setObject(settings.streamingQuality, forKey = KEY_STREAMING_QUALITY)
        def.setObject(settings.downloadQuality, forKey = KEY_DOWNLOAD_QUALITY)
        def.setBool(settings.autoPlayNext, forKey = KEY_AUTO_PLAY_NEXT)
        def.setBool(settings.volumeNormalization, forKey = KEY_VOLUME_NORMALIZATION)
        def.setObject(settings.preferredSource, forKey = KEY_PREFERRED_SOURCE)
    }
}
