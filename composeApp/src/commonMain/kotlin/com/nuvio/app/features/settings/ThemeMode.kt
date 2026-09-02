package com.nuvio.app.features.settings

enum class ThemeMode(val key: String) {
    DARK("dark"),
    LIGHT("light");

    companion object {
        fun fromKey(key: String?): ThemeMode =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: DARK
    }
}
