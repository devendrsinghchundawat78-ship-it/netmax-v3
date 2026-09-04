package com.lagradost.cloudstream3

import java.util.EnumSet

const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

enum class TvType(val value: Int = 0) {
    Movie(0),
    TvSeries(1),
    Anime(2),
    AnimeMovie(3),
    OVA(4),
    Cartoon(5),
    AsianDrama(6),
    Torrent(7),
    Documentary(8),
    Live(9),
    NSFW(10),
    Others(11);

    companion object {
        fun fromValue(value: Int?): TvType =
            values().firstOrNull { it.value == value } ?: Others
    }
}

enum class SearchQuality(val value: Int = 0) {
    HD(0),
    HQ(1),
    SD(2),
    BlueRay(3),
    FourK(4),
    DVD(5),
    CAM(6),
    HDR(7),
    UHD(8),
    WebRip(9),
    TeleSync(10);

    companion object {
        fun fromValue(value: Int?): SearchQuality =
            values().firstOrNull { it.value == value } ?: HD
    }
}

enum class DubStatus(val id: Int = 0) {
    None(0),
    Dubbed(1),
    Subbed(2);

    companion object {
        fun fromId(id: Int?): DubStatus =
            values().firstOrNull { it.id == id } ?: None
    }
}

enum class ShowStatus(val value: Int = 0) {
    Ongoing(0),
    Completed(1);

    companion object {
        fun fromValue(value: Int?): ShowStatus =
            values().firstOrNull { it.value == value } ?: Ongoing
    }
}

data class Score(
    var score: Double? = null,
    var count: Int? = null,
) {
    companion object {
        @JvmStatic
        fun from10(score: String?): Score? {
            val num = score?.toDoubleOrNull() ?: return null
            return Score(score = num)
        }

        @JvmStatic
        fun from10(score: Double?): Score? {
            if (score == null) return null
            return Score(score = score)
        }

        @JvmStatic
        fun from10(score: Float?): Score? {
            if (score == null) return null
            return Score(score = score.toDouble())
        }

        @JvmStatic
        fun from10(score: Int?): Score? {
            if (score == null) return null
            return Score(score = score.toDouble())
        }
    }
}

data class Actor(
    var name: String = "",
    var image: String? = null,
) {
    constructor(name: String) : this(name, null)
}

enum class ActorRole {
    Main,
    Supporting,
    Background
}

data class ActorData(
    var actor: Actor,
    var role: ActorRole? = null,
    var roleString: String? = null,
    var voiceActor: Actor? = null,
)

data class Episode(
    var data: String = "",
    var name: String? = null,
    var season: Int? = null,
    var episode: Int? = null,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var description: String? = null,
    var isFiller: Boolean? = null,
    var date: String? = null,
    var score: Score? = null,
    var runTime: Int? = null,
) {
    var descript: String?
        get() = description
        set(value) { description = value }
}

data class SubtitleFile(
    val lang: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

interface SearchResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var id: Int?
    var quality: SearchQuality?
    var posterHeaders: Map<String, String>?

    companion object {
        fun SearchResponse.addDubStatus(dub: Boolean, sub: Boolean, dubEpisodes: Int? = null, subEpisodes: Int? = null) {}
    }
}

data class MovieSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var year: Int? = null,
    var score: Score? = null,
) : SearchResponse

data class AnimeSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var year: Int? = null,
    var score: Score? = null,
    var dubStatus: EnumSet<DubStatus>? = null,
    var episodes: Map<DubStatus, Int>? = null,
    var otherNames: List<String>? = null,
) : SearchResponse

data class TvSeriesSearchResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    override var posterUrl: String? = null,
    override var id: Int? = null,
    override var quality: SearchQuality? = null,
    override var posterHeaders: Map<String, String>? = null,
    var year: Int? = null,
    var score: Score? = null,
    var episodes: Int? = null,
) : SearchResponse

data class SearchResponseList(
    val list: List<SearchResponse> = emptyList()
)

