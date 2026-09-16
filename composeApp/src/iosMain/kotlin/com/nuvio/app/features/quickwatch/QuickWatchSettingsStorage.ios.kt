package com.nuvio.app.features.quickwatch

import platform.Foundation.NSUserDefaults

internal actual object QuickWatchSettingsStorage {
    private const val KEY_ENABLED = "quick_watch_enabled"
    private const val KEY_OVERLAY_POSITION = "quick_watch_overlay_position"
    private const val KEY_SHOW_OVERVIEW = "quick_watch_show_overview"
    private const val KEY_AUTO_MUTE = "quick_watch_auto_mute"
    private const val KEY_SHOW_ACTION_RAIL = "quick_watch_show_action_rail"

    actual fun loadSettings(): QuickWatchSettings {
        val def = NSUserDefaults.standardUserDefaults
        val hasEnabled = def.objectForKey(KEY_ENABLED) != null
        val enabled = if (hasEnabled) def.boolForKey(KEY_ENABLED) else true
        val overlayPosition = def.stringForKey(KEY_OVERLAY_POSITION) ?: "left"
        val hasOverview = def.objectForKey(KEY_SHOW_OVERVIEW) != null
        val showOverview = if (hasOverview) def.boolForKey(KEY_SHOW_OVERVIEW) else true
        val autoMute = def.boolForKey(KEY_AUTO_MUTE)
        val hasActionRail = def.objectForKey(KEY_SHOW_ACTION_RAIL) != null
        val showActionRail = if (hasActionRail) def.boolForKey(KEY_SHOW_ACTION_RAIL) else true

        return QuickWatchSettings(
            enabled = enabled,
            overlayPosition = overlayPosition,
            showOverview = showOverview,
            autoMute = autoMute,
            showActionRail = showActionRail,
        )
    }

    actual fun saveSettings(settings: QuickWatchSettings) {
        val def = NSUserDefaults.standardUserDefaults
        def.setBool(settings.enabled, forKey = KEY_ENABLED)
        def.setObject(settings.overlayPosition, forKey = KEY_OVERLAY_POSITION)
        def.setBool(settings.showOverview, forKey = KEY_SHOW_OVERVIEW)
        def.setBool(settings.autoMute, forKey = KEY_AUTO_MUTE)
        def.setBool(settings.showActionRail, forKey = KEY_SHOW_ACTION_RAIL)
    }

    private const val KEY_SEEN_IDS = "quick_watch_seen_video_ids"

    actual fun loadSeenVideoIds(): Set<String> {
        val def = NSUserDefaults.standardUserDefaults
        val raw = def.stringForKey(KEY_SEEN_IDS) ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    actual fun saveSeenVideoIds(ids: Set<String>) {
        val raw = ids.takeLast(200).joinToString(",")
        val def = NSUserDefaults.standardUserDefaults
        def.setObject(raw, forKey = KEY_SEEN_IDS)
    }
}
