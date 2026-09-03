package com.nuvio.app.features.settings

import kotlinx.serialization.json.JsonObject

internal expect object ThemeSettingsStorage {
    fun loadSelectedTheme(): String?
    fun loadThemeMode(): String?
    fun saveThemeMode(modeKey: String)
    fun saveSelectedTheme(themeName: String)
    fun loadAmoledEnabled(): Boolean?
    fun saveAmoledEnabled(enabled: Boolean)
    fun loadLiquidGlassNativeTabBarEnabled(): Boolean?
    fun saveLiquidGlassNativeTabBarEnabled(enabled: Boolean)
    fun loadSelectedAppLanguage(): String?
    fun saveSelectedAppLanguage(languageCode: String)
    fun applySelectedAppLanguage(languageCode: String)
    fun loadNavBarStyle(): String?
    fun saveNavBarStyle(styleKey: String)
    fun loadLiquidGlassEnabled(): Boolean?
    fun saveLiquidGlassEnabled(enabled: Boolean)
    fun loadLiquidGlassVibrancy(): Float?
    fun saveLiquidGlassVibrancy(value: Float)
    fun loadLiquidGlassBlurRadius(): Float?
    fun saveLiquidGlassBlurRadius(value: Float)
    fun loadLiquidGlassRefractionHeight(): Float?
    fun saveLiquidGlassRefractionHeight(value: Float)
    fun loadLiquidGlassRefractionAmount(): Float?
    fun saveLiquidGlassRefractionAmount(value: Float)
    fun loadLiquidGlassChromaticAberration(): Float?
    fun saveLiquidGlassChromaticAberration(value: Float)
    fun loadLiquidGlassDepthEffect(): Float?
    fun saveLiquidGlassDepthEffect(value: Float)
    fun loadLiquidGlassSurfaceTint(): String?
    fun saveLiquidGlassSurfaceTint(value: String)
    fun loadLiquidGlassSurfaceOpacity(): Float?
    fun saveLiquidGlassSurfaceOpacity(value: Float)
    fun loadLiquidGlassTextColor(): String?
    fun saveLiquidGlassTextColor(value: String)
    fun exportToSyncPayload(): JsonObject
    fun replaceFromSyncPayload(payload: JsonObject)
}
