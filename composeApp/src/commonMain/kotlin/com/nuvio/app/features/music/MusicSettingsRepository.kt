package com.nuvio.app.features.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicSettingsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _settings = MutableStateFlow(MusicSettings())
    val settings: StateFlow<MusicSettings> = _settings.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
    }

    private fun loadFromDisk() {
        _settings.value = MusicSettingsStorage.loadSettings()
    }

    fun updateSettings(transform: (MusicSettings) -> MusicSettings) {
        ensureLoaded()
        val updated = transform(_settings.value)
        _settings.value = updated
        MusicSettingsStorage.saveSettings(updated)
    }

    fun setEnabled(enabled: Boolean) {
        updateSettings { it.copy(enabled = enabled) }
    }

    fun setStreamingQuality(quality: String) {
        updateSettings { it.copy(streamingQuality = quality) }
    }

    fun setDownloadQuality(quality: String) {
        updateSettings { it.copy(downloadQuality = quality) }
    }

    fun setAutoPlayNext(autoPlay: Boolean) {
        updateSettings { it.copy(autoPlayNext = autoPlay) }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        updateSettings { it.copy(volumeNormalization = enabled) }
    }

    fun setPreferredSource(source: String) {
        updateSettings { it.copy(preferredSource = source) }
    }
}
