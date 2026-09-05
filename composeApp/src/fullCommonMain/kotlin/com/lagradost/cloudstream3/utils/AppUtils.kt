package com.lagradost.cloudstream3.utils

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

object AppUtils {
    val mapper: ObjectMapper by lazy {
        runCatching {
            jacksonObjectMapper().apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }.getOrElse {
            ObjectMapper().apply {
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    inline fun <reified T> parseJson(text: String): T = mapper.readValue(text)

    inline fun <reified T> parseJson(stream: InputStream): T = mapper.readValue(stream)

    fun toJson(obj: Any): String = mapper.writeValueAsString(obj)

    inline fun <reified T> tryParseJson(text: String?): T? {
        if (text == null) return null
        return try {
            parseJson<T>(text)
        } catch (_: Exception) {
            null
        }
    }
}

fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")
fun String.urlDecode(): String = URLDecoder.decode(this, "UTF-8")

fun httpsify(url: String): String = when {
    url.startsWith("//") -> "https:$url"
    url.startsWith("http://") -> "https://" + url.substring(7)
    else -> url
}
