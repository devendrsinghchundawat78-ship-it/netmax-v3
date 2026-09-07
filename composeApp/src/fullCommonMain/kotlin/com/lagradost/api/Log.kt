@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.api

/**
 * Minimal stand-in for the CloudStream library logger. Providers link against
 * `com.lagradost.api.Log` (d/i/w/e with a tag and a message); on Android
 * `System.out` is mirrored into logcat, so plain prints are enough here and keep
 * this file free of any platform specific import.
 */
object Log {
    fun d(tag: String, message: String) = print("D", tag, message)
    fun i(tag: String, message: String) = print("I", tag, message)
    fun w(tag: String, message: String) = print("W", tag, message)
    fun e(tag: String, message: String) = print("E", tag, message)

    private fun print(level: String, tag: String, message: String) {
        println("$level/$tag: $message")
    }
}
