package com.lagradost.cloudstream3.utils

class M3u8Helper {
    companion object {
        suspend fun generateM3u8(
            source: String,
            streamUrl: String,
            referer: String,
            quality: Int? = null,
            headers: Map<String, String> = emptyMap(),
            name: String? = null
        ): List<ExtractorLink> {
            return listOf(
                ExtractorLink(
                    source = source,
                    name = name ?: source,
                    url = streamUrl,
                    referer = referer,
                    quality = quality ?: Qualities.Unknown.value,
                    type = ExtractorLinkType.M3U8,
                    headers = headers,
                    isM3u8 = true
                )
            )
        }
    }
}
