package com.nuvio.app.features.settings

import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import platform.Foundation.NSUserDefaults

actual object ThemeSettingsStorage {
    private const val selectedThemeKey = "selected_theme"
    private const val themeModeKey = "theme_mode"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val navBarStyleKey = "nav_bar_style"
    private const val liquidGlassEnabledKey = "liquid_glass_enabled"
    private const val liquidGlassVibrancyKey = "liquid_glass_vibrancy"
    private const val liquidGlassBlurRadiusKey = "liquid_glass_blur_radius"
    private const val liquidGlassRefractionHeightKey = "liquid_glass_refraction_height"
    private const val liquidGlassRefractionAmountKey = "liquid_glass_refraction_amount"
    private const val liquidGlassChromaticAberrationKey = "liquid_glass_chromatic_aberration"
    private const val liquidGlassDepthEffectKey = "liquid_glass_depth_effect"
    private const val liquidGlassSurfaceTintKey = "liquid_glass_surface_tint"
    private const val liquidGlassSurfaceOpacityKey = "liquid_glass_surface_opacity"
    private const val liquidGlassTextColorKey = "liquid_glass_text_color"
    private val profileScopedSyncKeys = listOf(
        selectedThemeKey,
        themeModeKey,
        amoledEnabledKey,
        liquidGlassNativeTabBarEnabledKey,
        navBarStyleKey,
        liquidGlassEnabledKey,
        liquidGlassVibrancyKey,
        liquidGlassBlurRadiusKey,
        liquidGlassRefractionHeightKey,
        liquidGlassRefractionAmountKey,
        liquidGlassChromaticAberrationKey,
        liquidGlassDepthEffectKey,
        liquidGlassSurfaceTintKey,
        liquidGlassSurfaceOpacityKey,
        liquidGlassTextColorKey,
    )

    actual fun loadSelectedTheme(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(selectedThemeKey))

    actual fun saveSelectedTheme(themeName: String) {
        NSUserDefaults.standardUserDefaults.setObject(themeName, forKey = ProfileScopedKey.of(selectedThemeKey))
    }

    actual fun loadThemeMode(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(themeModeKey))

    actual fun saveThemeMode(modeKey: String) {
        NSUserDefaults.standardUserDefaults.setObject(modeKey, forKey = ProfileScopedKey.of(themeModeKey))
    }

    actual fun loadAmoledEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(amoledEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(amoledEnabledKey))
    }

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val key = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey)
        return if (defaults.objectForKey(key) != null) {
            defaults.boolForKey(key)
        } else {
            null
        }
    }

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        NSUserDefaults.standardUserDefaults.setBool(
            enabled,
            forKey = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey),
        )
    }

    actual fun loadSelectedAppLanguage(): String? {
        val value = NSUserDefaults.standardUserDefaults.stringForKey(selectedAppLanguageKey)
        if (value != null) return value
        val legacy = NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(selectedAppLanguageKey))
        if (legacy != null) saveSelectedAppLanguage(legacy)
        return legacy
    }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        NSUserDefaults.standardUserDefaults.setObject(languageCode, forKey = selectedAppLanguageKey)
    }

    actual fun applySelectedAppLanguage(languageCode: String) {
        if (languageCode.equals("device", ignoreCase = true)) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey("AppleLanguages")
            NSUserDefaults.standardUserDefaults.synchronize()
            return
        }
        val normalizedCode = languageCode
            .trim()
            .takeIf { it.isNotBlank() }
            ?: AppLanguage.ENGLISH.code
        NSUserDefaults.standardUserDefaults.setObject(
            listOf(normalizedCode),
            forKey = "AppleLanguages",
        )
        NSUserDefaults.standardUserDefaults.synchronize()
    }

    actual fun loadNavBarStyle(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(navBarStyleKey))

    actual fun saveNavBarStyle(styleKey: String) {
        NSUserDefaults.standardUserDefaults.setObject(styleKey, forKey = ProfileScopedKey.of(navBarStyleKey))
    }

    actual fun loadLiquidGlassEnabled(): Boolean? = loadBool(liquidGlassEnabledKey)
    actual fun saveLiquidGlassEnabled(enabled: Boolean) = NSUserDefaults.standardUserDefaults.setBool(enabled, forKey = ProfileScopedKey.of(liquidGlassEnabledKey))
    actual fun loadLiquidGlassVibrancy(): Float? = loadFloat(liquidGlassVibrancyKey)
    actual fun saveLiquidGlassVibrancy(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassVibrancyKey))
    actual fun loadLiquidGlassBlurRadius(): Float? = loadFloat(liquidGlassBlurRadiusKey)
    actual fun saveLiquidGlassBlurRadius(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassBlurRadiusKey))
    actual fun loadLiquidGlassRefractionHeight(): Float? = loadFloat(liquidGlassRefractionHeightKey)
    actual fun saveLiquidGlassRefractionHeight(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassRefractionHeightKey))
    actual fun loadLiquidGlassRefractionAmount(): Float? = loadFloat(liquidGlassRefractionAmountKey)
    actual fun saveLiquidGlassRefractionAmount(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassRefractionAmountKey))
    actual fun loadLiquidGlassChromaticAberration(): Float? = loadFloat(liquidGlassChromaticAberrationKey)
    actual fun saveLiquidGlassChromaticAberration(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassChromaticAberrationKey))
    actual fun loadLiquidGlassDepthEffect(): Float? = loadFloat(liquidGlassDepthEffectKey)
    actual fun saveLiquidGlassDepthEffect(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassDepthEffectKey))
    actual fun loadLiquidGlassSurfaceTint(): String? = NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(liquidGlassSurfaceTintKey))
    actual fun saveLiquidGlassSurfaceTint(value: String) = NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(liquidGlassSurfaceTintKey))
    actual fun loadLiquidGlassSurfaceOpacity(): Float? = loadFloat(liquidGlassSurfaceOpacityKey)
    actual fun saveLiquidGlassSurfaceOpacity(value: Float) = NSUserDefaults.standardUserDefaults.setDouble(value.toDouble(), forKey = ProfileScopedKey.of(liquidGlassSurfaceOpacityKey))
    actual fun loadLiquidGlassTextColor(): String? = NSUserDefaults.standardUserDefaults.stringForKey(ProfileScopedKey.of(liquidGlassTextColorKey))
    actual fun saveLiquidGlassTextColor(value: String) = NSUserDefaults.standardUserDefaults.setObject(value, forKey = ProfileScopedKey.of(liquidGlassTextColorKey))

    private fun loadBool(key: String): Boolean? {
        val defaults = NSUserDefaults.standardUserDefaults
        val scoped = ProfileScopedKey.of(key)
        return if (defaults.objectForKey(scoped) != null) defaults.boolForKey(scoped) else null
    }

    private fun loadFloat(key: String): Float? {
        val defaults = NSUserDefaults.standardUserDefaults
        val scoped = ProfileScopedKey.of(key)
        return if (defaults.objectForKey(scoped) != null) defaults.doubleForKey(scoped).toFloat() else null
    }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadSelectedTheme()?.let { put(selectedThemeKey, encodeSyncString(it)) }
        loadThemeMode()?.let { put(themeModeKey, encodeSyncString(it)) }
        loadAmoledEnabled()?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
        loadLiquidGlassNativeTabBarEnabled()?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
        loadNavBarStyle()?.let { put(navBarStyleKey, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        profileScopedSyncKeys.forEach { key ->
            NSUserDefaults.standardUserDefaults.removeObjectForKey(ProfileScopedKey.of(key))
        }

        payload.decodeSyncString(selectedThemeKey)?.let(::saveSelectedTheme)
        payload.decodeSyncString(themeModeKey)?.let(::saveThemeMode)
        payload.decodeSyncBoolean(amoledEnabledKey)?.let(::saveAmoledEnabled)
        payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let(::saveLiquidGlassNativeTabBarEnabled)
        payload.decodeSyncString(navBarStyleKey)?.let(::saveNavBarStyle)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }
}
