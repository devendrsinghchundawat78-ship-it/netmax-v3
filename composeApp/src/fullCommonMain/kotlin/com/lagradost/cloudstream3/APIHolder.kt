package com.lagradost.cloudstream3

import java.util.Locale

object APIHolder {
    var allProviders: List<MainAPI> = emptyList()
    var apis: List<MainAPI> = emptyList()

    fun initAll() {}

    fun capitalize(str: String): String =
        str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

    fun getUnixTime(): Long = System.currentTimeMillis() / 1000L
    fun getUnixTimeMS(): Long = System.currentTimeMillis()

    fun getCaptchaToken(url: String, key: String): String? = null
    fun getCaptchaToken(url: String, key: String, referer: String? = null): String? = null
    suspend fun getCaptchaToken(url: String, key: String, referer: String? = null, timeout: Long = 0L): String? = null
}
