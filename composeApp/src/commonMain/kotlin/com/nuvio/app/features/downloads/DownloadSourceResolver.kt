package com.nuvio.app.features.downloads

import com.nuvio.app.features.debrid.DirectDebridPlaybackResolver
import com.nuvio.app.features.player.PlayerStreamsRepository
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamDebridCacheState
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/** Loads the same provider universe used by the player, then exposes only real file URLs. */
object DownloadSourceResolver {
    suspend fun loadDownloadableSources(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
    ): List<StreamItem> {
        PlayerStreamsRepository.loadSources(
            type = type,
            videoId = videoId,
            season = season,
            episode = episode,
            forceRefresh = true,
        )

        // Guarded wait: if stream resolution never finishes (stuck provider),
        // fall back to the latest snapshot instead of hanging the download UI.
        val state = try {
            withTimeout(30_000) {
                PlayerStreamsRepository.sourceState.first { state ->
                    !state.isAnyLoading || state.emptyStateReason != null
                }
            }
        } catch (_: TimeoutCancellationException) {
            PlayerStreamsRepository.sourceState.value
        }

        val direct = state.groups
            .flatMap { it.streams }
            .filter { it.isDownloadableFileSource() }

        // A cached/direct-debrid source may only become downloadable after resolution.
        val debridCandidates = state.groups
            .flatMap { it.streams }
            .filter { stream ->
                stream !in direct &&
                    (stream.isDirectDebridStream || stream.debridCacheStatus?.state == StreamDebridCacheState.CACHED) &&
                    DirectDebridPlaybackResolver.shouldResolveToPlayableStream(stream)
            }
            .take(MAX_DEBRID_RESOLVES)

        if (debridCandidates.isEmpty()) return direct.distinctBy(::sourceIdentity)

        val resolved = buildList {
            addAll(direct)
            debridCandidates.forEach { candidate ->
                val result = runCatching {
                    DirectDebridPlaybackResolver.resolveToPlayableStream(candidate, season, episode)
                }.getOrNull()
                val stream = (result as? com.nuvio.app.features.debrid.DirectDebridPlayableResult.Success)?.stream
                if (stream?.isDownloadableFileSource() == true) add(stream)
            }
        }
        return resolved.distinctBy(::sourceIdentity)
    }

    fun bestSource(sources: List<StreamItem>): StreamItem? =
        sources
            .filter { it.isDownloadableFileSource() }
            .maxWithOrNull(compareByDescending<StreamItem> { it.downloadQualityScore }
                // At equal quality, a single direct file is more reliable than an HLS playlist.
                .thenByDescending { !it.isHlsDownloadSource }
                .thenByDescending { it.downloadAvailabilityScore }
                .thenByDescending { it.downloadSpeedScore })

    fun sourceQuality(stream: StreamItem): String = stream.downloadQualityLabel

    fun sourceSizeBytes(stream: StreamItem): Long? =
        stream.behaviorHints.videoSize
            ?: stream.clientResolve?.stream?.raw?.size
            ?: stream.clientResolve?.stream?.raw?.folderSize
            ?: stream.debridCacheStatus?.cachedSize

    fun sourceExtension(stream: StreamItem): String = stream.downloadFileExtension

    private fun sourceIdentity(stream: StreamItem): String =
        listOf(stream.addonId, stream.playableDirectUrl.orEmpty(), stream.behaviorHints.filename.orEmpty()).joinToString("|")

    private const val MAX_DEBRID_RESOLVES = 4
}

internal val StreamItem.downloadableFileUrl: String?
    get() = playableDirectUrl?.trim()?.takeIf { it.isSupportedDownloadFileUrl() }

