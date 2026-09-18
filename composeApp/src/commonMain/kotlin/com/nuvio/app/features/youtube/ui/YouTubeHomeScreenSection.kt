package com.nuvio.app.features.youtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.features.youtube.YouTubeFeedCategory
import com.nuvio.app.features.youtube.YouTubeRepository
import com.nuvio.app.features.youtube.YouTubeVideoItem
import kotlinx.coroutines.launch

@Composable
fun YouTubeHomeScreenSection(
    onOpenVideo: (YouTubeVideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val categories = remember { YouTubeRepository.defaultCategories }
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var searchQuery by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<YouTubeVideoItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadVideos(query: String, isSearch: Boolean = false) {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val results = if (isSearch) {
                    YouTubeRepository.searchVideos(query)
                } else {
                    YouTubeRepository.fetchFeed(query)
                }
                videos = results
                if (results.isEmpty()) {
                    errorMessage = "No YouTube videos found. Please try another query."
                }
            } catch (e: Throwable) {
                errorMessage = "Failed to load YouTube videos: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedCategory) {
        if (searchQuery.isBlank()) {
            loadVideos(selectedCategory.searchQuery, isSearch = false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14)),
    ) {
        val isTablet = maxWidth >= 600.dp
        val columns = if (isTablet) 3 else 1
        val horizontalPadding = if (isTablet) 24.dp else 16.dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 8.dp,
                bottom = 96.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Header span: Search Bar
            item(span = { GridItemSpan(columns) }) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    YouTubeSearchBar(
                        query = searchQuery,
                        onQueryChange = {
                            searchQuery = it
                            if (it.isBlank()) {
                                loadVideos(selectedCategory.searchQuery, isSearch = false)
                            }
                        },
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                loadVideos(searchQuery, isSearch = true)
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    YouTubeFeedCategoryChips(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { cat ->
                            selectedCategory = cat
                            searchQuery = ""
                        },
                    )
                }
            }

            if (isLoading) {
                item(span = { GridItemSpan(columns) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFE5A00D),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Fetching YouTube 4K & Trending videos...",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            } else if (errorMessage != null && videos.isEmpty()) {
                item(span = { GridItemSpan(columns) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SmartDisplay,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = errorMessage ?: "No videos found",
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                            )
                            Button(
                                onClick = { loadVideos(selectedCategory.searchQuery) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5A00D)),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(videos, key = { it.id }) { video ->
                    YouTubeVideoCard(
                        video = video,
                        onClick = { onOpenVideo(video) },
                    )
                }
            }
        }
    }
}
