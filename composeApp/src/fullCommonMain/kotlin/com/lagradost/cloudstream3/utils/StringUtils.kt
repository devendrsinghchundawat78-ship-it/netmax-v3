package com.lagradost.cloudstream3.utils

fun String.html(): org.jsoup.nodes.Document = org.jsoup.Jsoup.parse(this)

fun String.findFirst(regex: Regex): String? = regex.find(this)?.groupValues?.getOrNull(1)

fun String.findFirst(regex: String): String? = Regex(regex).find(this)?.groupValues?.getOrNull(1)
