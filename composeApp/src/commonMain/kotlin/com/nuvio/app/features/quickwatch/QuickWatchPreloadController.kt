package com.nuvio.app.features.quickwatch

import co.touchlab.kermit.Logger
import com.nuvio.app.features.trailer.TrailerPlaybackResolver
import com.nuvio.app.features.trailer.TrailerPlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages video stream extraction with a strict sliding window of at most 3 videos.
 *
 * Window: [currentIndex, currentIndex + 1, currentIndex + 2]
 *
 * As the user scrolls to a new video:
 * 1. The upcoming video (currentIndex + 2) begins extracting in the background.
 * 2. Videos that fall outside the 3-item window are immediately cancelled and purged from memory,
 *    preventing memory accumulation and avoiding Android Out-Of-Memory (OOM) crashes.
 */
object QuickWatchPreloadController {
    private const val MAX_READY_COUNT = 3
    private val log = Logger.withTag("QuickWatchPreloader")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _playbackStates = MutableStateFlow<Map<String, QuickWatchPlaybackState>>(emptyMap())
    val playbackStates: StateFlow<Map<String, QuickWatchPlaybackState>> = _playbackStates.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    // Lightweight cache of resolved YouTube streams to prevent re-extracting and buffering loops
    private val resolvedCache = mutableMapOf<String, TrailerPlaybackSource>()

    private fun cacheResolvedStream(id: String, source: TrailerPlaybackSource) {
        if (resolvedCache.size >= 30) {
            val firstKey = resolvedCache.keys.firstOrNull()
            if (firstKey != null) resolvedCache.remove(firstKey)
        }
        resolvedCache[id] = source
    }

    fun onCurrentIndexChanged(currentIndex: Int, items: List<QuickWatchItem>) {
        if (items.isEmpty() || currentIndex !in items.indices) return

        // Compute the sliding window: current, previous, and up to 2 ahead
        val startIndex = (currentIndex - 1).coerceAtLeast(0)
        val endIndex = (currentIndex + 2).coerceAtMost(items.lastIndex)
        val targetIndices = (startIndex..endIndex)
        val targetItems = targetIndices.map { items[it] }
        val targetIds = targetItems.map { it.id }.toSet()

        // 1. Evict any media sources and cancel extraction jobs outside the active window
        val currentKeys = _playbackStates.value.keys.toSet()
        val keysToRemove = currentKeys - targetIds
        for (key in keysToRemove) {
            activeJobs[key]?.cancel()
            activeJobs.remove(key)
        }
        if (keysToRemove.isNotEmpty()) {
            _playbackStates.value = _playbackStates.value - keysToRemove
        }

        // 2. Schedule extraction for any item in window that isn't ready or extracting
        for (item in targetItems) {
            val currentState = _playbackStates.value[item.id]
            val alreadyReady = currentState?.videoUrl != null
            val isJobRunning = activeJobs[item.id]?.isActive == true

            if (!alreadyReady && !isJobRunning) {
                val cached = resolvedCache[item.youtubeVideoId]
                if (cached != null) {
                    // Instantly restore from cache without re-extracting or buffering
                    val map = _playbackStates.value.toMutableMap()
                    map[item.id] = QuickWatchPlaybackState(
                        videoUrl = cached.videoUrl,
                        audioUrl = cached.audioUrl,
                        isLoading = false,
                    )
                    _playbackStates.value = map
                } else {
                    startExtraction(item)
                }
            }
        }
    }

    private fun startExtraction(item: QuickWatchItem) {
        val job = scope.launch {
            updateState(item.id, QuickWatchPlaybackState(isLoading = true))
            try {
                val source = TrailerPlaybackResolver.resolveFromYouTubeUrl(item.youtubeUrl)
                if (source != null && source.videoUrl.isNotBlank()) {
                    cacheResolvedStream(item.youtubeVideoId, source)
                    updateState(
                        item.id,
                        QuickWatchPlaybackState(
                            videoUrl = source.videoUrl,
                            audioUrl = source.audioUrl,
                            isLoading = false,
                        ),
                    )
                } else {
                    updateState(
                        item.id,
                        QuickWatchPlaybackState(
                            isLoading = false,
                            isError = true,
                            errorMessage = "Unable to resolve stream",
                        ),
                    )
                }
            } catch (e: Throwable) {
                log.w(e) { "Stream extraction failed for ${item.youtubeUrl}" }
                updateState(
                    item.id,
                    QuickWatchPlaybackState(
                        isLoading = false,
                        isError = true,
                        errorMessage = e.message ?: "Extraction error",
                    ),
                )
            } finally {
                activeJobs.remove(item.id)
            }
        }
        activeJobs[item.id] = job
    }

    private suspend fun updateState(itemId: String, state: QuickWatchPlaybackState) = withContext(Dispatchers.Main) {
        val map = _playbackStates.value.toMutableMap()
        map[itemId] = state
        _playbackStates.value = map
    }

    fun clear() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        resolvedCache.clear()
        _playbackStates.value = emptyMap()
    }
}