internal val StreamItem.downloadFileExtension: String
    get() {
        val candidates = listOfNotNull(
            downloadableFileUrl?.substringBefore('?')?.substringBefore('#')?.substringAfterLast('.', ""),
            behaviorHints.filename?.substringBefore('?')?.substringBefore('#')?.substringAfterLast('.', ""),
            clientResolve?.filename?.substringBefore('?')?.substringBefore('#')?.substringAfterLast('.', ""),
        )
        val raw = candidates.firstOrNull { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?.lowercase()
            ?: "mp4"
        // HLS playlists are saved as a single concatenated transport-stream file.
        return if (raw == "m3u8") "ts" else raw
    }

internal val StreamItem.isHlsDownloadSource: Boolean
    get() = downloadableFileUrl?.isHlsPlaylistUrl() == true

internal val StreamItem.downloadQualityScore: Int
    get() {
        val text = buildString {
            append(name.orEmpty()).append(' ')
            append(title.orEmpty()).append(' ')
            append(description.orEmpty()).append(' ')
            append(behaviorHints.filename.orEmpty()).append(' ')
            append(clientResolve?.stream?.raw?.filename.orEmpty()).append(' ')
            append(clientResolve?.stream?.raw?.parsed?.resolution.orEmpty())
        }.lowercase()
        return when {
            Regex("\\b(4320p|8k)\\b").containsMatchIn(text) -> 8
            Regex("\\b(2160p|4k|uhd)\\b").containsMatchIn(text) -> 7
            Regex("\\b1440p\\b").containsMatchIn(text) -> 6
            Regex("\\b1080p\\b|full[ .-]?hd").containsMatchIn(text) -> 5
            Regex("\\b720p\\b|hd").containsMatchIn(text) -> 4
            Regex("\\b576p\\b").containsMatchIn(text) -> 3
            Regex("\\b480p\\b|sd").containsMatchIn(text) -> 2
            else -> 1
        }
    }

internal val StreamItem.downloadAvailabilityScore: Int
    get() = when {
        isDirectDebridStream -> 4
        debridCacheStatus?.state == StreamDebridCacheState.CACHED -> 3
        !behaviorHints.proxyHeaders?.request.isNullOrEmpty() -> 2
        else -> 1
    }

/** Smaller files at the same quality generally start/download faster on mobile networks. */
internal val StreamItem.downloadSpeedScore: Int
    get() {
        val bytes = DownloadSourceResolver.sourceSizeBytes(this) ?: return 0
        return when {
            bytes <= 1L * 1024 * 1024 * 1024 -> 5
            bytes <= 3L * 1024 * 1024 * 1024 -> 4
            bytes <= 6L * 1024 * 1024 * 1024 -> 3
            bytes <= 12L * 1024 * 1024 * 1024 -> 2
            else -> 1
        }
    }

internal val StreamItem.downloadQualityLabel: String
    get() = when (downloadQualityScore) {
        8 -> "8K"
        7 -> "4K"
        6 -> "1440p"
        5 -> "1080p"
        4 -> "720p"
        3 -> "576p"
        2 -> "480p"
        else -> "Unknown"
    }

internal fun StreamItem.isDownloadableFileSource(): Boolean =
    downloadableFileUrl != null

internal fun String.isSupportedDownloadFileUrl(): Boolean {
    val normalized = trim()
    if (!normalized.startsWith("http://", ignoreCase = true) && !normalized.startsWith("https://", ignoreCase = true)) {
        return false
    }
    // HLS playlists are downloadable: the platform downloader fetches the
    // playlist, downloads every segment and concatenates them into one file.
    if (normalized.isHlsPlaylistUrl()) return true
    val lower = normalized.lowercase()
    if (lower.contains(".mpd") || lower.contains(".torrent")) return false

    val path = lower.substringBefore('?').substringBefore('#')
    val extension = path.substringAfterLast('.', "")
    return extension in setOf("mp4", "mkv", "webm", "m4v", "mov", "avi", "ts", "mpeg", "mpg")
}

/** True for `http(s)` URLs whose path points at an HLS playlist (`.m3u8`). */
internal fun String.isHlsPlaylistUrl(): Boolean {
    val normalized = trim()
    if (!normalized.startsWith("http://", ignoreCase = true) && !normalized.startsWith("https://", ignoreCase = true)) {
        return false
    }
    val path = normalized.substringBefore('?').substringBefore('#').lowercase()
    return path.endsWith(".m3u8")
}

data class DownloadTarget(
    val contentType: String,
    val videoId: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val title: String,
    val logo: String?,
    val poster: String?,
    val background: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val episodeTitle: String?,
    val episodeThumbnail: String?,
)
