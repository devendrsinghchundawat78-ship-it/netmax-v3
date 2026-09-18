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
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

val defaultMapper: ObjectMapper by lazy {
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

interface ResponseParser {
    val mapper: ObjectMapper

    fun <T : Any> parse(text: String, kClass: KClass<T>): T
    fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T?
    fun writeValueAsString(obj: Any): String
}

class DefaultResponseParser(private val _mapper: ObjectMapper? = null) : ResponseParser {
    override val mapper: ObjectMapper
        get() = _mapper ?: defaultMapper
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

class NiceFile(val name: String, val fileName: String, val file: File? = null, val fileType: String? = null) {
    constructor(name: String, value: String) : this(name, value, null, null)
    constructor(name: String, file: File) : this(name, file.name, file, null)
    constructor(file: File) : this(file.name, file)
}

fun Map<String, String>.toNiceFiles(): List<NiceFile> = this.map { NiceFile(it.key, it.value) }

val Response.cookies: Map<String, String>
    get() = headers.getCookies("set-cookie")

val Request.cookies: Map<String, String>
    get() = headers.getCookies("Cookie")

fun Headers.getCookies(cookieKey: String): Map<String, String> {
    val cookieList = this.filter { it.first.equals(cookieKey, ignoreCase = true) }.map {
        it.second.substringBefore(";")
    }
    return cookieList.associate {
        val split = it.split("=", limit = 2)
        (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
    }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
}

fun requestCreator(
    method: String,
    url: String,
    headers: Map<String, String> = emptyMap(),
    referer: String? = null,
    params: Map<String, String> = emptyMap(),
    cookies: Map<String, String> = emptyMap(),
    data: Map<String, String>? = null,
    files: List<NiceFile>? = null,
    json: Any? = null,
    requestBody: RequestBody? = null,
    cacheTime: Int? = null,
    cacheUnit: TimeUnit? = null,
    responseParser: ResponseParser? = null
): Request {
    val httpUrlBuilder = url.toHttpUrlOrNull()?.newBuilder() ?: throw IllegalArgumentException("Invalid URL: $url")
    params.forEach { (k, v) -> httpUrlBuilder.addQueryParameter(k, v) }
    val finalUrl = httpUrlBuilder.build()

    val reqHeaders = headers.toMutableMap()
    if (referer != null) reqHeaders["Referer"] = referer
    if (cookies.isNotEmpty()) {
        reqHeaders["Cookie"] = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    val body = when {
        requestBody != null -> requestBody
        !data.isNullOrEmpty() -> {
            val formBuilder = FormBody.Builder()
            data.forEach { (k, v) -> formBuilder.add(k, v) }
            formBuilder.build()
        }
        json != null -> {
            val jsonStr = when (json) {
                is String -> json
                else -> (responseParser ?: defaultMapper).let {
                    if (it is ResponseParser) it.writeValueAsString(json) else defaultMapper.writeValueAsString(json)
                }
            }
            jsonStr.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        }
        !files.isNullOrEmpty() -> {
            val formBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            files.forEach {
                if (it.file != null) {
                    formBuilder.addFormDataPart(
                        it.name,
                        it.fileName,
                        it.file.asRequestBody(it.fileType?.toMediaTypeOrNull())
                    )
                } else {
                    formBuilder.addFormDataPart(it.name, it.fileName)
                }
            }
            formBuilder.build()
        }
        method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true) -> FormBody.Builder().build()
        else -> null
    }

    return Request.Builder()
        .url(finalUrl)
        .headers(reqHeaders.toHeaders())
        .method(method, body)
        .build()
}

open class Requests(
    var baseClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(SessionCookieJar())
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    var defaultHeaders: Map<String, String> = mapOf("User-Agent" to USER_AGENT),
    var defaultReferer: String? = null,
    var defaultData: Map<String, String> = emptyMap(),
    var defaultCookies: Map<String, String> = emptyMap(),
    var defaultCacheTime: Int = 0,
    var defaultCacheTimeUnit: TimeUnit = TimeUnit.MINUTES,
    var defaultTimeOut: Long = 0L,
    var responseParser: ResponseParser? = DefaultResponseParser()
) {
    open suspend fun custom(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser
    ): NiceResponse {
        val request = requestCreator(
            method = method,
            url = url,
            headers = defaultHeaders + headers,
            referer = referer ?: defaultReferer,
            params = params,
            cookies = defaultCookies + cookies,
            data = data,
            files = files,
            json = json,
            requestBody = requestBody,
            cacheTime = cacheTime,
            cacheUnit = cacheUnit,
            responseParser = responseParser
        )

        var client = if (timeout > 0L) {
            baseClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build()
        } else {
            baseClient
        }

        if (!allowRedirects) {
            client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
        }
        if (interceptor != null) {
            client = client.newBuilder().addInterceptor(interceptor).build()
        }

        return client.newCall(request).awaitResponse(responseParser ?: this.responseParser ?: DefaultResponseParser())
    }

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "GET", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "POST", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun head(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "HEAD", url, headers, referer, params, cookies, null, null, null, null,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun put(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        files: List<NiceFile>? = null,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "PUT", url, headers, referer, params, cookies, data, files, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )

    suspend fun delete(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String>? = defaultData,
        json: Any? = null,
        requestBody: RequestBody? = null,
        allowRedirects: Boolean = true,
        cacheTime: Int = defaultCacheTime,
        cacheUnit: TimeUnit = defaultCacheTimeUnit,
        timeout: Long = defaultTimeOut,
        interceptor: Interceptor? = null,
        verify: Boolean = true,
        responseParser: ResponseParser? = this.responseParser,
    ): NiceResponse = custom(
        "DELETE", url, headers, referer, params, cookies, data, null, json, requestBody,
        allowRedirects, cacheTime, cacheUnit, timeout, interceptor, verify, responseParser
    )
}

open class Session(
    client: OkHttpClient = OkHttpClient()
) : Requests() {
    init {
        this.baseClient = client
            .newBuilder()
            .cookieJar(CustomCookieJar())
            .build()
    }

    open inner class CustomCookieJar : CookieJar {
        var cookies = mapOf<String, Cookie>()

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return this.cookies.values.toList()
        }

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies += cookies.associateBy { it.name }
        }
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
    val cookies: Map<String, String> by lazy { okhttpResponse.cookies }

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
