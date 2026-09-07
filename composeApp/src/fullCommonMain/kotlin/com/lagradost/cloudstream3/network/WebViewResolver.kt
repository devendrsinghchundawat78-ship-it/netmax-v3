@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class)
package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * Upstream resolves this with an Android WebView to beat bot checks. The provider
 * binaries only link against the constructor and `resolveUsingWebView`, so this
 * build performs a plain HTTP fetch instead: no Android UI classes required and
 * providers still receive the final request.
 */
open class WebViewResolver(
    val interceptUrl: Regex,
    val additionalUrls: List<Regex> = emptyList(),
    val userAgent: String? = USER_AGENT,
    val useOkhttp: Boolean = true,
    val script: String? = null,
    val scriptCallback: ((String) -> Unit)? = null,
    val timeout: Long = DEFAULT_TIMEOUT
) : Interceptor {

    companion object {
        val DEFAULT_TIMEOUT: Long = 5500L
        var webViewUserAgent: String? = null
    }

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())

    suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        method: String,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> = resolveUsingWebView(url, referer, emptyMap(), method, requestCallBack)

    suspend fun resolveUsingWebView(url: String): Pair<Request?, List<Request>> =
        resolveUsingWebView(url, null, emptyMap(), "GET") { false }

    /**
     * @param requestCallBack called with every matched request, return true to stop early.
     * @return the final request plus every request that matched [additionalUrls].
     */
    suspend fun resolveUsingWebView(
        url: String,
        referer: String?,
        headers: Map<String, String>,
        method: String,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val requestHeaders = HashMap<String, String>(headers)
        requestHeaders["User-Agent"] = userAgent ?: USER_AGENT
        if (!referer.isNullOrBlank() && !requestHeaders.containsKey("Referer")) {
            requestHeaders["Referer"] = referer
        }
        val request = Request.Builder()
            .url(url)
            .headers(requestHeaders.toHeaders())
            .method(method, null)
            .build()
        val matched = mutableListOf<Request>()
        runCatching {
            val client = app.baseClient.newBuilder()
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
            client.newCall(request).execute().use { response ->
                matched.add(response.request)
            }
        }
        if (requestCallBack(request)) matched.add(request)
        return (matched.firstOrNull() ?: request) to matched
    }

    suspend fun resolveUsingWebView(request: Request): Pair<Request?, List<Request>> =
        resolveUsingWebView(request) { false }

    suspend fun resolveUsingWebView(
        request: Request,
        requestCallBack: (Request) -> Boolean
    ): Pair<Request?, List<Request>> {
        val matched = mutableListOf<Request>()
        runCatching {
            val client = app.baseClient.newBuilder()
                .callTimeout(timeout, TimeUnit.MILLISECONDS)
                .followRedirects(true)
                .build()
            client.newCall(request).execute().use { response ->
                matched.add(response.request)
            }
        }
        if (requestCallBack(request)) matched.add(request)
        return (matched.firstOrNull() ?: request) to matched
    }
}

/**
 * Upstream forces requests through a WebView so the cloudflare cookie is set.
 * Here it degrades to a normal fetch, which is enough for the providers that
 * only construct it and hand it to `app.get(interceptor = ...)`.
 */
open class CloudflareKiller : WebViewResolver(REGEX) {
    companion object {
        val REGEX: Regex = Regex(".*")
    }
}
