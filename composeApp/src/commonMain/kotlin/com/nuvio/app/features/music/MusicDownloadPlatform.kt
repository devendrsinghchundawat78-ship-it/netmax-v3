package com.nuvio.app.features.music

internal expect object MusicDownloadPlatform {
    suspend fun downloadFile(url: String, trackId: String, onProgress: (Float) -> Unit): String?
    fun deleteFile(filePath: String): Boolean
    fun fileExists(filePath: String): Boolean
    fun getFileSize(filePath: String): Long
    fun getDownloadsDirectorySize(): Long
    fun clearDownloadsDirectory(): Boolean
}
