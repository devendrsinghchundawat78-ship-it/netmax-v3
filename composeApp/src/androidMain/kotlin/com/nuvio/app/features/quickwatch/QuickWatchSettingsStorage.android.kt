package com.nuvio.app.features.quickwatch

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.app.core.storage.ProfileScopedKey

internal actual object QuickWatchSettingsStorage {
    private const val PREFS_NAME = "nuvio_quick_watch_settings"
    private const val KEY_ENABLED = "quick_watch_enabled"
    private const val KEY_OVERLAY_POSITION = "quick_watch_overlay_position"
    private const val KEY_SHOW_OVERVIEW = "quick_watch_show_overview"
    private const val KEY_AUTO_MUTE = "quick_watch_auto_mute"
    private const val KEY_SHOW_ACTION_RAIL = "quick_watch_show_action_rail"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadSettings(): QuickWatchSettings {
        val prefs = preferences ?: return QuickWatchSettings()
        return QuickWatchSettings(
            enabled = prefs.getBoolean(ProfileScopedKey.of(KEY_ENABLED), true),
            overlayPosition = prefs.getString(ProfileScopedKey.of(KEY_OVERLAY_POSITION), "left") ?: "left",
            showOverview = prefs.getBoolean(ProfileScopedKey.of(KEY_SHOW_OVERVIEW), true),
            autoMute = prefs.getBoolean(ProfileScopedKey.of(KEY_AUTO_MUTE), false),
            showActionRail = prefs.getBoolean(ProfileScopedKey.of(KEY_SHOW_ACTION_RAIL), true),
        )
    }

    actual fun saveSettings(settings: QuickWatchSettings) {
        preferences?.edit()
            ?.putBoolean(ProfileScopedKey.of(KEY_ENABLED), settings.enabled)
            ?.putString(ProfileScopedKey.of(KEY_OVERLAY_POSITION), settings.overlayPosition)
            ?.putBoolean(ProfileScopedKey.of(KEY_SHOW_OVERVIEW), settings.showOverview)
            ?.putBoolean(ProfileScopedKey.of(KEY_AUTO_MUTE), settings.autoMute)
            ?.putBoolean(ProfileScopedKey.of(KEY_SHOW_ACTION_RAIL), settings.showActionRail)
            ?.apply()
    }

    private const val KEY_SEEN_IDS = "quick_watch_seen_video_ids"

    actual fun loadSeenVideoIds(): Set<String> {
        val prefs = preferences ?: return emptySet()
        val raw = prefs.getString(ProfileScopedKey.of(KEY_SEEN_IDS), "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    actual fun saveSeenVideoIds(ids: Set<String>) {
        val raw = ids.toList().takeLast(200).joinToString(",")
        preferences?.edit()
            ?.putString(ProfileScopedKey.of(KEY_SEEN_IDS), raw)
            ?.apply()
    }
}
