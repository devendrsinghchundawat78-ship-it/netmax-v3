package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import com.nuvio.app.core.ui.NativeTabBridge
import com.nuvio.app.core.ui.ThemeColors
import com.nuvio.app.core.ui.ThemeCustomColor
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.resolveAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object ThemeSettingsRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _selectedThemePreference = MutableStateFlow<AppTheme?>(null)
    val selectedThemePreference: StateFlow<AppTheme?> = _selectedThemePreference.asStateFlow()
    private val _selectedTheme = MutableStateFlow(AppTheme.WHITE)
    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    private val _amoledEnabled = MutableStateFlow(false)
    val amoledEnabled: StateFlow<Boolean> = _amoledEnabled.asStateFlow()

    /**
     * The colour behind [AppTheme.CUSTOM] as `#RRGGBB`. Reading it always gives a usable colour:
     * an empty or malformed stored value falls back to [ThemeCustomColor.DEFAULT_ACCENT_HEX].
     */
    private val _customThemeAccentHex = MutableStateFlow(ThemeCustomColor.DEFAULT_ACCENT_HEX)
    val customThemeAccentHex: StateFlow<String> = _customThemeAccentHex.asStateFlow()

    private val _liquidGlassNativeTabBarEnabled = MutableStateFlow(true)
    val liquidGlassNativeTabBarEnabled: StateFlow<Boolean> = _liquidGlassNativeTabBarEnabled.asStateFlow()

    private val _selectedAppLanguage = MutableStateFlow(AppLanguage.DEVICE)
    val selectedAppLanguage: StateFlow<AppLanguage> = _selectedAppLanguage.asStateFlow()

    private val _navBarStyle = MutableStateFlow(NavBarStyle.ADAPTIVE)
    val navBarStyle: StateFlow<NavBarStyle> = _navBarStyle.asStateFlow()

    private var hasLoaded = false
    private var observesMembership = false

    fun ensureLoaded() {
        observeMembership()
        if (hasLoaded) return
        loadFromDisk()
    }

    fun onProfileChanged() {
        loadFromDisk()
        LiquidGlassSettingsRepository.onProfileChanged()
    }

    fun clearLocalState() {
        hasLoaded = false
        _selectedThemePreference.value = null
        _selectedTheme.value = AppTheme.WHITE
        _customThemeAccentHex.value = ThemeCustomColor.DEFAULT_ACCENT_HEX
        ThemeCustomColor.accentHex = ThemeCustomColor.DEFAULT_ACCENT_HEX
        _themeMode.value = ThemeMode.DARK
        _amoledEnabled.value = false
        _liquidGlassNativeTabBarEnabled.value = false
        NativeTabBridge.publishAccentColor(AppTheme.WHITE.nativeTabAccentHex())
        NativeTabBridge.publishLiquidGlassEnabled(false)
        _selectedAppLanguage.value = AppLanguage.DEVICE
        _navBarStyle.value = NavBarStyle.ADAPTIVE
        LiquidGlassSettingsRepository.clearLocalState()
    }

    private fun loadFromDisk() {
        hasLoaded = true
        val stored = ThemeSettingsStorage.loadSelectedTheme()
        val theme = if (stored != null) {
            try {
                AppTheme.valueOf(stored)
            } catch (_: IllegalArgumentException) {
                null
            }
        } else {
            null
        }
        val customAccent = ThemeCustomColor.rgbHexOf(ThemeSettingsStorage.loadCustomThemeAccent())
        _customThemeAccentHex.value = customAccent?.let { "#$it" } ?: ThemeCustomColor.DEFAULT_ACCENT_HEX
        ThemeCustomColor.accentHex = _customThemeAccentHex.value
        _selectedThemePreference.value = theme
        applyEffectiveTheme()
        _themeMode.value = ThemeMode.fromKey(ThemeSettingsStorage.loadThemeMode())
        _amoledEnabled.value = ThemeSettingsStorage.loadAmoledEnabled() ?: false
        val liquidGlassEnabled = ThemeSettingsStorage.loadLiquidGlassNativeTabBarEnabled() ?: true
        _liquidGlassNativeTabBarEnabled.value = liquidGlassEnabled
        NativeTabBridge.publishLiquidGlassEnabled(liquidGlassEnabled)
        val appLanguage = AppLanguage.fromCode(ThemeSettingsStorage.loadSelectedAppLanguage())
        ThemeSettingsStorage.applySelectedAppLanguage(appLanguage.code)
        _selectedAppLanguage.value = appLanguage
        _navBarStyle.value = NavBarStyle.fromKey(ThemeSettingsStorage.loadNavBarStyle())
    }

    fun setTheme(theme: AppTheme) {
        ensureLoaded()
        if (_selectedThemePreference.value == theme) return
        _selectedThemePreference.value = theme
        ThemeSettingsStorage.saveSelectedTheme(theme.name)
        applyEffectiveTheme()
    }

    /**
     * Stores the user's own theme colour. The value is normalised to `#RRGGBB`; anything that is not a
     * colour is rejected so a typo in the picker can never leave the app with a broken theme.
     */
    fun setCustomThemeAccent(accentHex: String) {
        ensureLoaded()
        val normalized = ThemeCustomColor.rgbHexOf(accentHex) ?: return
        val stored = "#$normalized"
        if (_customThemeAccentHex.value == stored) return
        _customThemeAccentHex.value = stored
        ThemeCustomColor.accentHex = stored
        ThemeSettingsStorage.saveCustomThemeAccent(stored)
        if (_selectedTheme.value == AppTheme.CUSTOM) {
            NativeTabBridge.publishAccentColor(stored)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        ensureLoaded()
        if (_themeMode.value == mode) return
        _themeMode.value = mode
        ThemeSettingsStorage.saveThemeMode(mode.key)
        if (mode == ThemeMode.LIGHT && _amoledEnabled.value) {
            _amoledEnabled.value = false
            ThemeSettingsStorage.saveAmoledEnabled(false)
        }
    }

    fun setAmoled(enabled: Boolean) {
        ensureLoaded()
        if (_amoledEnabled.value == enabled) return
        _amoledEnabled.value = enabled
        ThemeSettingsStorage.saveAmoledEnabled(enabled)
    }

    fun setLiquidGlassNativeTabBar(enabled: Boolean) {
        ensureLoaded()
        if (_liquidGlassNativeTabBarEnabled.value == enabled) return
        _liquidGlassNativeTabBarEnabled.value = enabled
        ThemeSettingsStorage.saveLiquidGlassNativeTabBarEnabled(enabled)
        NativeTabBridge.publishLiquidGlassEnabled(enabled)
    }

    fun setAppLanguage(language: AppLanguage) {
        ensureLoaded()
        if (_selectedAppLanguage.value == language) return
        ThemeSettingsStorage.saveSelectedAppLanguage(language.code)
        ThemeSettingsStorage.applySelectedAppLanguage(language.code)
        _selectedAppLanguage.value = language
    }

    fun setNavBarStyle(style: NavBarStyle) {
        ensureLoaded()
        if (_navBarStyle.value == style) return
        _navBarStyle.value = style
        ThemeSettingsStorage.saveNavBarStyle(style.key)
    }

    private fun observeMembership() {
        if (observesMembership) return
        observesMembership = true
        MemberAccessRepository.ensureStarted()
        scope.launch {
            MemberAccessRepository.access.collect {
                if (hasLoaded) applyEffectiveTheme()
            }
        }
    }

    private fun applyEffectiveTheme() {
        val effective = resolveAppTheme(
            selectedTheme = _selectedThemePreference.value,
            entitlements = MemberAccessRepository.access.value.entitlements,
        )
        _selectedTheme.value = effective
        ThemeCustomColor.accentHex = _customThemeAccentHex.value
        NativeTabBridge.publishAccentColor(effective.nativeTabAccentHex())
    }
}

private fun AppTheme.nativeTabAccentHex(): String =
    ThemeColors.getColorPalette(this).nativeAccentHex
