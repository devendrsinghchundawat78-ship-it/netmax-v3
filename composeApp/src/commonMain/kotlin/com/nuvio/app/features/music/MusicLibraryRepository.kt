package com.nuvio.app.features.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MusicLibraryRepository {
    private val _likedTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val likedTracks: StateFlow<List<MusicTrack>> = _likedTracks.asStateFlow()

    private val _recentTracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val recentTracks: StateFlow<List<MusicTrack>> = _recentTracks.asStateFlow()

    private var hasLoaded = false

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        loadFromDisk()
    }

    private fun loadFromDisk() {
        _likedTracks.value = MusicLibraryStorage.loadLikedTracks()
        _recentTracks.value = MusicLibraryStorage.loadRecentTracks()
    }

    fun isLiked(trackId: String): Boolean {
        return _likedTracks.value.any { it.id == trackId }
    }

    fun toggleLike(track: MusicTrack): Boolean {
        ensureLoaded()
        val current = _likedTracks.value.toMutableList()
        val index = current.indexOfFirst { it.id == track.id }
        val nowLiked = if (index >= 0) {
            current.removeAt(index)
            false
        } else {
            current.add(0, track.copy(isLiked = true))
            true
        }
        _likedTracks.value = current
        MusicLibraryStorage.saveLikedTracks(current)
        return nowLiked
    }

    fun recordPlayback(track: MusicTrack) {
        ensureLoaded()
        val current = _recentTracks.value.toMutableList()
        current.removeAll { it.id == track.id }
        current.add(0, track)
        val capped = current.take(50)
        _recentTracks.value = capped
        MusicLibraryStorage.saveRecentTracks(capped)
    }

    fun clearRecentTracks() {
        _recentTracks.value = emptyList()
        MusicLibraryStorage.saveRecentTracks(emptyList())
    }
}
