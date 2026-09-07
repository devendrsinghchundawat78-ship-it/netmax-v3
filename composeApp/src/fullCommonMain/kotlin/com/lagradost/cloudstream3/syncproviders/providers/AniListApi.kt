@file:OptIn(com.lagradost.cloudstream3.InternalAPI::class, com.lagradost.cloudstream3.Prerelease::class)
package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.syncproviders.SyncAPI
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Only the data shapes providers touch are declared here (recommendations and
 * airing schedule); login/library syncing is handled by this app elsewhere.
 */
class AniListApi(index: Int = 0) : SyncAPI(SyncIdName.Anilist) {
    override val name = "AniList"
    override val key = "anilist"
    override val apiDocUrl = "https://docs.anilist.co"

    @Serializable
    data class Title(
        @JsonProperty("romaji") @SerialName("romaji") var romaji: String? = null,
        @JsonProperty("english") @SerialName("english") var english: String? = null,
        @JsonProperty("native") @SerialName("native") var native: String? = null,
    )

    @Serializable
    data class MediaTitle(
        @JsonProperty("romaji") @SerialName("romaji") var romaji: String? = null,
        @JsonProperty("english") @SerialName("english") var english: String? = null,
    )

    @Serializable
    data class CoverImage(
        @JsonProperty("extraLarge") @SerialName("extraLarge") var extraLarge: String? = null,
        @JsonProperty("large") @SerialName("large") var large: String? = null,
        @JsonProperty("medium") @SerialName("medium") var medium: String? = null,
        @JsonProperty("color") @SerialName("color") var color: String? = null,
    )

    @Serializable
    data class MediaCoverImage(
        @JsonProperty("large") @SerialName("large") var large: String? = null,
        @JsonProperty("medium") @SerialName("medium") var medium: String? = null,
        @JsonProperty("color") @SerialName("color") var color: String? = null,
    )

    @Serializable
    data class RecommendedMedia(
        @JsonProperty("id") @SerialName("id") var id: Int? = null,
        @JsonProperty("title") @SerialName("title") var title: MediaTitle? = null,
        @JsonProperty("coverImage") @SerialName("coverImage") var coverImage: MediaCoverImage? = null,
        @JsonProperty("format") @SerialName("format") var format: String? = null,
        @JsonProperty("type") @SerialName("type") var type: String? = null,
    )

    @Serializable
    data class Recommendation(
        @JsonProperty("mediaRecommendation") @SerialName("mediaRecommendation") var mediaRecommendation: RecommendedMedia? = null,
        @JsonProperty("rating") @SerialName("rating") var rating: Int? = null,
        @JsonProperty("userRating") @SerialName("userRating") var userRating: String? = null,
    )

    @Serializable
    data class RecommendationEdge(
        @JsonProperty("node") @SerialName("node") var node: Recommendation? = null,
    )

    @Serializable
    data class RecommendationConnection(
        @JsonProperty("edges") @SerialName("edges") var edges: List<RecommendationEdge>? = null,
    )

    @Serializable
    data class LikePageInfo(
        @JsonProperty("total") @SerialName("total") var total: Int? = null,
        @JsonProperty("currentPage") @SerialName("currentPage") var currentPage: Int? = null,
        @JsonProperty("lastPage") @SerialName("lastPage") var lastPage: Int? = null,
        @JsonProperty("hasNextPage") @SerialName("hasNextPage") var hasNextPage: Boolean? = null,
    )

    @Serializable
    data class SeasonNextAiringEpisode(
        @JsonProperty("episode") @SerialName("episode") var episode: Int? = null,
        @JsonProperty("airingAt") @SerialName("airingAt") var airingAt: Int? = null,
        @JsonProperty("timeUntilAiring") @SerialName("timeUntilAiring") var timeUntilAiring: Int? = null,
    )
}
