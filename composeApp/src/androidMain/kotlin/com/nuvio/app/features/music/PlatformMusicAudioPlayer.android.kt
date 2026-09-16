package com.nuvio.app.features.music

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal actual class PlatformMusicAudioPlayer actual constructor() {
    companion object {
        private var appContext: Context? = null

        fun initialize(context: Context) {
            appContext = context.applicationContext
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null

    private var onStateChangedCallback: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null
    private var onProgressCallback: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    init {
        initPlayer()
    }

    private fun initPlayer() {
        val ctx = appContext ?: return
        if (exoPlayer != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        exoPlayer = ExoPlayer.Builder(ctx)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                onStateChangedCallback?.invoke(playWhenReady, true)
                            }
                            Player.STATE_READY -> {
                                onStateChangedCallback?.invoke(playWhenReady, false)
                            }
                            Player.STATE_ENDED -> {
                                onStateChangedCallback?.invoke(false, false)
                                onEndedCallback?.invoke()
                            }
                            Player.STATE_IDLE -> {
                                onStateChangedCallback?.invoke(false, false)
                            }
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        onStateChangedCallback?.invoke(isPlaying, false)
                        if (isPlaying) {
                            startProgressPolling()
                        } else {
                            stopProgressPolling()
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        onErrorCallback?.invoke(error.message ?: "Playback error")
                    }
                })
            }.also { player ->
                try {
                    com.nuvio.app.features.equalizer.PlatformAudioEqualizer.attachAudioSession(player.audioSessionId)
                } catch (_: Throwable) {}
            }
    }

    actual fun playUrl(url: String, startPositionMs: Long) {
        initPlayer()
        val player = exoPlayer ?: return
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem, startPositionMs)
        player.prepare()
        player.playWhenReady = true
        startProgressPolling()
    }

    actual fun play() {
        exoPlayer?.play()
        startProgressPolling()
    }

    actual fun pause() {
        exoPlayer?.pause()
        stopProgressPolling()
    }

    actual fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    actual fun setVolume(volume: Float) {
        exoPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    actual fun release() {
        stopProgressPolling()
        exoPlayer?.let { player ->
            try {
                com.nuvio.app.features.equalizer.PlatformAudioEqualizer.detachAudioSession(player.audioSessionId)
            } catch (_: Throwable) {}
            player.release()
        }
        exoPlayer = null
    }

    actual fun setCallbacks(
        onStateChanged: (isPlaying: Boolean, isBuffering: Boolean) -> Unit,
        onProgress: (currentMs: Long, totalMs: Long) -> Unit,
        onEnded: () -> Unit,
        onError: (String) -> Unit,
    ) {
        this.onStateChangedCallback = onStateChanged
        this.onProgressCallback = onProgress
        this.onEndedCallback = onEnded
        this.onErrorCallback = onError
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = exoPlayer
                if (player != null && player.isPlaying) {
                    val current = player.currentPosition.coerceAtLeast(0L)
                    val duration = player.duration.coerceAtLeast(0L)
                    onProgressCallback?.invoke(current, duration)
                }
                delay(300)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }
}
