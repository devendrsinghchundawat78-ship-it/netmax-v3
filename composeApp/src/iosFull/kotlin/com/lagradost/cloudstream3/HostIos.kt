@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3

import com.lagradost.cloudstream3.utils.Event

/** iOS twins of the CloudStream app classes providers link against (no Android SDK on this target). */
object CommonActivity {
    fun getActivity(): Any? = null
    fun showToast(message: String, duration: Int? = null) = Unit
    fun showToast(message: Int, duration: Int? = null) = Unit
    fun isTvDevice(): Boolean = false
}

class CloudStreamApp {
    companion object {
        val context: Any? = null

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
        val afterPluginsLoadedEvent: Event<String> = Event()
    }
}

