package com.nuvio.app.features.music

internal expect object MusicSettingsStorage {
    fun loadSettings(): MusicSettings
    fun saveSettings(settings: MusicSettings)
}
