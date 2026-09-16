package com.nuvio.app.features.quickwatch

internal expect object QuickWatchSettingsStorage {
    fun loadSettings(): QuickWatchSettings
    fun saveSettings(settings: QuickWatchSettings)
    fun loadSeenVideoIds(): Set<String>
    fun saveSeenVideoIds(ids: Set<String>)
}
