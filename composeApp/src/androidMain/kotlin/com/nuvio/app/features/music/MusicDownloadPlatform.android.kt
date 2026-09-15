package com.nuvio.app.features.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal actual object MusicDownloadPlatform {
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private fun getDownloadsDir(): File? {
        val ctx = appContext ?: return null
        val dir = File(ctx.filesDir, "music_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    actual suspend fun downloadFile(url: String, trackId: String, onProgress: (Float) -> Unit): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = getDownloadsDir() ?: return@withContext null
            val targetFile = File(dir, "$trackId.mp4")
            val tempFile = File(dir, "$trackId.tmp")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                return@withContext null
            }

            val totalBytes = connection.contentLength.toLong()
            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (tempFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                onProgress(1f)
                targetFile.absolutePath
            } else {
                null
            }
        }.getOrNull()
    }

    actual fun deleteFile(filePath: String): Boolean = runCatching {
        val file = File(filePath)
        if (file.exists()) file.delete() else false
    }.getOrDefault(false)

    actual fun fileExists(filePath: String): Boolean = runCatching {
        File(filePath).exists()
    }.getOrDefault(false)

    actual fun getFileSize(filePath: String): Long = runCatching {
        File(filePath).length()
    }.getOrDefault(0L)

    actual fun getDownloadsDirectorySize(): Long = runCatching {
        val dir = getDownloadsDir() ?: return@runCatching 0L
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }.getOrDefault(0L)

    actual fun clearDownloadsDirectory(): Boolean = runCatching {
        val dir = getDownloadsDir() ?: return@runCatching false
        dir.listFiles()?.forEach { it.delete() }
        true
    }.getOrDefault(false)
}
