package com.nuvio.app.features.music

internal expect class PlatformMusicAudioPlayer() {
    fun playUrl(url: String, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun release()
    fun setCallbacks(
        onStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
        onProgress: (currentMs: Long, totalMs: Long) -> Unit,
        onEnded: () -> Unit,
        onError: (String) -> Unit,
    )
}
