package com.nuvio.app.features.player

import android.graphics.Typeface
import java.io.File

/**
 * Loads and caches user-imported subtitle Typefaces (TTF/OTF).
 * Falls back to the system default when the path is blank or the file
 * cannot be parsed (so subtitle rendering never breaks).
 */
internal object PlayerSubtitleFonts {
    private val cache = HashMap<String, Typeface>()
    private val lock = Any()

    fun typefaceFor(fontPath: String?, bold: Boolean): Typeface {
        val default = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        if (fontPath.isNullOrBlank()) return default
        val base = loadBase(fontPath) ?: return default
        if (!bold) return base
        return runCatching { Typeface.create(base, Typeface.BOLD) }.getOrNull() ?: base
    }

    fun invalidate(fontPath: String) {
        synchronized(lock) { cache.remove(fontPath) }
    }

    private fun loadBase(fontPath: String): Typeface? =
        synchronized(lock) {
            cache[fontPath] ?: run {
                val loaded = File(fontPath)
                    .takeIf { it.isFile }
                    ?.let { file -> runCatching { Typeface.createFromFile(file.absolutePath) }.getOrNull() }
                if (loaded != null) cache[fontPath] = loaded
                loaded
            }
        }
}
