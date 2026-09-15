package com.nuvio.app.features.music

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.stringByAppendingPathComponent
import platform.Foundation.writeToFile

internal actual object MusicDownloadPlatform {
    private fun getDownloadsDirPath(): String {
        val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        val documentsDirectory = paths.firstOrNull() as? String ?: ""
        val downloadsPath = (documentsDirectory as NSString).stringByAppendingPathComponent("music_downloads")
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(downloadsPath)) {
            fm.createDirectoryAtPath(downloadsPath, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return downloadsPath
    }

    actual suspend fun downloadFile(url: String, trackId: String, onProgress: (Float) -> Unit): String? = runCatching {
        val dir = getDownloadsDirPath()
        val targetPath = (dir as NSString).stringByAppendingPathComponent("$trackId.mp4")
        val nsUrl = NSURL(string = url)
        val data = NSData_dataWithContentsOfURL(nsUrl) ?: return null
        val success = data.writeToFile(targetPath, atomically = true)
        if (success) {
            onProgress(1f)
            targetPath
        } else {
            null
        }
    }.getOrNull()

    private fun NSData_dataWithContentsOfURL(url: NSURL): platform.Foundation.NSData? {
        return platform.Foundation.NSData.dataWithContentsOfURL(url)
    }

    actual fun deleteFile(filePath: String): Boolean = runCatching {
        NSFileManager.defaultManager.removeItemAtPath(filePath, error = null)
    }.getOrDefault(false)

    actual fun fileExists(filePath: String): Boolean = runCatching {
        NSFileManager.defaultManager.fileExistsAtPath(filePath)
    }.getOrDefault(false)

    actual fun getFileSize(filePath: String): Long = runCatching {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(filePath, error = null)
        (attrs?.get(platform.Foundation.NSFileSize) as? Number)?.toLong() ?: 0L
    }.getOrDefault(0L)

    actual fun getDownloadsDirectorySize(): Long = runCatching {
        var total = 0L
        val dir = getDownloadsDirPath()
        val fm = NSFileManager.defaultManager
        val files = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return 0L
        for (f in files) {
            val fileStr = f as? String ?: continue
            val fullPath = (dir as NSString).stringByAppendingPathComponent(fileStr)
            total += getFileSize(fullPath)
        }
        total
    }.getOrDefault(0L)

    actual fun clearDownloadsDirectory(): Boolean = runCatching {
        val dir = getDownloadsDirPath()
        val fm = NSFileManager.defaultManager
        val files = fm.contentsOfDirectoryAtPath(dir, error = null) ?: return true
        for (f in files) {
            val fileStr = f as? String ?: continue
            val fullPath = (dir as NSString).stringByAppendingPathComponent(fileStr)
            fm.removeItemAtPath(fullPath, error = null)
        }
        true
    }.getOrDefault(false)
}
