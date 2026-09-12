package com.nuvio.app.features.settings

import com.nuvio.app.core.ui.AppTheme
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

internal val AppIconOption.labelResource: StringResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.string.settings_appearance_app_icon_original
        AppIconOption.ARCTIC_BLUE -> Res.string.settings_appearance_app_icon_arctic_blue
        AppIconOption.EMERALD -> Res.string.settings_appearance_app_icon_emerald
        AppIconOption.ROSE_GOLD -> Res.string.settings_appearance_app_icon_rose_gold
        AppIconOption.COPPER -> Res.string.settings_appearance_app_icon_copper
        AppIconOption.GRAPHITE -> Res.string.settings_appearance_app_icon_graphite
        AppIconOption.CRIMSON -> Res.string.settings_appearance_app_icon_crimson
        AppIconOption.VIOLET -> Res.string.settings_appearance_app_icon_violet
        AppIconOption.TEAL -> Res.string.settings_appearance_app_icon_teal
        AppIconOption.MAGENTA -> Res.string.settings_appearance_app_icon_magenta
        AppIconOption.AMBER -> Res.string.settings_appearance_app_icon_amber
    }

internal val AppIconOption.previewResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.app_icon_original
        AppIconOption.ARCTIC_BLUE -> Res.drawable.app_icon_arctic_blue
        AppIconOption.EMERALD -> Res.drawable.app_icon_emerald
        AppIconOption.ROSE_GOLD -> Res.drawable.app_icon_rose_gold
        AppIconOption.COPPER -> Res.drawable.app_icon_copper
        AppIconOption.GRAPHITE -> Res.drawable.app_icon_graphite
        AppIconOption.CRIMSON -> Res.drawable.app_icon_crimson
        AppIconOption.VIOLET -> Res.drawable.app_icon_violet
        AppIconOption.TEAL -> Res.drawable.app_icon_teal
        AppIconOption.MAGENTA -> Res.drawable.app_icon_magenta
        AppIconOption.AMBER -> Res.drawable.app_icon_amber
    }

internal val AppIconOption.wordmarkResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.app_logo_wordmark_original
        AppIconOption.ARCTIC_BLUE -> Res.drawable.app_logo_wordmark_arctic_blue
        AppIconOption.EMERALD -> Res.drawable.app_logo_wordmark_emerald
        AppIconOption.ROSE_GOLD -> Res.drawable.app_logo_wordmark_rose_gold
        AppIconOption.COPPER -> Res.drawable.app_logo_wordmark_copper
        AppIconOption.GRAPHITE -> Res.drawable.app_logo_wordmark_graphite
        AppIconOption.CRIMSON -> Res.drawable.app_logo_wordmark_crimson
        AppIconOption.VIOLET -> Res.drawable.app_logo_wordmark_violet
        AppIconOption.TEAL -> Res.drawable.app_logo_wordmark_teal
        AppIconOption.MAGENTA -> Res.drawable.app_logo_wordmark_magenta
        AppIconOption.AMBER -> Res.drawable.app_logo_wordmark_amber
    }

internal fun AppTheme.wordmarkResource(fallback: AppIconOption): DrawableResource =
    when (this) {
        AppTheme.GOLD -> Res.drawable.app_logo_wordmark_gold
        AppTheme.JADE -> AppIconOption.EMERALD.wordmarkResource
        AppTheme.ROSE_GOLD -> AppIconOption.ROSE_GOLD.wordmarkResource
        AppTheme.ARCTIC_BLUE -> AppIconOption.ARCTIC_BLUE.wordmarkResource
        AppTheme.GRAPHITE -> AppIconOption.GRAPHITE.wordmarkResource
        else -> fallback.wordmarkResource
    }

/**
 * The in-app NetMax brand logo for a given icon choice: the classic red wordmark
 * for the default option, colour-matched NetMax wordmark variants otherwise.
 */
internal val AppIconOption.brandWordmarkResource: DrawableResource
    get() = when (this) {
        AppIconOption.ORIGINAL -> Res.drawable.netmax_logo
        AppIconOption.ARCTIC_BLUE -> Res.drawable.netmax_logo_arctic_blue
        AppIconOption.EMERALD -> Res.drawable.netmax_logo_emerald
        AppIconOption.ROSE_GOLD -> Res.drawable.netmax_logo_rose_gold
        AppIconOption.COPPER -> Res.drawable.netmax_logo_copper
        AppIconOption.GRAPHITE -> Res.drawable.netmax_logo_graphite
        AppIconOption.CRIMSON -> Res.drawable.netmax_logo_crimson
        AppIconOption.VIOLET -> Res.drawable.netmax_logo_violet
        AppIconOption.TEAL -> Res.drawable.netmax_logo_teal
        AppIconOption.MAGENTA -> Res.drawable.netmax_logo_magenta
        AppIconOption.AMBER -> Res.drawable.netmax_logo_amber
    }
