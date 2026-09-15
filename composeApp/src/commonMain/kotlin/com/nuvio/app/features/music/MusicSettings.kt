package com.nuvio.app.features.music

import kotlinx.serialization.Serializable

@Serializable
data class MusicSettings(
    val enabled: Boolean = true,
    val streamingQuality: String = "320kbps",
    val downloadQuality: String = "320kbps",
    val autoPlayNext: Boolean = true,
    val volumeNormalization: Boolean = true,
    val preferredSource: String = "JioSaavn",
)
