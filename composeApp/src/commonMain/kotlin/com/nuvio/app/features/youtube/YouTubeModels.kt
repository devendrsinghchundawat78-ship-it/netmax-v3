package com.nuvio.app.features.youtube

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeVideoItem(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelTitle: String,
    val channelId: String? = null,
    val channelAvatarUrl: String? = null,
    val duration: String? = null,
    val viewCount: String? = null,
    val publishedTime: String? = null,
    val description: String? = null,
    val is4K: Boolean = false,
    val badges: List<String> = emptyList(),
)

data class YouTubeStreamQuality(
    val label: String,
    val height: Int,
    val videoUrl: String,
    val audioUrl: String? = null,
    val bitrate: Long = 0L,
    val fps: Int = 30,
)

data class YouTubeFeedCategory(
    val id: String,
    val label: String,
    val searchQuery: String,
)

object YouTubePlayerQualityStore {
    private val qualitiesByVideoId = mutableMapOf<String, List<YouTubeStreamQuality>>()

    fun setQualities(videoId: String, list: List<YouTubeStreamQuality>) {
        qualitiesByVideoId[videoId] = list
    }

    fun getQualities(videoId: String): List<YouTubeStreamQuality> = qualitiesByVideoId[videoId].orEmpty()
}
