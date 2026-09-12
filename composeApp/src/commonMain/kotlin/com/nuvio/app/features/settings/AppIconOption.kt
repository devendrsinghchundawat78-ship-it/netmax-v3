package com.nuvio.app.features.settings

internal enum class AppIconOption(
    val key: String,
    val platformName: String?,
) {
    ORIGINAL(
        key = "original",
        platformName = null,
    ),
    ARCTIC_BLUE(
        key = "arctic_blue",
        platformName = "AppIconArcticBlue",
    ),
    EMERALD(
        key = "emerald",
        platformName = "AppIconEmerald",
    ),
    ROSE_GOLD(
        key = "rose_gold",
        platformName = "AppIconRoseGold",
    ),
    COPPER(
        key = "copper",
        platformName = "AppIconCopper",
    ),
    GRAPHITE(
        key = "graphite",
        platformName = "AppIconGraphite",
    ),
    CRIMSON(
        key = "crimson",
        platformName = "AppIconCrimson",
    ),
    VIOLET(
        key = "violet",
        platformName = "AppIconViolet",
    ),
    TEAL(
        key = "teal",
        platformName = "AppIconTeal",
    ),
    MAGENTA(
        key = "magenta",
        platformName = "AppIconMagenta",
    ),
    AMBER(
        key = "amber",
        platformName = "AppIconAmber",
    ),
    ;

    companion object {
        fun fromPlatformName(name: String?): AppIconOption =
            entries.firstOrNull { it.platformName == name } ?: ORIGINAL
    }
}
