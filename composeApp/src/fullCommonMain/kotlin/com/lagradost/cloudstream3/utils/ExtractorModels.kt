package com.lagradost.cloudstream3.utils

enum class ExtractorLinkType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    TORRENT,
    M3U8,
    DASH,
    OTHER;

    companion object {
        fun fromMimeType(mimeType: String?): ExtractorLinkType = when (mimeType) {
            "application/x-mpegURL", "application/vnd.apple.mpegurl" -> M3U8
            "application/dash+xml" -> DASH
            else -> VIDEO
        }
    }
}

enum class Qualities(val value: Int) {
    Unknown(400),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P1440(1440),
    P2160(2160);

    companion object {
        fun getStringByInt(quality: Int?): String = when (quality) {
            144 -> "144p"
            240 -> "240p"
            360 -> "360p"
            480 -> "480p"
            720 -> "720p"
            1080 -> "1080p"
            1440 -> "1440p"
            2160 -> "4K"
            else -> "Auto"
        }
    }
}

data class ExtractorLink(
    var source: String = "",
    var name: String = "",
    var url: String = "",
    var referer: String = "",
    var quality: Int = Qualities.Unknown.value,
    var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    var headers: Map<String, String> = emptyMap(),
    var extractorData: String? = null,
    var isM3u8: Boolean = false,
    var isDash: Boolean = false,
) {
    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        extractorData: String? = null
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
        headers = headers,
        extractorData = extractorData,
        isM3u8 = isM3u8
    )
}
