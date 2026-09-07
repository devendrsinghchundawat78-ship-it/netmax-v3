package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Providers read their own small key/value state through this. The app keeps its
 * settings elsewhere, so the object only exposes the prefs file the library uses.
 */
object DataStore {
    const val dataStoreFileName = "cloudstream_settings"

    fun getSharedPrefs(context: Context?): SharedPreferences? =
        context?.getSharedPreferences(dataStoreFileName, Context.MODE_PRIVATE)

    /** Same file, but created for the application context instead of an activity. */
    fun getDefaultSharedPrefs(context: Context?): SharedPreferences? =
        getSharedPrefs(context?.applicationContext ?: context)

    fun <T> putKeyValue(context: Context?, key: String, value: T?) {
        val prefs = getDefaultSharedPrefs(context) ?: return
        prefs.edit().putString(key, value?.toString()).apply()
    }

    fun getString(context: Context?, key: String): String? =
        getDefaultSharedPrefs(context)?.getString(key, null)
}
