package com.nuvio.app.features.music.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.LiquidGlassDefaults
import com.nuvio.app.core.ui.liquidGlass
import com.nuvio.app.features.music.MusicDownloadManager
import com.nuvio.app.features.music.MusicLibraryRepository
import com.nuvio.app.features.music.MusicPlaybackController
import com.nuvio.app.features.music.MusicService
import com.nuvio.app.features.music.MusicTab
import com.nuvio.app.features.music.MusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val playbackState by MusicPlaybackController.playbackState.collectAsStateWithLifecycle()
    val likedTracks by MusicLibraryRepository.likedTracks.collectAsStateWithLifecycle()
    val recentTracks by MusicLibraryRepository.recentTracks.collectAsStateWithLifecycle()
    val downloadedTracks by MusicDownloadManager.downloadedTracks.collectAsStateWithLifecycle()
    val downloadProgress by MusicDownloadManager.downloadProgress.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(MusicTab.TRENDING) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var trendingTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isTrendingLoading by remember { mutableStateOf(false) }

    // Load trending songs on launch
    LaunchedEffect(Unit) {
        MusicLibraryRepository.ensureLoaded()
        MusicDownloadManager.ensureLoaded()
        if (trendingTracks.isEmpty()) {
            isTrendingLoading = true
            val res = MusicService.getTrendingSongs()
            trendingTracks = res.getOrDefault(emptyList())
            isTrendingLoading = false
        }
    }

    // Debounced search
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        delay(400)
        isSearching = true
        val res = MusicService.searchSongs(q)
        searchResults = res.getOrDefault(emptyList())
        isSearching = false
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0B10)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset),
        ) {
            // Header Title & Search Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "NetMax Music",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Spacer(Modifier.height(10.dp))

                // Search Bar
                val searchShape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .liquidGlass(shape = searchShape, borderWidth = 1.dp)
                        .clip(searchShape)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(Modifier.width(10.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotBlank() && selectedTab != MusicTab.SEARCH) {
                                selectedTab = MusicTab.SEARCH
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search songs, artists, albums...",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                        fontSize = 14.sp,
                                    ),
                                )
                            }
                            innerTextField()
                        },
                        modifier = Modifier.weight(1f),
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Tab Selector Pills (Trending, Search, Library, Downloads)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MusicTab.entries.forEach { tab ->
                    val isSelected = selectedTab == tab
                    val tabShape = RoundedCornerShape(20.dp)
                    val activeTint = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else null

                    Box(
                        modifier = Modifier
                            .liquidGlass(shape = tabShape, dynamicTint = activeTint, borderWidth = if (isSelected) 1.5.dp else 0.5.dp)
                            .clip(tabShape)
                            .clickable { selectedTab = tab }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            // Main Content Body based on tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (selectedTab) {
                    MusicTab.TRENDING -> TrendingView(
                        tracks = trendingTracks,
                        isLoading = isTrendingLoading,
                        playbackState = playbackState,
                        likedTracks = likedTracks,
                        downloadedTracks = downloadedTracks,
                        downloadProgress = downloadProgress,
                    )
                    MusicTab.SEARCH -> SearchView(
                        query = searchQuery,
                        results = searchResults,
                        isSearching = isSearching,
                        playbackState = playbackState,
                        likedTracks = likedTracks,
                        downloadedTracks = downloadedTracks,
                        downloadProgress = downloadProgress,
                        onSuggestionClick = { q ->
                            searchQuery = q
                        },
                    )
                    MusicTab.LIBRARY -> LibraryView(
                        likedTracks = likedTracks,
                        recentTracks = recentTracks,
                        playbackState = playbackState,
                        downloadedTracks = downloadedTracks,
                        downloadProgress = downloadProgress,
                    )
                    MusicTab.DOWNLOADS -> DownloadsView(
                        downloadedTracks = downloadedTracks,
                        playbackState = playbackState,
                        likedTracks = likedTracks,
                        downloadProgress = downloadProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingView(
    tracks: List<MusicTrack>,
    isLoading: Boolean,
    playbackState: com.nuvio.app.features.music.MusicPlaybackState,
    likedTracks: List<MusicTrack>,
    downloadedTracks: List<MusicTrack>,
    downloadProgress: Map<String, Float>,
) {
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Loading top hits...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No trending songs found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = Color(0xFFFF5722),
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Top Trending Hits",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Button(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                MusicPlaybackController.playTrack(tracks.first(), tracks)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            items(tracks, key = { it.id }) { track ->
                val isActive = playbackState.currentTrack?.id == track.id
                val isLiked = likedTracks.any { it.id == track.id }
                val isDownloaded = downloadedTracks.any { it.id == track.id }
                val isDownloading = downloadProgress.containsKey(track.id)
                val prog = downloadProgress[track.id] ?: 0f

                MusicTrackRow(
                    track = track,
                    isActiveTrack = isActive,
                    isPlaying = isActive && playbackState.isPlaying,
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = prog,
                    onClick = { MusicPlaybackController.playTrack(track, tracks) },
                    onLikeClick = { MusicLibraryRepository.toggleLike(track) },
                    onDownloadClick = { MusicDownloadManager.downloadTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun SearchView(
    query: String,
    results: List<MusicTrack>,
    isSearching: Boolean,
    playbackState: com.nuvio.app.features.music.MusicPlaybackState,
    likedTracks: List<MusicTrack>,
    downloadedTracks: List<MusicTrack>,
    downloadProgress: Map<String, Float>,
    onSuggestionClick: (String) -> Unit,
) {
    val suggestions = listOf(
        "Arijit Singh", "Sidhu Moose Wala", "Imagine Dragons",
        "Ed Sheeran", "Taylor Swift", "Diljit Dosanjh",
        "Badshah", "Shreya Ghoshal", "Anirudh Ravichander", "Coldplay"
    )

    if (query.isBlank()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Text(
                text = "Popular Searches",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowItems.forEach { suggestion ->
                            val chipShape = RoundedCornerShape(14.dp)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .liquidGlass(shape = chipShape, borderWidth = 0.5.dp)
                                    .clip(chipShape)
                                    .clickable { onSuggestionClick(suggestion) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = suggestion,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else if (isSearching) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (results.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No songs found for \"$query\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.id }) { track ->
                val isActive = playbackState.currentTrack?.id == track.id
                val isLiked = likedTracks.any { it.id == track.id }
                val isDownloaded = downloadedTracks.any { it.id == track.id }
                val isDownloading = downloadProgress.containsKey(track.id)
                val prog = downloadProgress[track.id] ?: 0f

                MusicTrackRow(
                    track = track,
                    isActiveTrack = isActive,
                    isPlaying = isActive && playbackState.isPlaying,
                    isLiked = isLiked,
                    isDownloaded = isDownloaded,
                    isDownloading = isDownloading,
                    downloadProgress = prog,
                    onClick = { MusicPlaybackController.playTrack(track, results) },
                    onLikeClick = { MusicLibraryRepository.toggleLike(track) },
                    onDownloadClick = { MusicDownloadManager.downloadTrack(track) },
                )
            }
        }
    }
}

@Composable
private fun LibraryView(
    likedTracks: List<MusicTrack>,
    recentTracks: List<MusicTrack>,
    playbackState: com.nuvio.app.features.music.MusicPlaybackState,
    downloadedTracks: List<MusicTrack>,
    downloadProgress: Map<String, Float>,
) {
    if (likedTracks.isEmpty() && recentTracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF4081).copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your library is empty",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Like tracks and listen to music to see them here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (likedTracks.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = Color(0xFFFF4081),
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Liked Songs (${likedTracks.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Button(
                            onClick = {
                                if (likedTracks.isNotEmpty()) {
                                    MusicPlaybackController.playTrack(likedTracks.first(), likedTracks)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                items(likedTracks, key = { "liked_${it.id}" }) { track ->
                    val isActive = playbackState.currentTrack?.id == track.id
                    val isDownloaded = downloadedTracks.any { it.id == track.id }
                    val isDownloading = downloadProgress.containsKey(track.id)
                    val prog = downloadProgress[track.id] ?: 0f

                    MusicTrackRow(
                        track = track,
                        isActiveTrack = isActive,
                        isPlaying = isActive && playbackState.isPlaying,
                        isLiked = true,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        downloadProgress = prog,
                        onClick = { MusicPlaybackController.playTrack(track, likedTracks) },
                        onLikeClick = { MusicLibraryRepository.toggleLike(track) },
                        onDownloadClick = { MusicDownloadManager.downloadTrack(track) },
                    )
                }
            }

            if (recentTracks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "Recently Played",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { MusicLibraryRepository.clearRecentTracks() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }

                items(recentTracks, key = { "recent_${it.id}" }) { track ->
                    val isActive = playbackState.currentTrack?.id == track.id
                    val isLiked = likedTracks.any { it.id == track.id }
                    val isDownloaded = downloadedTracks.any { it.id == track.id }
                    val isDownloading = downloadProgress.containsKey(track.id)
                    val prog = downloadProgress[track.id] ?: 0f

                    MusicTrackRow(
                        track = track,
                        isActiveTrack = isActive,
                        isPlaying = isActive && playbackState.isPlaying,
                        isLiked = isLiked,
                        isDownloaded = isDownloaded,
                        isDownloading = isDownloading,
                        downloadProgress = prog,
                        onClick = { MusicPlaybackController.playTrack(track, recentTracks) },
                        onLikeClick = { MusicLibraryRepository.toggleLike(track) },
                        onDownloadClick = { MusicDownloadManager.downloadTrack(track) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadsView(
    downloadedTracks: List<MusicTrack>,
    playbackState: com.nuvio.app.features.music.MusicPlaybackState,
    likedTracks: List<MusicTrack>,
    downloadProgress: Map<String, Float>,
) {
    val storageSize = remember(downloadedTracks) { MusicDownloadManager.getFormattedStorageSize() }

    if (downloadedTracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.DownloadDone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    modifier = Modifier.size(54.dp),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "No offline songs",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Download songs to listen offline without internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Downloaded (${downloadedTracks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Storage used: $storageSize",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (downloadedTracks.isNotEmpty()) {
                                    MusicPlaybackController.playTrack(downloadedTracks.first(), downloadedTracks)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = { MusicDownloadManager.clearAllDownloads() },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear All Downloads",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }

            items(downloadedTracks, key = { "dl_${it.id}" }) { track ->
                val isActive = playbackState.currentTrack?.id == track.id
                val isLiked = likedTracks.any { it.id == track.id }
                val isDownloading = downloadProgress.containsKey(track.id)
                val prog = downloadProgress[track.id] ?: 0f

                MusicTrackRow(
                    track = track,
                    isActiveTrack = isActive,
                    isPlaying = isActive && playbackState.isPlaying,
                    isLiked = isLiked,
                    isDownloaded = true,
                    isDownloading = isDownloading,
                    downloadProgress = prog,
                    onClick = { MusicPlaybackController.playTrack(track, downloadedTracks) },
                    onLikeClick = { MusicLibraryRepository.toggleLike(track) },
                    onDownloadClick = { MusicDownloadManager.deleteDownload(track.id) },
                )
            }
        }
    }
}
