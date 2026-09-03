package com.nuvio.app.features.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nuvio.app.core.sync.decodeSyncBoolean
import com.nuvio.app.core.sync.decodeSyncString
import com.nuvio.app.core.sync.encodeSyncBoolean
import com.nuvio.app.core.sync.encodeSyncString
import com.nuvio.app.core.storage.ProfileScopedKey
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

actual object ThemeSettingsStorage {
    private const val preferencesName = "nuvio_theme_settings"
    private const val selectedThemeKey = "selected_theme"
    private const val themeModeKey = "theme_mode"
    private const val amoledEnabledKey = "amoled_enabled"
    private const val liquidGlassNativeTabBarEnabledKey = "liquid_glass_native_tab_bar_enabled"
    private const val selectedAppLanguageKey = "selected_app_language"
    private const val NAV_BAR_STYLE_KEY = "nav_bar_style"
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
        NAV_BAR_STYLE_KEY,
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

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }

    actual fun loadSelectedTheme(): String? =
        preferences?.getString(ProfileScopedKey.of(selectedThemeKey), null)

    actual fun saveSelectedTheme(themeName: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(selectedThemeKey), themeName)
            ?.apply()
    }

    actual fun loadThemeMode(): String? =
        preferences?.getString(ProfileScopedKey.of(themeModeKey), null)

    actual fun saveThemeMode(modeKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(themeModeKey), modeKey)
            ?.apply()
    }

    actual fun loadAmoledEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(amoledEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveAmoledEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(amoledEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadLiquidGlassNativeTabBarEnabled(): Boolean? =
        preferences?.let { prefs ->
            val key = ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey)
            if (prefs.contains(key)) prefs.getBoolean(key, false) else null
        }

    actual fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean) {
        preferences
            ?.edit()
            ?.putBoolean(ProfileScopedKey.of(liquidGlassNativeTabBarEnabledKey), enabled)
            ?.apply()
    }

    actual fun loadSelectedAppLanguage(): String? {
        val value = preferences?.getString(selectedAppLanguageKey, null)
        if (value != null) return value
        val legacy = preferences?.getString(ProfileScopedKey.of(selectedAppLanguageKey), null)
        if (legacy != null) saveSelectedAppLanguage(legacy)
        return legacy
    }

    actual fun saveSelectedAppLanguage(languageCode: String) {
        preferences
            ?.edit()
            ?.putString(selectedAppLanguageKey, languageCode)
            ?.apply()
    }

    actual fun applySelectedAppLanguage(languageCode: String) {
        if (languageCode.equals("device", ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(languageCode),
            )
        }
    }

    actual fun loadNavBarStyle(): String? =
        preferences?.getString(ProfileScopedKey.of(NAV_BAR_STYLE_KEY), null)

    actual fun saveNavBarStyle(styleKey: String) {
        preferences
            ?.edit()
            ?.putString(ProfileScopedKey.of(NAV_BAR_STYLE_KEY), styleKey)
            ?.apply()
    }

    actual fun loadLiquidGlassEnabled(): Boolean? = preferences?.let { prefs ->
        val key = ProfileScopedKey.of(liquidGlassEnabledKey)
        if (prefs.contains(key)) prefs.getBoolean(key, true) else null
    }
    actual fun saveLiquidGlassEnabled(enabled: Boolean) { preferences?.edit()?.putBoolean(ProfileScopedKey.of(liquidGlassEnabledKey), enabled)?.apply() }
    actual fun loadLiquidGlassVibrancy(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassVibrancyKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassVibrancy(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassVibrancyKey), value)?.apply() }
    actual fun loadLiquidGlassBlurRadius(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassBlurRadiusKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassBlurRadius(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassBlurRadiusKey), value)?.apply() }
    actual fun loadLiquidGlassRefractionHeight(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassRefractionHeightKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassRefractionHeight(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassRefractionHeightKey), value)?.apply() }
    actual fun loadLiquidGlassRefractionAmount(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassRefractionAmountKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassRefractionAmount(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassRefractionAmountKey), value)?.apply() }
    actual fun loadLiquidGlassChromaticAberration(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassChromaticAberrationKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassChromaticAberration(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassChromaticAberrationKey), value)?.apply() }
    actual fun loadLiquidGlassDepthEffect(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassDepthEffectKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassDepthEffect(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassDepthEffectKey), value)?.apply() }
    actual fun loadLiquidGlassSurfaceTint(): String? = preferences?.getString(ProfileScopedKey.of(liquidGlassSurfaceTintKey), null)
    actual fun saveLiquidGlassSurfaceTint(value: String) { preferences?.edit()?.putString(ProfileScopedKey.of(liquidGlassSurfaceTintKey), value)?.apply() }
    actual fun loadLiquidGlassSurfaceOpacity(): Float? = preferences?.let { prefs -> prefs.getFloat(ProfileScopedKey.of(liquidGlassSurfaceOpacityKey), Float.NaN).takeUnless { it.isNaN() } }
    actual fun saveLiquidGlassSurfaceOpacity(value: Float) { preferences?.edit()?.putFloat(ProfileScopedKey.of(liquidGlassSurfaceOpacityKey), value)?.apply() }
    actual fun loadLiquidGlassTextColor(): String? = preferences?.getString(ProfileScopedKey.of(liquidGlassTextColorKey), null)
    actual fun saveLiquidGlassTextColor(value: String) { preferences?.edit()?.putString(ProfileScopedKey.of(liquidGlassTextColorKey), value)?.apply() }

    actual fun exportToSyncPayload(): JsonObject = buildJsonObject {
        loadSelectedTheme()?.let { put(selectedThemeKey, encodeSyncString(it)) }
        loadThemeMode()?.let { put(themeModeKey, encodeSyncString(it)) }
        loadAmoledEnabled()?.let { put(amoledEnabledKey, encodeSyncBoolean(it)) }
        loadLiquidGlassNativeTabBarEnabled()?.let { put(liquidGlassNativeTabBarEnabledKey, encodeSyncBoolean(it)) }
        loadNavBarStyle()?.let { put(NAV_BAR_STYLE_KEY, encodeSyncString(it)) }
    }

    actual fun replaceFromSyncPayload(payload: JsonObject) {
        preferences?.edit()?.apply {
            profileScopedSyncKeys.forEach { remove(ProfileScopedKey.of(it)) }
        }?.apply()

        payload.decodeSyncString(selectedThemeKey)?.let(::saveSelectedTheme)
        payload.decodeSyncString(themeModeKey)?.let(::saveThemeMode)
        payload.decodeSyncBoolean(amoledEnabledKey)?.let(::saveAmoledEnabled)
        payload.decodeSyncBoolean(liquidGlassNativeTabBarEnabledKey)?.let(::saveLiquidGlassNativeTabBarEnabled)
        payload.decodeSyncString(NAV_BAR_STYLE_KEY)?.let(::saveNavBarStyle)
        applySelectedAppLanguage(loadSelectedAppLanguage() ?: AppLanguage.DEVICE.code)
    }
}