interface LoadResponse {
    var name: String
    var url: String
    var apiName: String
    var type: TvType
    var posterUrl: String?
    var backgroundPosterUrl: String?
    var logoUrl: String?
    var year: Int?
    var plot: String?
    var rating: Int?
    var score: Score?
    var tags: List<String>?
    var duration: Int?
    var runTime: Int?
    var actors: List<ActorData>?
    var posterHeaders: Map<String, String>?
    var backgroundPosterHeaders: Map<String, String>?
    var logoHeaders: Map<String, String>?
    var contentRating: String?

    companion object {
        var LoadResponse.malId: Int?
            get() = null
            set(_) {}

        var LoadResponse.aniListId: Int?
            get() = null
            set(_) {}

        var LoadResponse.tmdbId: String?
            get() = null
            set(_) {}

        var LoadResponse.imdbId: String?
            get() = null
            set(_) {}

        var LoadResponse.trailerUrl: String?
            get() = null
            set(_) {}

        fun LoadResponse.addMalId(id: Int?) {
            malId = id
        }

        fun LoadResponse.addAniListId(id: Int?) {
            aniListId = id
        }

        fun LoadResponse.addTMDbId(id: String?) {
            tmdbId = id
        }

        fun LoadResponse.addImdbId(id: String?) {
            imdbId = id
        }

        suspend fun LoadResponse.addTrailer(trailerUrl: String?, referer: String? = null, addRaw: Boolean = false) {
            this.trailerUrl = trailerUrl
        }

        fun LoadResponse.addDuration(duration: String?) {}
        fun LoadResponse.addActors(actors: List<String>?) {}
        fun LoadResponse.addRating(rating: String?) {}
        fun LoadResponse.addPoster(url: String?) {
            posterUrl = url
        }
    }
}

data class MovieLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Movie,
    var dataUrl: String = url,
    override var posterUrl: String? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var runTime: Int? = null,
    override var actors: List<ActorData>? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterHeaders: Map<String, String>? = null,
    override var logoHeaders: Map<String, String>? = null,
    override var contentRating: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var comingSoon: Boolean = false,
) : LoadResponse

data class TvSeriesLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.TvSeries,
    var episodes: List<Episode> = emptyList(),
    override var posterUrl: String? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var runTime: Int? = null,
    override var actors: List<ActorData>? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterHeaders: Map<String, String>? = null,
    override var logoHeaders: Map<String, String>? = null,
    override var contentRating: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var showStatus: ShowStatus? = null,
    var seasonNames: List<String>? = null,
    var comingSoon: Boolean = false,
) : LoadResponse

data class AnimeLoadResponse(
    override var name: String,
    override var url: String,
    override var apiName: String,
    override var type: TvType = TvType.Anime,
    var episodes: Map<DubStatus, List<Episode>> = emptyMap(),
    override var posterUrl: String? = null,
    override var backgroundPosterUrl: String? = null,
    override var logoUrl: String? = null,
    override var year: Int? = null,
    override var plot: String? = null,
    override var rating: Int? = null,
    override var score: Score? = null,
    override var tags: List<String>? = null,
    override var duration: Int? = null,
    override var runTime: Int? = null,
    override var actors: List<ActorData>? = null,
    override var posterHeaders: Map<String, String>? = null,
    override var backgroundPosterHeaders: Map<String, String>? = null,
    override var logoHeaders: Map<String, String>? = null,
    override var contentRating: String? = null,
    var recommendations: List<SearchResponse>? = null,
    var showStatus: ShowStatus? = null,
    var japName: String? = null,
    var engName: String? = null,
    var synonyms: List<String>? = null,
    var comingSoon: Boolean = false,
) : LoadResponse {
    fun addEpisodes(status: DubStatus, eps: List<Episode>?) {
        if (eps == null) return
        val map = episodes.toMutableMap()
        map[status] = eps
        episodes = map
    }
}

data class MainPageData(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

data class MainPageRequest(
    val name: String,
    val data: String,
    val horizontalImages: Boolean = false,
)

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>,
    val isHorizontalImages: Boolean = false,
)

data class HomePageResponse(
    val items: List<HomePageList>,
    val hasNext: Boolean = false,
)
