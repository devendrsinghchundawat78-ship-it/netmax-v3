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

data class AudioFile(
    val url: String,
    val lang: String? = null,
)

open class ExtractorLink(
    open var source: String = "",
    open var name: String = "",
    open var url: String = "",
    open var referer: String = "",
    open var quality: Int = Qualities.Unknown.value,
    open var headers: Map<String, String> = emptyMap(),
    open var extractorData: String? = null,
    open var type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    open var audioTracks: List<AudioFile> = emptyList(),
) {
    open val isM3u8: Boolean get() = type == ExtractorLinkType.M3U8
    open val isDash: Boolean get() = type == ExtractorLinkType.DASH

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        isM3u8: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
    )

    constructor(
        source: String,
        name: String,
        url: String,
        referer: String,
        quality: Int,
        type: ExtractorLinkType,
        headers: Map<String, String> = emptyMap(),
        extractorData: String? = null,
    ) : this(
        source = source,
        name = name,
        url = url,
        referer = referer,
        quality = quality,
        headers = headers,
        extractorData = extractorData,
        type = type,
    )

    fun getAllHeaders(): Map<String, String> {
        if (referer.isBlank()) {
            return headers
        } else if (headers.keys.none { it.equals("referer", ignoreCase = true) }) {
            return headers + mapOf("referer" to referer)
        }
        return headers
    }
}
