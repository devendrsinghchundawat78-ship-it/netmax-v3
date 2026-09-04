package com.lagradost.cloudstream3.network

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

class WebViewResolver(val interceptor: Interceptor? = null) {
    fun resolveUsingWebView(request: Request): Response? = null
}
