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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
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
                        onSearchArtist = { artistName ->
                            searchQuery = artistName
                            selectedTab = MusicTab.SEARCH
                        },
                        onSelectMood = { mood ->
                            coroutineScope.launch {
                                isTrendingLoading = true
                                val res = if (mood == "Trending") MusicService.getTrendingSongs() else MusicService.searchSongs("$mood hits")
                                trendingTracks = res.getOrDefault(emptyList())
                                isTrendingLoading = false
                            }
                        },
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
    onSearchArtist: (String) -> Unit,
    onSelectMood: (String) -> Unit,
) {
    var activeMood by remember { mutableStateOf("Trending") }
    val moods = listOf("Trending", "Bollywood", "Punjabi", "Romance", "Chill Lo-Fi", "Party", "Workout", "90s Hits")

    val curatedCharts = listOf(
        Triple("India Superhits Top 50", "Top 50 trending songs in India", listOf(Color(0xFFFF5722), Color(0xFF880E4F))),
        Triple("Bollywood Romance", "Soulful love melodies & duets", listOf(Color(0xFFE91E63), Color(0xFF4A148C))),
        Triple("Punjabi Hits 2026", "High-energy bhangra & hip-hop", listOf(Color(0xFFFFB300), Color(0xFFE65100))),
        Triple("Lo-Fi Midnight Chill", "Relaxing vibes, study & sleep", listOf(Color(0xFF3F51B5), Color(0xFF1A237E))),
        Triple("Party EDM Bangers", "Club dance anthems & beats", listOf(Color(0xFF00E5FF), Color(0xFF004D40))),
        Triple("90s Golden Bollywood", "Evergreen romantic nostalgic hits", listOf(Color(0xFFD84315), Color(0xFF3E2723))),
    )

    val popularArtists = listOf(
        "Arijit Singh",
        "Shreya Ghoshal",
        "Diljit Dosanjh",
        "Sidhu Moose Wala",
        "Atif Aslam",
        "Anirudh",
        "Badshah",
        "Karan Aujla",
        "Taylor Swift",
        "The Weeknd",
    )

    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Loading music...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (tracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No songs found for this selection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        val heroTrack = tracks.firstOrNull()
        val quickPickTracks = remember(tracks) { tracks.take(8) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // 1. Mood & Genre Filter Pills (Convx ChipsRow)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    moods.forEach { mood ->
                        val isSelected = activeMood == mood
                        val shape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .clip(shape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .clickable {
                                    activeMood = mood
                                    onSelectMood(mood)
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = mood,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp,
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 2. Hero Spotlight Featured Card (Convx HomeHeroCard)
            if (heroTrack != null) {
                item {
                    val heroShape = RoundedCornerShape(22.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(heroShape)
                            .clickable { MusicPlaybackController.playTrack(heroTrack, tracks) },
                    ) {
                        // Background Art
                        if (heroTrack.artworkUrl.isNotBlank()) {
                            AsyncImage(
                                model = heroTrack.displayArtworkUrl,
                                contentDescription = heroTrack.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                            )
                        }

                        // Gradient Shade
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color(0xC0000000),
                                            Color(0xF50A0B10),
                                        )
                                    )
                                )
                        )

                        // Hero Details
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "FEATURED SPOTLIGHT",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = heroTrack.title,
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Text(
                                text = heroTrack.artist.ifBlank { "Top Hit" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { MusicPlaybackController.playTrack(heroTrack, tracks) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Play Now", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val shuffled = tracks.shuffled()
                                        MusicPlaybackController.playTrack(shuffled.first(), shuffled)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp),
                                ) {
                                    Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Shuffle", fontSize = 13.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Picks (2-row horizontal grid like Convx Speed Dial)
            if (quickPickTracks.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    text = "Quick Picks",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Start listening right away",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }

                        // 2 Rows of 4 items scrolling horizontally
                        val chunked = quickPickTracks.chunked(2)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            chunked.forEach { columnItems ->
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    columnItems.forEach { track ->
                                        val isThisPlaying = playbackState.currentTrack?.id == track.id && playbackState.isPlaying
                                        Row(
                                            modifier = Modifier
                                                .width(240.dp)
                                                .height(58.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    if (isThisPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                                )
                                                .clickable { MusicPlaybackController.playTrack(track, tracks) }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(46.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                if (track.artworkUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = track.displayArtworkUrl,
                                                        contentDescription = track.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize(),
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Rounded.MusicNote,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(22.dp),
                                                    )
                                                }
                                            }

                                            Spacer(Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                    color = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = track.artist.ifBlank { "Track" },
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }

                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = null,
                                                tint = if (isThisPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Top Charts & Playlists Carousel
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.BarChart,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Top Charts & Playlists",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        curatedCharts.forEach { (name, desc, gradient) ->
                            val cardShape = RoundedCornerShape(16.dp)
                            Box(
                                modifier = Modifier
                                    .width(150.dp)
                                    .height(150.dp)
                                    .clip(cardShape)
                                    .background(Brush.linearGradient(gradient))
                                    .clickable { onSelectMood(name) }
                                    .padding(14.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Album,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(28.dp),
                                    )

                                    Column {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = Color.White.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. Popular Artists Rail
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Popular Artists",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        popularArtists.forEach { artistName ->
                            Column(
                                modifier = Modifier
                                    .width(76.dp)
                                    .clickable { onSearchArtist(artistName) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                    MaterialTheme.colorScheme.surfaceVariant,
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = artistName,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }

                                Spacer(Modifier.height(6.dp))

                                Text(
                                    text = artistName,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }

            // 6. Ranked Top Songs Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
                            text = "Top Ranked Songs",
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

            // Ranked Song Rows
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val isActive = playbackState.currentTrack?.id == track.id
                val isLiked = likedTracks.any { it.id == track.id }
                val isDownloaded = downloadedTracks.any { it.id == track.id }
                val isDownloading = downloadProgress.containsKey(track.id)
                val prog = downloadProgress[track.id] ?: 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Rank badge
                    val rankColor = when (index) {
                        0 -> Color(0xFFFFD700) // Gold
                        1 -> Color(0xFFE0E0E0) // Silver
                        2 -> Color(0xFFCD7F32) // Bronze
                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        color = rankColor,
                        modifier = Modifier.width(28.dp),
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.width(6.dp))

                    Box(modifier = Modifier.weight(1f)) {
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
