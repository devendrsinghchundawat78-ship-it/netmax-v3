package com.lagradost.cloudstream3.utils

object DataStore {
    const val dataStoreFileName = "cloudstream_settings"

    fun getSharedPrefs(context: Any?): Any? = null
    fun getDefaultSharedPrefs(context: Any?): Any? = null
    fun <T> putKeyValue(context: Any?, key: String, value: T?) = Unit
    fun getString(context: Any?, key: String): String? = null
}
