package com.nuvio.app.features.music.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.music.MusicLyrics
import com.nuvio.app.features.music.MusicLyricsService
import com.nuvio.app.features.music.MusicPlaybackController
import com.nuvio.app.features.music.MusicTrack
import kotlinx.coroutines.launch

@Composable
fun MusicLyricsView(
    track: MusicTrack,
    currentPositionMs: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var lyrics by remember(track.id) { mutableStateOf<MusicLyrics?>(null) }
    var isLoading by remember(track.id) { mutableStateOf(true) }
    var errorMessage by remember(track.id) { mutableStateOf<String?>(null) }

    fun loadLyrics() {
        isLoading = true
        errorMessage = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            val res = MusicLyricsService.getLyrics(track)
            res.onSuccess {
                lyrics = it
                isLoading = false
            }.onFailure { err ->
                errorMessage = err.message ?: "No lyrics found"
                isLoading = false
            }
        }
    }

    LaunchedEffect(track.id) {
        loadLyrics()
    }

    val listState = rememberLazyListState()
    val activeIndex = remember(lyrics, currentPositionMs) {
        val currentLyrics = lyrics
        if (currentLyrics != null && currentLyrics.isSynced && currentLyrics.lines.isNotEmpty()) {
            val idx = currentLyrics.lines.indexOfLast { it.timeMs <= currentPositionMs }
            if (idx >= 0) idx else 0
        } else 0
    }

    // Auto-scroll to active lyric line
    LaunchedEffect(activeIndex) {
        if (lyrics?.isSynced == true && activeIndex in (lyrics?.lines?.indices ?: 0..-1)) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F1018).copy(alpha = 0.85f))
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Lyrics Badge & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = if (lyrics?.isSynced == true) "SYNCED LYRICS" else "LYRICS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        ),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close Lyrics",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Searching lyrics...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                errorMessage != null || lyrics == null || lyrics?.lines.isNullOrEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(24.dp),
                        ) {
                            Text(
                                text = "No lyrics found for this song",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Button(
                                onClick = { loadLyrics() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Retry Search")
                            }
                        }
                    }
                }
                else -> {
                    val currentLyrics = lyrics!!
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 40.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        itemsIndexed(currentLyrics.lines) { index, line ->
                            val isActive = currentLyrics.isSynced && index == activeIndex
                            val isPassed = currentLyrics.isSynced && index < activeIndex

                            val textColor by animateColorAsState(
                                targetValue = when {
                                    isActive -> MaterialTheme.colorScheme.primary
                                    isPassed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                }
                            )

                            val textAlpha by animateFloatAsState(
                                targetValue = if (isActive) 1f else if (isPassed) 0.55f else 0.35f
                            )

                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = if (isActive) 22.sp else 18.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                    lineHeight = if (isActive) 30.sp else 26.sp,
                                ),
                                color = textColor,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(textAlpha)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = currentLyrics.isSynced && line.timeMs >= 0L) {
                                        MusicPlaybackController.seekTo(line.timeMs)
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
