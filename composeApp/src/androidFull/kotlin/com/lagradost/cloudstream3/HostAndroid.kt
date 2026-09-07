@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import com.lagradost.cloudstream3.utils.Event

/**
 * Provider binaries link against a handful of CloudStream *app* classes (not the
 * library). This app never runs that UI, so these stand-ins expose the same
 * members wired to whatever the Nuvio host actually has: the current activity,
 * a toast, and the prefs file the library uses.
 */
object CommonActivity {
    private var currentActivity: Activity? = null

    /** Called by the host when an activity resumes / pauses. */
    fun setActivity(activity: Activity?) {
        currentActivity = activity
    }

    fun getActivity(): Activity? = currentActivity

    fun showToast(message: String, duration: Int? = null) {
        val activity = currentActivity ?: return
        val length = if (duration == Toast.LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        runCatching { Toast.makeText(activity, message, length).show() }
    }

    fun showToast(message: Int, duration: Int? = null) {
        val activity = currentActivity ?: return
        val length = if (duration == Toast.LENGTH_LONG) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        runCatching { Toast.makeText(activity, message, length).show() }
    }

    fun isTvDevice(): Boolean = runCatching {
        val pm = currentActivity?.packageManager
        pm?.hasSystemFeature("android.software.leanback") == true ||
            Build.MODEL.contains("tv", ignoreCase = true)
    }.getOrDefault(false)
}

class CloudStreamApp {
    companion object {
        @Volatile
        var ctx: Context? = null

        val context: Context?
            get() = ctx

        private val keyValueStore = HashMap<String, Any?>()

        fun setKey(key: String, value: Any?) {
            synchronized(keyValueStore) { keyValueStore[key] = value }
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> getKey(key: String): T? = synchronized(keyValueStore) { keyValueStore[key] as T? }
    }
}

class MainActivity {
    companion object {
        /** Emitted once the host finished loading plugins, like upstream does. */
        val afterPluginsLoadedEvent: Event<String> = Event()

        /** Providers notify the UI about library changes through these. */
        val bookmarksUpdatedEvent: Event<Boolean> = Event()
        val reloadLibraryEvent: Event<Unit> = Event()
    }
}

