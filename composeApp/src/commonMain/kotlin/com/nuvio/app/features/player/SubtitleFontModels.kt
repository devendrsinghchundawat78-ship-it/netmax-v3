package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

/**
 * A user-imported subtitle font (TTF/OTF) stored in the app's private font directory.
 */
data class ImportedSubtitleFont(
    val path: String,
    val displayName: String,
) {
    /** Name used to register the font with libass (file name without extension). */
    val assFontName: String
        get() = displayName.substringBeforeLast('.')
}

/**
 * Platform store for user-imported subtitle fonts.
 *
 * Fonts are copied into app-private storage so playback does not depend on
 * the original document (SAF permissions, removable storage, etc.).
 * The selected font path is intentionally NOT synced across profiles:
 * font files live on the device.
 */
internal expect object SubtitleFontStore {
    /** Platform initialization hook (context on Android, no-op on iOS). */
    fun initialize(contextRef: Any?)

    /** All imported fonts, sorted by display name. */
    fun listFonts(): List<ImportedSubtitleFont>

    /**
     * Copies a font picked from the platform document picker into the font store.
     * @return absolute path of the stored font file, or null when the import failed.
     */
    fun importFontFromUri(uri: String, displayName: String): String?

    /** Deletes an imported font file. */
    fun deleteFont(fontPath: String): Boolean

    /** Raw font bytes (used to register fonts with libass / load Typefaces). */
    fun loadFontBytes(fontPath: String): ByteArray?

    /** Human readable name for a stored font path. */
    fun fontDisplayName(fontPath: String): String
}

/**
 * Starts the platform font file picker.
 *
 * Returns a lambda that the UI calls to open the picker. When the user picks
 * a file, [onPicked] is invoked with the content URI string and a display name.
 * On platforms without a picker (iOS) the returned lambda is a no-op.
 */
@Composable
internal expect fun subtitleFontPickerLauncher(
    onPicked: (uri: String, displayName: String) -> Unit,
): () -> Unit
