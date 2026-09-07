@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.api

/**
 * Host context handed to the library (the app sets it while loading plugins).
 * Kept as `Any?` exactly like upstream so this file has no platform dependency.
 */
private var hostedContext: Any? = null

fun setContext(context: Any?) {
    hostedContext = context
}

fun getContext(): Any? = hostedContext
