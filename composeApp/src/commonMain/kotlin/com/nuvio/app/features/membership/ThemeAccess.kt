package com.nuvio.app.features.membership

import com.nuvio.app.core.ui.AppTheme

private val supporterThemes = linkedMapOf(
    AppTheme.GOLD to CosmeticEntitlement.GOLD_THEME,
    AppTheme.JADE to CosmeticEntitlement.JADE_THEME,
    AppTheme.ROSE_GOLD to CosmeticEntitlement.ROSE_GOLD_THEME,
    AppTheme.ARCTIC_BLUE to CosmeticEntitlement.ARCTIC_BLUE_THEME,
    AppTheme.GRAPHITE to CosmeticEntitlement.GRAPHITE_THEME,
)

private val standardThemes = listOf(AppTheme.WHITE) + AppTheme.entries.filterNot {
    it == AppTheme.WHITE || it in supporterThemes
}

fun availableAppThemes(entitlements: CosmeticEntitlements): List<AppTheme> {
    val supporter = supporterThemes
        .filterValues(entitlements::includes)
        .keys
        .toList()
    // The user's own colour is a mixing tool, not a cosmetic unlock, so it is always available
    // (listed last, after every shipped palette).
    return supporter + standardThemes + AppTheme.CUSTOM
}

fun resolveAppTheme(
    selectedTheme: AppTheme?,
    entitlements: CosmeticEntitlements,
): AppTheme {
    if (selectedTheme == null) {
        return supporterThemes
            .filterValues(entitlements::includes)
            .keys
            .firstOrNull()
            ?: AppTheme.WHITE
    }
    return selectedTheme.takeIf { it in availableAppThemes(entitlements) } ?: AppTheme.WHITE
}
