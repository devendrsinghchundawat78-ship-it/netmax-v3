package com.nuvio.app.features.quickwatch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.LibraryRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QuickWatchScreen(
    modifier: Modifier = Modifier,
    onPosterClick: (MetaPreview) -> Unit,
    onPlayMovie: ((type: String, id: String, title: String, poster: String?, background: String?, logo: String?) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    val feed by QuickWatchFeedRepository.feed.collectAsStateWithLifecycle()
    val isLoadingFeed by QuickWatchFeedRepository.isLoading.collectAsStateWithLifecycle()
    val playbackStates by QuickWatchPreloadController.playbackStates.collectAsStateWithLifecycle()
    val settings by QuickWatchSettingsRepository.settings.collectAsStateWithLifecycle()

    var isMuted by rememberSaveable(settings.autoMute) { mutableStateOf(settings.autoMute) }
    var isPausedManually by remember { mutableStateOf(false) }
    var activeCommentItem by remember { mutableStateOf<QuickWatchItem?>(null) }
    var savedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        QuickWatchSettingsRepository.ensureLoaded()
        QuickWatchFeedRepository.ensureLoaded()
    }

    DisposableEffect(Unit) {
        onDispose {
            QuickWatchPreloadController.clear()
        }
    }

    if (feed.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoadingFeed) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading Quick Watch...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No Quick Watch clips available",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { QuickWatchFeedRepository.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        }
        return
    }

    val pagerState = rememberPagerState(pageCount = { feed.size })

    // Strict 3-video sliding window: actively preload current and next 2 items
    LaunchedEffect(pagerState.currentPage, feed.size) {
        if (feed.isNotEmpty() && pagerState.currentPage in feed.indices) {
            val currentItem = feed[pagerState.currentPage]
            QuickWatchFeedRepository.markVideoAsSeen(currentItem.youtubeVideoId)
            QuickWatchPreloadController.onCurrentIndexChanged(pagerState.currentPage, feed)
        }
        if (pagerState.currentPage >= feed.size - 3) {
            QuickWatchFeedRepository.loadMore()
        }
        isPausedManually = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        VerticalPager(
            state = pagerState,
            key = { pageIndex -> feed[pageIndex].id },
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
        ) { pageIndex ->
            val item = feed[pageIndex]
            val isCurrentPage = pagerState.currentPage == pageIndex
            val playbackState = playbackStates[item.id]

            var currentProgressFraction by remember { mutableFloatStateOf(0f) }
            var showHeartBurst by remember { mutableStateOf(false) }
            var showPlayPauseIndicator by remember { mutableStateOf(false) }

            val isItemSaved = savedIds.contains(item.id) || item.isSaved

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(item.id) {
                        detectTapGestures(
                            onTap = {
                                isPausedManually = !isPausedManually
                                showPlayPauseIndicator = true
                                coroutineScope.launch {
                                    delay(650)
                                    showPlayPauseIndicator = false
                                }
                            },
                            onDoubleTap = {
                                if (!item.isLiked) {
                                    QuickWatchFeedRepository.toggleLike(item.youtubeVideoId)
                                }
                                showHeartBurst = true
                                coroutineScope.launch {
                                    delay(800)
                                    showHeartBurst = false
                                }
                            },
                        )
                    },
            ) {
                // Background Poster / Thumbnail
                val thumbnail = item.movieBackdrop ?: item.moviePoster ?: "https://img.youtube.com/vi/${item.youtubeVideoId}/hqdefault.jpg"
                AsyncImage(
                    model = thumbnail,
                    contentDescription = item.movieTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Video Surface
                if (playbackState?.videoUrl != null) {
                    QuickWatchPlayerSurface(
                        sourceUrl = playbackState.videoUrl,
                        sourceAudioUrl = playbackState.audioUrl,
                        playWhenReady = isCurrentPage && !isPausedManually,
                        muted = isMuted,
                        modifier = Modifier.fillMaxSize(),
                        onProgress = { currentMs, totalMs ->
                            if (totalMs > 0) {
                                currentProgressFraction = (currentMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                            }
                        },
                    )
                }

                // Loading Indicator
                if (playbackState?.isLoading == true && isCurrentPage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                }

                // Error / Unavailable Fallback
                if (playbackState?.isError == true && isCurrentPage) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Trailer unavailable on YouTube",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "You can watch the full movie directly in NetMax",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f),
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (onPlayMovie != null && item.isWatchable) {
                                        onPlayMovie(item.mediaType, item.metaId, item.movieTitle, item.moviePoster, item.movieBackdrop, item.movieLogo)
                                    } else {
                                        onPosterClick(item.toMetaPreview())
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Watch Movie")
                            }
                        }
                    }
                }

                // Gradient scrims for readability
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.95f)),
                            ),
                        ),
                )

                // Play / Pause Tap Indicator
                AnimatedVisibility(
                    visible = showPlayPauseIndicator,
                    enter = fadeIn() + scaleIn(initialScale = 0.7f),
                    exit = fadeOut() + scaleOut(targetScale = 1.2f),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isPausedManually) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                // Double Tap Heart Burst Animation
                AnimatedVisibility(
                    visible = showHeartBurst,
                    enter = scaleIn(initialScale = 0.3f, animationSpec = tween(250, easing = FastOutSlowInEasing)) + fadeIn(),
                    exit = scaleOut(targetScale = 1.4f, animationSpec = tween(400)) + fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Liked",
                        tint = Color(0xFFFF2E56),
                        modifier = Modifier.size(100.dp),
                    )
                }

                val isCenter = settings.overlayPosition == "center"
                val isMinimal = settings.overlayPosition == "minimal"

                // Right Action Rail (Conditional on Settings)
                if (settings.showActionRail) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 96.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        // Like Button
                        ActionRailItem(
                            icon = if (item.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            label = item.likeCount.toString(),
                            tint = if (item.isLiked) Color(0xFFFF2E56) else Color.White,
                            onClick = { QuickWatchFeedRepository.toggleLike(item.youtubeVideoId) },
                        )

                        // Comment Button
                        ActionRailItem(
                            icon = Icons.AutoMirrored.Rounded.Chat,
                            label = item.commentCount.toString(),
                            tint = Color.White,
                            onClick = { activeCommentItem = item },
                        )

                        // Save / Bookmark to NetMax Library
                        ActionRailItem(
                            icon = if (isItemSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            label = "Save",
                            tint = if (isItemSaved) MaterialTheme.colorScheme.primary else Color.White,
                            onClick = {
                                coroutineScope.launch {
                                    LibraryRepository.ensureLoaded()
                                    LibraryRepository.toggleSaved(item.toLibraryItem())
                                    val updated = savedIds.toMutableSet()
                                    if (updated.contains(item.id)) {
                                        updated.remove(item.id)
                                        NuvioToastController.show("Removed from Library")
                                    } else {
                                        updated.add(item.id)
                                        NuvioToastController.show("Saved to Library")
                                    }
                                    savedIds = updated
                                }
                            },
                        )

                        // Share Button
                        ActionRailItem(
                            icon = Icons.Rounded.Share,
                            label = "Share",
                            tint = Color.White,
                            onClick = {
                                runCatching {
                                    uriHandler.openUri(item.youtubeUrl)
                                }
                            },
                        )

                        // Movie Details Info Icon
                        ActionRailItem(
                            icon = Icons.Rounded.Info,
                            label = "Details",
                            tint = Color.White,
                            onClick = { onPosterClick(item.toMetaPreview()) },
                        )
                    }
                }

                // Bottom Movie Info & "WATCH NOW" Button
                Column(
                    modifier = Modifier
                        .align(if (isCenter) Alignment.BottomCenter else Alignment.BottomStart)
                        .fillMaxWidth(if (isCenter) 0.90f else if (settings.showActionRail) 0.80f else 0.94f)
                        .padding(
                            start = if (isCenter) 0.dp else 16.dp,
                            end = if (isCenter) 0.dp else 16.dp,
                            bottom = 96.dp,
                        ),
                    horizontalAlignment = if (isCenter) Alignment.CenterHorizontally else Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Movie Title & Year
                    Text(
                        text = buildString {
                            append(item.movieTitle)
                            item.releaseYear?.let { append(" ($it)") }
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = if (isCenter) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                    )

                    // Tags / Chips Row (omitted in minimal mode if preferred)
                    if (!isMinimal) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (isCenter) Arrangement.Center else Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            ) {
                                Text(
                                    text = item.videoType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }

                            item.voteAverage?.let { rating ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.White.copy(alpha = 0.15f),
                                ) {
                                    Text(
                                        text = "★ $rating",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFFFD700),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    )
                                }
                            }

                            if (item.genres.isNotEmpty()) {
                                Text(
                                    text = item.genres.take(2).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    // Movie Description snippet (conditional on showOverview setting and not minimal)
                    if (settings.showOverview && !isMinimal && item.movieOverview.isNotBlank()) {
                        Text(
                            text = item.movieOverview,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = if (isCenter) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Start,
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // "WATCH NOW" CTA Button
                    Row(
                        modifier = if (isCenter) Modifier.fillMaxWidth() else Modifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isCenter) Arrangement.Center else Arrangement.spacedBy(10.dp),
                    ) {
                        Surface(
                            onClick = {
                                if (item.isWatchable && onPlayMovie != null) {
                                    onPlayMovie(
                                        item.mediaType,
                                        item.metaId,
                                        item.movieTitle,
                                        item.moviePoster,
                                        item.movieBackdrop,
                                        item.movieLogo,
                                    )
                                } else {
                                    onPosterClick(item.toMetaPreview())
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = if (item.isWatchable) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                            contentColor = if (item.isWatchable) MaterialTheme.colorScheme.onPrimary else Color.White,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = if (item.isWatchable) Icons.Filled.PlayArrow else Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = if (item.isWatchable) "WATCH MOVIE" else "VIEW DETAILS",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                // Progress Bar at bottom of reel
                LinearProgressIndicator(
                    progress = { currentProgressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 76.dp), // sits right above bottom navigation bar
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                )
            }
        }

        // Top Screen Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Quick Watch",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )

            // Mute / Unmute Button
            IconButton(
                onClick = { isMuted = !isMuted },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.Filled.VolumeMute else Icons.Filled.VolumeUp,
                    contentDescription = if (isMuted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Comments Bottom Sheet
        activeCommentItem?.let { commentItem ->
            QuickWatchCommentSheet(
                item = commentItem,
                onDismiss = { activeCommentItem = null },
            )
        }
    }
}

@Composable
private fun ActionRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        ),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
        )
    }
}
