package com.nuvio.app.features.music

internal expect object MusicDownloadStorage {
    fun loadDownloadedTracks(): List<MusicTrack>
    fun saveDownloadedTracks(tracks: List<MusicTrack>)
}
