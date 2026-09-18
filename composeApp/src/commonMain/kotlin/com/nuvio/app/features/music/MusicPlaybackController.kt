package com.nuvio.app.features.music

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

object MusicPlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val audioPlayer = PlatformMusicAudioPlayer()

    private val _playbackState = MutableStateFlow(MusicPlaybackState())
    val playbackState: StateFlow<MusicPlaybackState> = _playbackState.asStateFlow()

    init {
        audioPlayer.setCallbacks(
            onStateChanged = { isPlaying, isBuffering ->
                _playbackState.update {
                    it.copy(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                    )
                }
            },
            onProgress = { currentMs, totalMs ->
                _playbackState.update {
                    it.copy(
                        currentPositionMs = currentMs,
                        durationMs = if (totalMs > 0) totalMs else it.durationMs,
                    )
                }
            },
            onEnded = {
                handleTrackEnded()
            },
            onError = { _ ->
                _playbackState.update { it.copy(isPlaying = false, isBuffering = false) }
            },
        )
    }

    fun playTrack(track: MusicTrack, newQueue: List<MusicTrack>? = null) {
        scope.launch {
            val queue = newQueue ?: if (_playbackState.value.queue.isEmpty()) listOf(track) else _playbackState.value.queue
            val queueIdx = queue.indexOfFirst { it.id == track.id }.let { if (it >= 0) it else 0 }

            val initialDurationMs = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            _playbackState.update {
                it.copy(
                    currentTrack = track,
                    queue = queue,
                    queueIndex = queueIdx,
                    isPlaying = false,
                    isBuffering = true,
                    currentPositionMs = 0L,
                    durationMs = initialDurationMs,
                    isMiniPlayerVisible = true,
                )
            }

            MusicLibraryRepository.recordPlayback(track)

            // Resolve streaming or local URL
            val localTrack = MusicDownloadManager.getLocalTrack(track.id)
            val currentPref = MusicSettingsRepository.settings.value.streamingQuality
            val streamUrl = localTrack?.localFilePath
                ?: MusicService.resolveStreamUrlAsync(
                    track = track,
                    preferredQuality = currentPref,
                ) ?: MusicService.resolveStreamUrl(
                    track = track,
                    preferredQuality = currentPref,
                )

            if (!streamUrl.isNullOrBlank()) {
                audioPlayer.playUrl(streamUrl)
            } else {
                _playbackState.update { it.copy(isBuffering = false, isPlaying = false) }
            }
        }
    }

    fun changeQuality(newQuality: MusicQuality) {
        scope.launch {
            MusicSettingsRepository.setStreamingQuality(newQuality.bitrateString)
            val currentTrack = _playbackState.value.currentTrack ?: return@launch
            val currentPos = _playbackState.value.currentPositionMs
            val wasPlaying = _playbackState.value.isPlaying

            _playbackState.update { it.copy(isBuffering = true) }

            val newStreamUrl = MusicService.resolveStreamUrlAsync(
                track = currentTrack,
                preferredQuality = newQuality.bitrateString,
            ) ?: MusicService.resolveStreamUrl(
                track = currentTrack,
                preferredQuality = newQuality.bitrateString,
            )

            if (!newStreamUrl.isNullOrBlank()) {
                val qualityLabel = when (newQuality) {
                    MusicQuality.LOSSLESS_FLAC -> "Hi-Res FLAC"
                    MusicQuality.HIGH_320 -> "320 KBPS"
                    MusicQuality.MEDIUM_160 -> "160 KBPS"
                    MusicQuality.LOW_96 -> "96 KBPS"
                }
                val updatedTrack = currentTrack.copy(
                    streamUrl = newStreamUrl,
                    isFlac = newQuality == MusicQuality.LOSSLESS_FLAC,
                    currentQuality = qualityLabel,
                )
                _playbackState.update {
                    it.copy(
                        currentTrack = updatedTrack,
                        isBuffering = true,
                    )
                }
                audioPlayer.playUrl(newStreamUrl)
                if (currentPos > 0) {
                    audioPlayer.seekTo(currentPos)
                }
                if (!wasPlaying) {
                    audioPlayer.pause()
                }
            } else {
                _playbackState.update { it.copy(isBuffering = false) }
            }
        }
    }

    fun togglePlayPause() {
        val state = _playbackState.value
        if (state.currentTrack == null) return

        if (state.isPlaying) {
            audioPlayer.pause()
            _playbackState.update { it.copy(isPlaying = false) }
        } else {
            audioPlayer.play()
            _playbackState.update { it.copy(isPlaying = true) }
        }
    }

    fun playNext() {
        val state = _playbackState.value
        if (state.queue.isEmpty()) return

        val nextIndex = if (state.isShuffle) {
            (state.queue.indices - state.queueIndex).randomOrNull() ?: 0
        } else {
            when {
                state.queueIndex < state.queue.lastIndex -> state.queueIndex + 1
                state.repeatMode == MusicRepeatMode.ALL -> 0
                else -> return
            }
        }

        val nextTrack = state.queue.getOrNull(nextIndex) ?: return
        playTrack(nextTrack, state.queue)
    }

    fun playPrevious() {
        val state = _playbackState.value
        if (state.queue.isEmpty()) return

        if (state.currentPositionMs > 3000L) {
            seekTo(0L)
            return
        }

        val prevIndex = when {
            state.queueIndex > 0 -> state.queueIndex - 1
            state.repeatMode == MusicRepeatMode.ALL -> state.queue.lastIndex
            else -> 0
        }

        val prevTrack = state.queue.getOrNull(prevIndex) ?: return
        playTrack(prevTrack, state.queue)
    }

    fun seekTo(positionMs: Long) {
        val state = _playbackState.value
        val clamped = positionMs.coerceIn(0L, state.durationMs.coerceAtLeast(1L))
        _playbackState.update { it.copy(currentPositionMs = clamped) }
        audioPlayer.seekTo(clamped)
    }

    fun toggleShuffle() {
        _playbackState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun cycleRepeatMode() {
        _playbackState.update {
            val nextMode = when (it.repeatMode) {
                MusicRepeatMode.OFF -> MusicRepeatMode.ALL
                MusicRepeatMode.ALL -> MusicRepeatMode.ONE
                MusicRepeatMode.ONE -> MusicRepeatMode.OFF
            }
            it.copy(repeatMode = nextMode)
        }
    }

    fun addToQueue(track: MusicTrack) {
        _playbackState.update {
            val updatedQueue = it.queue + track
            it.copy(queue = updatedQueue)
        }
    }

    fun removeFromQueue(index: Int) {
        _playbackState.update {
            if (index !in it.queue.indices) return@update it
            val updatedQueue = it.queue.toMutableList().apply { removeAt(index) }
            val newIdx = when {
                index < it.queueIndex -> it.queueIndex - 1
                index == it.queueIndex -> (it.queueIndex).coerceAtMost(updatedQueue.lastIndex)
                else -> it.queueIndex
            }
            it.copy(queue = updatedQueue, queueIndex = newIdx)
        }
    }

    fun showFullPlayer() {
        _playbackState.update { it.copy(isFullPlayerVisible = true) }
    }

    fun hideFullPlayer() {
        _playbackState.update { it.copy(isFullPlayerVisible = false) }
    }

    fun dismissMiniPlayer() {
        audioPlayer.pause()
        _playbackState.update { it.copy(isMiniPlayerVisible = false, isPlaying = false) }
    }

    private fun handleTrackEnded() {
        val state = _playbackState.value
        when (state.repeatMode) {
            MusicRepeatMode.ONE -> {
                seekTo(0L)
                audioPlayer.play()
            }
            MusicRepeatMode.ALL, MusicRepeatMode.OFF -> {
                if (MusicSettingsRepository.settings.value.autoPlayNext) {
                    if (state.hasNext || state.repeatMode == MusicRepeatMode.ALL) {
                        playNext()
                    } else {
                        _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                    }
                } else {
                    _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                }
            }
        }
    }
}
