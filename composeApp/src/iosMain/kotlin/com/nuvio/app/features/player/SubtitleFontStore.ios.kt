package com.nuvio.app.features.player

import androidx.compose.runtime.Composable

/**
 * iOS has no in-app font import flow yet (device font import is an Android-only
 * feature for now). All operations are safe no-ops so the shared subtitle UI
 * can call the store unconditionally.
 */
actual object SubtitleFontStore {
    actual fun initialize(contextRef: Any?) {
        // No-op on iOS.
    }

    actual fun listFonts(): List<ImportedSubtitleFont> = emptyList()

    actual fun importFontFromUri(uri: String, displayName: String): String? = null

    actual fun deleteFont(fontPath: String): Boolean = false

    actual fun loadFontBytes(fontPath: String): ByteArray? = null

    actual fun fontDisplayName(fontPath: String): String =
        fontPath.substringAfterLast('/').substringAfterLast('\\')
}

@Composable
actual fun subtitleFontPickerLauncher(
    onPicked: (uri: String, displayName: String) -> Unit,
): () -> Unit = {
    // No document picker on iOS yet.
}
