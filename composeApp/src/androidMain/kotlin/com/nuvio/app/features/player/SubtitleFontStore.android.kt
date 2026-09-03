package com.nuvio.app.features.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.IOException

private const val FONT_DIR_NAME = "subtitle_fonts"
private val FONT_EXTENSIONS = listOf("ttf", "otf", "ttc")

actual object SubtitleFontStore {
    private var appContext: Context? = null
    private val bytesCache = HashMap<String, ByteArray>()

    actual fun initialize(contextRef: Any?) {
        if (contextRef is Context) {
            appContext = contextRef.applicationContext
        }
    }

    private fun context(): Context? = appContext

    private fun fontDir(context: Context): File =
        File(context.filesDir, FONT_DIR_NAME).apply { if (!exists()) mkdirs() }

    private fun isFontFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in FONT_EXTENSIONS
    }

    private fun sanitizeFileName(name: String): String {
        val base = name.substringBeforeLast('.')
        val sanitized = buildString {
            base.forEach { char ->
                append(if (char.isLetterOrDigit() || char == ' ' || char == '-' || char == '_') char else '_')
            }
        }
            .trim()
            .replace(Regex("\\s+"), "-")
            .ifBlank { "font" }
        return if (isFontFile(name)) "$sanitized.${name.substringAfterLast('.')}" else "$sanitized.ttf"
    }

    actual fun listFonts(): List<ImportedSubtitleFont> {
        val context = context() ?: return emptyList()
        val files = fontDir(context).listFiles() ?: return emptyList()
        return files
            .filter { it.isFile && isFontFile(it.name) }
            .sortedBy { it.name.lowercase() }
            .map { file -> ImportedSubtitleFont(path = file.absolutePath, displayName = file.name) }
    }

    actual fun importFontFromUri(uri: String, displayName: String): String? {
        val context = context() ?: return null
        val contentUri = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        return try {
            val safeName = resolveSafeName(context, contentUri, displayName)
            val target = uniqueFile(fontDir(context), safeName)
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            if (target.length() == 0L) {
                target.delete()
                return null
            }
            bytesCache.remove(target.absolutePath)
            target.absolutePath
        } catch (_: IOException) {
            null
        }
    }

    private fun resolveSafeName(context: Context, uri: Uri, fallback: String): String {
        val queried = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
        }.getOrNull()
        val baseName = queried?.takeIf { it.isNotBlank() } ?: fallback
        return sanitizeFileName(baseName)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        var counter = 1
        while (candidate.exists()) {
            val base = name.substringBeforeLast('.')
            val ext = name.substringAfterLast('.', "ttf")
            candidate = File(dir, "$base-$counter.$ext")
            counter++
        }
        return candidate
    }

    actual fun deleteFont(fontPath: String): Boolean {
        val file = File(fontPath)
        val parent = file.parentFile ?: return false
        val dir = fontDirSafe() ?: return false
        // Only ever delete files from the private font dir (safety).
        if (parent.absolutePath != dir.absolutePath) return false
        bytesCache.remove(fontPath)
        return runCatching { file.delete() }.getOrDefault(false)
    }

    private fun fontDirSafe(): File? {
        val context = context() ?: return null
        return File(context.filesDir, FONT_DIR_NAME)
    }

    actual fun loadFontBytes(fontPath: String): ByteArray? {
        val cached = bytesCache[fontPath]
        if (cached != null) return cached
        val file = File(fontPath)
        val parent = file.parentFile ?: return null
        val dir = fontDirSafe() ?: return null
        if (parent.absolutePath != dir.absolutePath) return null
        return runCatching {
            file.readBytes().also { bytesCache[fontPath] = it }
        }.getOrNull()
    }

    actual fun fontDisplayName(fontPath: String): String = File(fontPath).name
}

@Composable
actual fun subtitleFontPickerLauncher(
    onPicked: (uri: String, displayName: String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val pendingCallback = remember { arrayOfNulls<((String, String) -> Unit)>(1) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val callback = pendingCallback[0]
        pendingCallback[0] = null
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
                }
            }.getOrNull() ?: "font.ttf"
            callback?.invoke(uri.toString(), name)
        }
    }
    return {
        pendingCallback[0] = onPicked
        launcher.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "*/*"))
    }
}
