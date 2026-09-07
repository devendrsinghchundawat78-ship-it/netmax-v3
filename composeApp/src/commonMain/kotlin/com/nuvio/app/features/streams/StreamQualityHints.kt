package com.nuvio.app.features.streams

/**
 * Resolution/quality parsing for a source stream.
 *
 * Single implementation shared by auto play (data-saver pick) and the download
 * source picker, so both screens describe the exact same source identically.
 */
object StreamQualityHints {
    val QUALITY_PATTERNS: List<Pair<Regex, Int>> = listOf(
        Regex("\\b(4320p|8k)\\b") to 8,
        Regex("\\b(2160p|4k|uhd)\\b") to 7,
        Regex("\\b1440p\\b") to 6,
        Regex("\\b1080p\\b|full[ .-]?hd") to 5,
        Regex("\\b720p\\b|hd") to 4,
        Regex("\\b576p\\b") to 3,
        Regex("\\b480p\\b|sd") to 2,
    )

    /** Highest score = highest resolution; unknown/unnamed sources get 1. */
    fun scoreOf(stream: StreamItem): Int {
        val text = buildString {
            append(stream.name.orEmpty()).append(' ')
            append(stream.title.orEmpty()).append(' ')
            append(stream.description.orEmpty()).append(' ')
            append(stream.behaviorHints.filename.orEmpty()).append(' ')
            append(stream.clientResolve?.stream?.raw?.filename.orEmpty()).append(' ')
            append(stream.clientResolve?.stream?.raw?.parsed?.resolution.orEmpty())
        }.lowercase()
        return QUALITY_PATTERNS.firstOrNull { (regex, _) -> regex.containsMatchIn(text) }?.second ?: 1
    }

    fun labelOf(score: Int): String = when (score) {
        8 -> "8K"
        7 -> "4K"
        6 -> "1440p"
        5 -> "1080p"
        4 -> "720p"
        3 -> "576p"
        2 -> "480p"
        else -> "Unknown"
    }
}
