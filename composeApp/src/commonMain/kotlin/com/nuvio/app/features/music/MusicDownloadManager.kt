package com.nuvio.app.features.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object MusicDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _downloadedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val downloadedTracks: StateFlow<List<MusicTrack>> = _downloadedTracks.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val tracks = MusicDownloadStorage.loadDownloadedTracks()
        // verify file still exists
        val validTracks = tracks.filter { track ->
            track.localFilePath != null && MusicDownloadPlatform.fileExists(track.localFilePath)
        }
        if (validTracks.size != tracks.size) {
            MusicDownloadStorage.saveDownloadedTracks(validTracks)
        }
        _downloadedTracks.value = validTracks
    }

    fun isDownloaded(trackId: String): Boolean {
        ensureLoaded()
        return _downloadedTracks.value.any { it.id == trackId }
    }

    fun isDownloading(trackId: String): Boolean {
        return _downloadProgress.value.containsKey(trackId)
    }

    fun getLocalTrack(trackId: String): MusicTrack? {
        ensureLoaded()
        return _downloadedTracks.value.firstOrNull { it.id == trackId }
    }

    fun downloadTrack(track: MusicTrack, quality: String = "320kbps") {
        ensureLoaded()
        if (isDownloaded(track.id) || isDownloading(track.id)) return

        scope.launch {
            _downloadProgress.value = _downloadProgress.value + (track.id to 0f)

            val streamUrl = MusicService.resolveStreamUrl(track, quality)
            if (streamUrl == null) {
                _downloadProgress.value = _downloadProgress.value - track.id
                return@launch
            }

            val localPath = MusicDownloadPlatform.downloadFile(
                url = streamUrl,
                trackId = track.id,
                onProgress = { p ->
                    _downloadProgress.value = _downloadProgress.value + (track.id to p)
                }
            )

            _downloadProgress.value = _downloadProgress.value - track.id

            if (localPath != null) {
                val downloadedTrack = track.copy(
                    localFilePath = localPath,
                    isDownloaded = true,
                    addedAtMs = System.currentTimeMillis()
                )
                val updated = listOf(downloadedTrack) + _downloadedTracks.value.filterNot { it.id == track.id }
                _downloadedTracks.value = updated
                MusicDownloadStorage.saveDownloadedTracks(updated)
            }
        }
    }

    fun deleteDownload(trackId: String) {
        ensureLoaded()
        val track = _downloadedTracks.value.firstOrNull { it.id == trackId } ?: return
        if (track.localFilePath != null) {
            MusicDownloadPlatform.deleteFile(track.localFilePath)
        }
        val updated = _downloadedTracks.value.filterNot { it.id == trackId }
        _downloadedTracks.value = updated
        MusicDownloadStorage.saveDownloadedTracks(updated)
    }

    fun clearAllDownloads() {
        ensureLoaded()
        MusicDownloadPlatform.clearDownloadsDirectory()
        _downloadedTracks.value = emptyList()
        MusicDownloadStorage.saveDownloadedTracks(emptyList())
    }

    fun getFormattedStorageSize(): String {
        val bytes = MusicDownloadPlatform.getDownloadsDirectorySize()
        if (bytes <= 0) return "0 MB"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1000) {
            val gb = mb / 1024.0
            "${(gb * 10).toInt() / 10.0} GB"
        } else {
            "${(mb * 10).toInt() / 10.0} MB"
        }
    }
}
