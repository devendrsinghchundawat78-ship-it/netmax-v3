@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.utils

/**
 * Small HLS master playlist reader with the same shape as the media3 based
 * parser upstream uses, but without any Android player dependency: providers
 * only need the variant list (url + resolution + codecs) to pick a stream.
 */
object HlsPlaylistParser {
    data class Format(
        val height: Int = -1,
        val width: Int = -1,
        val averageBitrate: Int = -1,
        val codecs: String? = null
    )

    data class Variant(
        val url: String,
        val format: Format,
        val audioGroupId: String? = null,
        val videoGroupId: String? = null,
        val subtitlesGroupId: String? = null,
        val isTrickPlayVideo: Boolean = false
    ) {
        fun containsAudio(): Boolean = audioGroupId != null || videoGroupId == null

        fun isTrickPlay(): Boolean = isTrickPlayVideo

        /** Usable on its own: audio either lives in the segment or is referenced but present. */
        fun isPlayableStandalone(master: MasterPlaylist): Boolean =
            !isTrickPlay() && (containsAudio() || master.audioGroups.contains(audioGroupId))

        override fun toString(): String = url
    }

    data class MasterPlaylist(
        val variants: List<Variant>,
        val audioGroups: Set<String?> = emptySet(),
        val subtitleGroups: Set<String?> = emptySet()
    )

    private val RESOLUTION = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val BANDWIDTH = Regex("""BANDWIDTH=(\d+)""")
    private val CODECS = Regex("""CODECS="([^"]+)"""")
    private val GROUP_ID = Regex("""GROUP-ID="([^"]+)"""")
    private val TYPE_ATTR = Regex("""TYPE=([A-Z-]+)""")
    private val CHARACTERISTICS = Regex("""CHARACTERISTICS="([^"]+)"""")
    private val URI_ATTR = Regex("""URI="([^"]*)"""")

    /** @return null when [text] is not a master playlist (no #EXT-X-STREAM-INF). */
    fun parse(url: String, text: String): MasterPlaylist? {
        if (!text.contains("#EXTM3U")) return null
        val lines = text.lines()
        var pendingAttr: String? = null
        val trickPlayUris = mutableSetOf<String>()
        val audioGroups = mutableSetOf<String?>()
        val subtitleGroups = mutableSetOf<String?>()

        // first pass: media tags, to know which video groups are "trick play"
        for (raw in lines) {
            val line = raw.trim()
            if (!line.startsWith("#EXT-X-MEDIA:")) continue
            val characteristics = CHARACTERISTICS.find(line)?.groupValues?.get(1).orEmpty()
            val uri = URI_ATTR.find(line)?.groupValues?.get(1)
            val type = TYPE_ATTR.find(line)?.groupValues?.get(1)
            if (type == "VIDEO" && uri != null && characteristics.contains("trick-play", true)) {
                trickPlayUris.add(uri)
            }
            when (type) {
                "AUDIO" -> line.substringAfter("GROUP-ID=", "").takeWhile { it != ',' && it != '"' }
                    .let { audioGroups.add(it.trim('"')) }
                "SUBTITLES" -> line.substringAfter("GROUP-ID=", "").takeWhile { it != ',' && it != '"' }
                    .let { subtitleGroups.add(it.trim('"')) }
            }
        }

        val variants = mutableListOf<Variant>()
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pendingAttr = line.removePrefix("#EXT-X-STREAM-INF:")
                continue
            }
            if (line.isEmpty() || line.startsWith("#")) continue
            val attr = pendingAttr ?: continue
            pendingAttr = null

            val height = RESOLUTION.find(attr)?.groupValues?.get(2)?.toIntOrNull() ?: -1
            val width = RESOLUTION.find(attr)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val bitrate = BANDWIDTH.find(attr)?.groupValues?.get(1)?.toIntOrNull() ?: -1
            val codecs = CODECS.find(attr)?.groupValues?.get(1)
            val audioGroup = attr.substringAfter("AUDIO=", "").substringBefore(",").trim('"').takeIf { it.isNotEmpty() }
            val videoGroup = attr.substringAfter("VIDEO=", "").substringBefore(",").trim('"').takeIf { it.isNotEmpty() }
            val subs = attr.substringAfter("SUBTITLES=", "").substringBefore(",").trim('"').takeIf { it.isNotEmpty() }
            val variantUrl = resolveUrl(url, line)
            variants.add(
                Variant(
                    url = variantUrl,
                    format = Format(height = height, width = width, averageBitrate = bitrate, codecs = codecs),
                    audioGroupId = audioGroup,
                    videoGroupId = videoGroup,
                    subtitlesGroupId = subs,
                    isTrickPlayVideo = videoGroup in trickPlayUris || line in trickPlayUris
                )
            )
        }
        if (variants.isEmpty()) return null
        return MasterPlaylist(variants, audioGroups, subtitleGroups)
    }

    /** Turns a relative playlist uri into an absolute url. */
    fun resolveUrl(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        if (ref.startsWith("//")) return "https:$ref"
        val schemeEnd = baseUrl.indexOf("://")
        if (schemeEnd < 0) return ref
        val authority = baseUrl.substringAfter("://").substringBefore("/")
        return if (ref.startsWith("/")) {
            baseUrl.substring(0, schemeEnd + 3) + authority + ref
        } else {
            baseUrl.substringBeforeLast("/", "") + "/" + ref
        }
    }
}
