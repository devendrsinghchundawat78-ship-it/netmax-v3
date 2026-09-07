@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Upstream wraps a loaded .cs3 in this class; provider binaries subclass it and
 * call `getResources()` when `requiresResources` is set, so it has to be a real
 * class here instead of a typealias.
 */
abstract class Plugin : BasePlugin() {
    companion object {
        /** Set by the host loader before a plugin is instantiated. */
        @Volatile
        var hostContext: Context? = null
    }

    var url: String? = null
    var repoUrl: String? = null
    var _name: String? = null
    var _version: Int? = null

    /**
     * The plugin's own resources when `resources.zip` was packed into the .cs3,
     * otherwise the host resources so a lookup still resolves to something usable.
     */
    var resources: Resources? = null
        get() = field ?: hostContext?.resources

    /** Extra settings entry a plugin can add; unused by this host, kept for parity. */
    var openSettings: ((context: Context) -> Unit)? = null

    open fun load(context: Context) {
        hostContext = context
        load()
    }
}
