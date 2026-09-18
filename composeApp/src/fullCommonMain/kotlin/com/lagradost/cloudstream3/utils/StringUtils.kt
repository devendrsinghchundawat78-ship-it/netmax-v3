package com.lagradost.cloudstream3.utils

import java.net.URLEncoder

object StringUtils {
    fun encodeUri(uri: String): String =
        runCatching { URLEncoder.encode(uri, "UTF-8") }.getOrDefault(uri)
}

fun String.encodeUri(): String = StringUtils.encodeUri(this)

fun String.html(): org.jsoup.nodes.Document = org.jsoup.Jsoup.parse(this)

fun String.findFirst(regex: Regex): String? = regex.find(this)?.groupValues?.getOrNull(1)

fun String.findFirst(regex: String): String? = Regex(regex).find(this)?.groupValues?.getOrNull(1)
