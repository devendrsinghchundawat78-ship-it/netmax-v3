package com.lagradost.nicehttp

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlin.reflect.KClass
import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

val defaultMapper: ObjectMapper = jacksonObjectMapper().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

interface ResponseParser {
    val mapper: ObjectMapper

    fun <T : Any> parse(text: String, kClass: KClass<T>): T
    fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T?
    fun writeValueAsString(obj: Any): String
}

class DefaultResponseParser(override val mapper: ObjectMapper = defaultMapper) : ResponseParser {
    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return mapper.readValue(text, kClass.java)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            mapper.readValue(text, kClass.java)
        } catch (e: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}

open class Requests(
    val baseClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    val defaultHeaders: Map<String, String> = mapOf("User-Agent" to USER_AGENT),
    val responseParser: ResponseParser = DefaultResponseParser()
) {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 30L,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser = this.responseParser,
    ): NiceResponse {
        val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("Invalid URL: $url")
        params.forEach { (k, v) -> httpUrlBuilder.addQueryParameter(k, v) }
        val finalUrl = httpUrlBuilder.build()

        val reqHeaders = (defaultHeaders + headers).toMutableMap()
        if (referer != null) reqHeaders["Referer"] = referer
        if (cookies.isNotEmpty()) {
            reqHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        val request = Request.Builder()
            .url(finalUrl)
            .headers(reqHeaders.toHeaders())
            .get()
            .build()

        var client = if (timeout != 30L) {
            baseClient.newBuilder().readTimeout(timeout, TimeUnit.SECONDS).build()
        } else {
            baseClient
        }

        if (!allowRedirects) {
            client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        }
        if (interceptor != null) {
            client = client.newBuilder().addInterceptor(interceptor).build()
        }

        return client.newCall(request).awaitResponse(responseParser)
    }

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = null,
        files: List<Any>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = 0,
        cacheUnit: TimeUnit = TimeUnit.MINUTES,
        timeout: Long = 30L,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser = this.responseParser,
    ): NiceResponse {
        val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("Invalid URL: $url")
        params.forEach { (k, v) -> httpUrlBuilder.addQueryParameter(k, v) }
        val finalUrl = httpUrlBuilder.build()

        val reqHeaders = (defaultHeaders + headers).toMutableMap()
        if (referer != null) reqHeaders["Referer"] = referer
        if (cookies.isNotEmpty()) {
            reqHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }

        val body = when {
            requestBody != null -> requestBody
            data != null -> {
                val formBuilder = FormBody.Builder()
                data.forEach { (k, v) -> formBuilder.add(k, v) }
                formBuilder.build()
            }
            json != null -> {
                val jsonStr = if (json is String) json else defaultMapper.writeValueAsString(json)
                jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            }
            else -> FormBody.Builder().build()
        }

        val request = Request.Builder()
            .url(finalUrl)
            .headers(reqHeaders.toHeaders())
            .post(body)
            .build()

        var client = if (timeout != 30L) {
            baseClient.newBuilder().readTimeout(timeout, TimeUnit.SECONDS).build()
        } else {
            baseClient
        }

        if (!allowRedirects) {
            client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        }
        if (interceptor != null) {
            client = client.newBuilder().addInterceptor(interceptor).build()
        }

        return client.newCall(request).awaitResponse(responseParser)
    }
}

class NiceResponse(
    val okhttpResponse: Response,
    val text: String,
    val url: String,
    val headers: Headers,
    val code: Int,
    val parser: ResponseParser = DefaultResponseParser()
) {
    val isSuccessful: Boolean get() = code in 200..299
    val document: Document by lazy { Jsoup.parse(text, url) }

    inline fun <reified T : Any> parsed(): T = parser.parse(text, T::class)

    inline fun <reified T : Any> parsedSafe(): T? = parser.parseSafe(text, T::class)
}

class SessionCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = cookieStore.getOrPut(host) { mutableListOf() }
        cookies.forEach { newCookie ->
            current.removeAll { it.name == newCookie.name }
            current.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        return cookieStore[host] ?: emptyList()
    }
}

suspend fun Call.awaitResponse(responseParser: ResponseParser = DefaultResponseParser()): NiceResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
    }

    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            try {
                val bodyString = response.body?.string() ?: ""
                val nice = NiceResponse(
                    okhttpResponse = response,
                    text = bodyString,
                    url = response.request.url.toString(),
                    headers = response.headers,
                    code = response.code,
                    parser = responseParser
                )
                continuation.resume(nice)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    })
}
