package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile

abstract class ExtractorApi {
    abstract val name: String
    abstract val mainUrl: String
    abstract val requiresReferer: Boolean

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val links = getUrl(url, referer)
        links?.forEach(callback)
    }

    open suspend fun getUrl(
        url: String,
        referer: String? = null,
    ): List<ExtractorLink>? = null

    open suspend fun getSafeUrl(
        url: String,
        referer: String? = null,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            getUrl(url, referer, subtitleCallback, callback)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}

val INFER_TYPE: ExtractorLinkType = ExtractorLinkType.VIDEO

val extractorApis = mutableListOf<ExtractorApi>()

suspend fun loadExtractor(
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val cleanUrl = url.trim()
    val extractor = extractorApis.firstOrNull { cleanUrl.startsWith(it.mainUrl) }
    return if (extractor != null) {
        extractor.getSafeUrl(cleanUrl, referer, subtitleCallback, callback)
        true
    } else {
        callback(
            ExtractorLink(
                source = "Stream Link",
                name = "Stream Link",
                url = cleanUrl,
                referer = referer ?: "",
                quality = Qualities.Unknown.value,
            )
        )
        true
    }
}

suspend fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean = loadExtractor(url, null, subtitleCallback, callback)

suspend fun newExtractorLink(
    source: String,
    name: String,
    url: String,
    type: ExtractorLinkType = ExtractorLinkType.VIDEO,
    block: suspend ExtractorLink.() -> Unit = {}
): ExtractorLink {
    val link = ExtractorLink(
        source = source,
        name = name,
        url = url,
        referer = "",
        quality = Qualities.Unknown.value,
        type = type
    )
    link.block()
    return link
}

fun getQualityFromName(qualityName: String?): Int {
    if (qualityName == null) return Qualities.Unknown.value
    return when {
        qualityName.contains("4k", ignoreCase = true) || qualityName.contains("2160", ignoreCase = true) -> Qualities.P2160.value
        qualityName.contains("1440", ignoreCase = true) -> Qualities.P1440.value
        qualityName.contains("1080", ignoreCase = true) -> Qualities.P1080.value
        qualityName.contains("720", ignoreCase = true) -> Qualities.P720.value
        qualityName.contains("480", ignoreCase = true) -> Qualities.P480.value
        qualityName.contains("360", ignoreCase = true) -> Qualities.P360.value
        qualityName.contains("240", ignoreCase = true) -> Qualities.P240.value
        else -> Qualities.Unknown.value
    }
}
