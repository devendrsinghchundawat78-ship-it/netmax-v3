package com.nuvio.app.features.youtube.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.youtube.YouTubePlayerQualityStore
import com.nuvio.app.features.youtube.YouTubeRepository
import com.nuvio.app.features.youtube.YouTubeStreamQuality
import com.nuvio.app.features.youtube.YouTubeVideoItem
import kotlinx.coroutines.launch

@Composable
fun YouTubeVideoDetailScreen(
    videoId: String,
    initialTitle: String = "",
    initialThumbnail: String = "",
    initialChannel: String = "",
    initialIs4K: Boolean = false,
    onBack: () -> Unit,
    onOpenVideo: (YouTubeVideoItem) -> Unit,
    onPlayVideo: (video: YouTubeVideoItem, selectedQuality: YouTubeStreamQuality?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    var video by remember(videoId) {
        mutableStateOf(
            YouTubeVideoItem(
                id = videoId,
                title = initialTitle.ifBlank { "YouTube Video" },
                thumbnailUrl = initialThumbnail.ifBlank { "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg" },
                channelTitle = initialChannel.ifBlank { "YouTube Channel" },
                is4K = initialIs4K,
            )
        )
    }

    val findPreferredQuality = remember {
        { list: List<YouTubeStreamQuality> ->
            list.firstOrNull { it.height == 1080 }
                ?: list.firstOrNull { it.height == 720 }
                ?: list.firstOrNull { it.height <= 1080 }
                ?: list.firstOrNull()
        }
    }

    var streamQualities by remember(videoId) {
        mutableStateOf(YouTubePlayerQualityStore.getQualities(videoId))
    }
    var selectedQuality by remember(videoId) {
        mutableStateOf(findPreferredQuality(streamQualities))
    }
    var isLoadingQualities by remember(videoId) {
        mutableStateOf(streamQualities.isEmpty())
    }
    var showQualityDialog by remember { mutableStateOf(false) }

    var channelVideos by remember(videoId) { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var isLoadingChannelVideos by remember(videoId) { mutableStateOf(true) }
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    // Fetch stream qualities
    LaunchedEffect(videoId) {
        if (streamQualities.isEmpty()) {
            isLoadingQualities = true
            try {
                val extracted = YouTubeRepository.extractStreamQualities(videoId)
                streamQualities = extracted
                if (selectedQuality == null) {
                    selectedQuality = findPreferredQuality(extracted)
                }
                // Update 4K status if 4K stream is detected
                if (extracted.any { it.height >= 2160 }) {
                    video = video.copy(is4K = true)
                }
            } catch (_: Throwable) {
            } finally {
                isLoadingQualities = false
            }
        }
    }

    // Fetch more videos from this channel
    LaunchedEffect(videoId, video.channelTitle) {
        val channel = video.channelTitle.takeIf { it.isNotBlank() && it != "YouTube Channel" }
        if (channel != null) {
            isLoadingChannelVideos = true
            try {
                val results = YouTubeRepository.fetchChannelVideos(
                    channelName = channel,
                    excludeVideoId = videoId,
                )
                channelVideos = results
            } catch (_: Throwable) {
            } finally {
                isLoadingChannelVideos = false
            }
        } else {
            isLoadingChannelVideos = false
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14)),
    ) {
        val isTablet = maxWidth >= 600.dp
        val horizontalPadding = if (isTablet) 32.dp else 16.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            // 1. Top Bar & Hero Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color(0xFF14171E)),
                ) {
                    // Thumbnail
                    AsyncImage(
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )

                    // Cinematic shadow overlays
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.70f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.85f),
                                    ),
                                )
                            ),
                    )

                    // Top Back Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = statusBarTop + 8.dp, start = 12.dp)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.60f))
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    // 4K Badge (Prominently displayed)
                    if (video.is4K || streamQualities.any { it.height >= 2160 }) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = statusBarTop + 12.dp, end = 16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFE5A00D), Color(0xFFFF5722))
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "4K UHD",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }

                    // Large Center Play Button
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE5A00D).copy(alpha = 0.90f))
                            .clickable {
                                onPlayVideo(video, selectedQuality)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play Video",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    // Duration Badge bottom right
                    if (!video.duration.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = video.duration ?: "",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // 2. Video Title & Metadata
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 14.dp),
                ) {
                    Text(
                        text = video.title,
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 25.sp,
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Channel Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (!video.channelAvatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = video.channelAvatarUrl,
                                contentDescription = video.channelTitle,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF222834)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF262E3D)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = video.channelTitle.firstOrNull()?.uppercase() ?: "Y",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = video.channelTitle,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                if (!video.viewCount.isNullOrBlank()) {
                                    Text(
                                        text = video.viewCount ?: "",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 12.sp,
                                    )
                                }
                                if (!video.publishedTime.isNullOrBlank()) {
                                    Text(
                                        text = "• ${video.publishedTime}",
                                        color = Color.White.copy(alpha = 0.45f),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // 3. Action Buttons Row: Play & Quality
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Play Button
                        Button(
                            onClick = {
                                onPlayVideo(video, selectedQuality)
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D)),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play Video",
                                color = Color.Black,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Quality Selector Button
                        OutlinedButton(
                            onClick = { showQualityDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.White.copy(alpha = 0.08f),
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.linearGradient(
                                    listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.12f))
                                )
                            ),
                        ) {
                            if (isLoadingQualities) {
                                CircularProgressIndicator(
                                    color = Color(0xFFE5A00D),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quality", color = Color.White, fontSize = 13.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.HighQuality,
                                    contentDescription = null,
                                    tint = Color(0xFFE5A00D),
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = selectedQuality?.label?.substringBefore(" ") ?: "Quality",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Share Button
                        IconButton(
                            onClick = {
                                val url = "https://youtu.be/$videoId"
                                clipboardManager.setText(AnnotatedString(url))
                                NuvioToastController.show("Video link copied to clipboard")
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // 4. Description Box (if available)
                    if (!video.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF141822))
                                .clickable { isDescriptionExpanded = !isDescriptionExpanded }
                                .padding(14.dp)
                                .animateContentSize(),
                        ) {
                            Column {
                                Text(
                                    text = video.description ?: "",
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isDescriptionExpanded) "Show less" else "Show more",
                                    color = Color(0xFFE5A00D),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // 5. "More from this channel" ("इस चैनल के और वीडियो") Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartDisplay,
                            contentDescription = null,
                            tint = Color(0xFFE5A00D),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "More from ${video.channelTitle}",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isLoadingChannelVideos) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFFE5A00D),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    } else if (channelVideos.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = horizontalPadding, vertical = 12.dp),
                        ) {
                            Text(
                                text = "No additional videos found from this channel.",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 13.sp,
                            )
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = horizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            items(channelVideos, key = { it.id }) { item ->
                                Box(modifier = Modifier.width(220.dp)) {
                                    YouTubeVideoCard(
                                        video = item,
                                        onClick = { onOpenVideo(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quality selection dialog
        if (showQualityDialog) {
            YouTubeQualitySelectionDialog(
                qualities = streamQualities,
                selectedQuality = selectedQuality,
                onSelectQuality = { quality ->
                    selectedQuality = quality
                },
                onDismiss = { showQualityDialog = false },
            )
        }
    }
}
