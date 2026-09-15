package com.nuvio.app.features.music

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.rate
import platform.AVFoundation.seekToTime
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

internal actual class PlatformMusicAudioPlayer actual constructor() {
    private var avPlayer: AVPlayer? = null
    private var onStateChangedCallback: ((isPlaying: Boolean, isBuffering: Boolean) -> Unit)? = null
    private var onProgressCallback: ((currentMs: Long, totalMs: Long) -> Unit)? = null
    private var onEndedCallback: (() -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    @OptIn(ExperimentalForeignApi::class)
    actual fun playUrl(url: String, startPositionMs: Long) {
        val nsUrl = NSURL(string = url)
        val item = AVPlayerItem(uRL = nsUrl)
        if (avPlayer == null) {
            avPlayer = AVPlayer(playerItem = item)
        } else {
            avPlayer?.replaceCurrentItemWithPlayerItem(item)
        }
        if (startPositionMs > 0) {
            val cmTime = CMTimeMakeWithSeconds(startPositionMs / 1000.0, 1000)
            avPlayer?.seekToTime(cmTime)
        }
        avPlayer?.play()
        onStateChangedCallback?.invoke(true, false)
    }

    actual fun play() {
        avPlayer?.play()
        onStateChangedCallback?.invoke(true, false)
    }

    actual fun pause() {
        avPlayer?.pause()
        onStateChangedCallback?.invoke(false, false)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun seekTo(positionMs: Long) {
        val cmTime = CMTimeMakeWithSeconds(positionMs / 1000.0, 1000)
        avPlayer?.seekToTime(cmTime)
    }

    actual fun setVolume(volume: Float) {
        avPlayer?.volume = volume.coerceIn(0f, 1f)
    }

    actual fun release() {
        avPlayer?.pause()
        avPlayer = null
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
}
