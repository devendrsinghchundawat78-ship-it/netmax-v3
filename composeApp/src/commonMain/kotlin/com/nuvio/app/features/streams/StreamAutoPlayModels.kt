package com.nuvio.app.features.streams

enum class StreamAutoPlayMode {
    MANUAL,
    FIRST_STREAM,
    REGEX_MATCH,
}

enum class StreamAutoPlaySource {
    ALL_SOURCES,
    INSTALLED_ADDONS_ONLY,
    ENABLED_PLUGINS_ONLY,

    /**
     * Data saver scope: prefer the smallest/SD source (480p) over the first, usually
     * highest quality, one. Driven by the "Auto pick 480p source" switch in Settings ->
     * Playback; the source-scope dialog never offers it, so it is not user-visible there.
     */
    LOWEST_QUALITY_SD,
}
