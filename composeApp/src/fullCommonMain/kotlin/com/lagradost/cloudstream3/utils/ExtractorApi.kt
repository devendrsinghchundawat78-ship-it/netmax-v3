package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.SubtitleFile
import kotlin.math.pow

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

private val packedRegex = Regex("""eval\(function\(p,a,c,k,e,.*\)\)""")

fun getPacked(string: String): String? {
    return packedRegex.find(string)?.value
}

fun getAndUnpack(string: String): String {
    val packedText = getPacked(string) ?: return string
    return JsUnpacker(packedText).unpack() ?: string
}

class JsUnpacker(private val packedJS: String?) {

    fun detect(): Boolean {
        val js = packedJS?.replace(" ", "") ?: return false
        return Regex("""eval\(function\(p,a,c,k,e,[rd]""").containsMatchIn(js)
    }

    fun unpack(): String? {
        val js = packedJS ?: return null
        try {
            val match = Regex(
                """(?s)\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)"""
            ).find(js)
            if (match != null && match.groupValues.size == 5) {
                val payload = match.groupValues[1].replace("\\'", "'")
                val radixStr = match.groupValues[2]
                val countStr = match.groupValues[3]
                val symtab = match.groupValues[4].split("|").toTypedArray()
                var radix = 36
                var count = 0
                try {
                    radix = radixStr.toIntOrNull() ?: radix
                } catch (_: Exception) {
                }
                try {
                    count = countStr.toIntOrNull() ?: 0
                } catch (_: Exception) {
                }
                if (symtab.size != count) {
                    return null
                }
                val unbase = Unbase(radix)
                val wordRegex = Regex("""\b[a-zA-Z0-9_]+\b""")
                val decoded = StringBuilder(payload)
                var replaceOffset = 0
                wordRegex.findAll(payload).forEach { wordMatch ->
                    val word = wordMatch.value
                    val x = unbase.unbase(word)
                    val value = if (x in symtab.indices) symtab[x] else null
                    if (!value.isNullOrEmpty()) {
                        decoded.setRange(
                            wordMatch.range.first + replaceOffset,
                            wordMatch.range.last + 1 + replaceOffset,
                            value
                        )
                        replaceOffset += value.length - word.length
                    }
                }
                return decoded.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private inner class Unbase(private val radix: Int) {
        private val ALPHABET_62 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private val ALPHABET_95 =
            " !\"#\$%&\\'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~"
        private var alphabet: String? = null
        private var dictionary: HashMap<String, Int>? = null

        fun unbase(str: String): Int {
            var ret = 0
            if (alphabet == null) {
                ret = str.toInt(radix)
            } else {
                val tmp = StringBuilder(str).reverse().toString()
                for (i in tmp.indices) {
                    ret += (radix.toDouble().pow(i.toDouble()) * (dictionary?.get(tmp.substring(i, i + 1)) ?: 0)).toInt()
                }
            }
            return ret
        }

        init {
            if (radix > 36) {
                when {
                    radix < 62 -> alphabet = ALPHABET_62.substring(0, radix)
                    radix in 63..94 -> alphabet = ALPHABET_95.substring(0, radix)
                    radix == 62 -> alphabet = ALPHABET_62
                    radix == 95 -> alphabet = ALPHABET_95
                }
                dictionary = HashMap(95)
                for (i in 0 until (alphabet?.length ?: 0)) {
                    val ch = alphabet?.substring(i, i + 1)
                    if (ch != null) dictionary?.set(ch, i)
                }
            }
        }
    }
}
