package com.nuvio.app.features.music

internal expect object MusicLibraryStorage {
    fun loadLikedTracks(): List<MusicTrack>
    fun saveLikedTracks(tracks: List<MusicTrack>)
    fun loadRecentTracks(): List<MusicTrack>
    fun saveRecentTracks(tracks: List<MusicTrack>)
}
