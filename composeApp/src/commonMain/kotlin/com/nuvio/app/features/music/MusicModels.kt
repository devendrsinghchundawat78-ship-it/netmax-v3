package com.nuvio.app.features.music

import kotlinx.serialization.Serializable

@Serializable
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val durationSeconds: Int = 0,
    val streamUrl: String? = null,
    val encryptedMediaUrl: String? = null,
    val bitrate320Available: Boolean = true,
    val localFilePath: String? = null,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false,
    val addedAtMs: Long = 0L,
) {
    val durationFormatted: String
        get() {
            if (durationSeconds <= 0) return "--:--"
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            return "$minutes:${seconds.toString().padStart(2, '0')}"
        }

    val displayArtworkUrl: String
        get() = artworkUrl
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
}

enum class MusicRepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class MusicQuality(val label: String, val bitrateString: String) {
    LOW_96("96 kbps (Data Saver)", "96kbps"),
    MEDIUM_160("160 kbps (Standard)", "160kbps"),
    HIGH_320("320 kbps (High Quality)", "320kbps");

    companion object {
        fun fromBitrate(bitrate: String): MusicQuality = when (bitrate) {
            "96kbps", "96" -> LOW_96
            "160kbps", "160" -> MEDIUM_160
            else -> HIGH_320
        }
    }
}

data class MusicPlaybackState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<MusicTrack> = emptyList(),
    val queueIndex: Int = -1,
    val repeatMode: MusicRepeatMode = MusicRepeatMode.OFF,
    val isShuffle: Boolean = false,
    val isMiniPlayerVisible: Boolean = false,
    val isFullPlayerVisible: Boolean = false,
) {
    val hasNext: Boolean
        get() = if (repeatMode == MusicRepeatMode.ALL) queue.isNotEmpty() else queueIndex in 0 until (queue.size - 1)

    val hasPrevious: Boolean
        get() = if (repeatMode == MusicRepeatMode.ALL) queue.isNotEmpty() else queueIndex > 0

    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

enum class MusicTab(val label: String) {
    TRENDING("Trending"),
    SEARCH("Search"),
    LIBRARY("Library"),
    DOWNLOADS("Downloads"),
}
