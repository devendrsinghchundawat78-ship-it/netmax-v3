package com.nuvio.app.features.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil3.compose.AsyncImage
import com.nuvio.app.core.format.extractReleaseYearForDisplay
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.home.MetaPreview
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.netmax_logo
import org.jetbrains.compose.resources.painterResource
import kotlin.math.absoluteValue

private const val HERO_CAROUSEL_AUTO_SCROLL_DELAY_MS = 8_000L

@Composable
fun HomeHeroCategoryHeader(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 20.dp,
    statusBarTop: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
) {
    val categories = remember { listOf("Trending", "New", "Movies", "Serials", "TV Shows", "YT Videos") }
    val categoryScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarTop + 12.dp, bottom = 12.dp),
    ) {
        // 1. NETMAX BRANDING: Top-left of hero section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.netmax_logo),
                contentDescription = "NetMax",
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "NetMax",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
                color = Color.White,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 2. CATEGORY NAVIGATION: Directly below NetMax branding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(categoryScrollState)
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            categories.forEach { category ->
                val isSelected = category == selectedCategory
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCategorySelected(category) }
                        .padding(vertical = 2.dp),
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(2.5.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HomeHeroPosterCarouselSection(
    items: List<MetaPreview>,
    modifier: Modifier = Modifier,
    selectedCategory: String = "Trending",
    onCategorySelected: (String) -> Unit = {},
    viewportHeight: Dp? = null,
    listState: LazyListState? = null,
    stretchPx: () -> Float = { 0f },
    onItemClick: ((MetaPreview) -> Unit)? = null,
) {
    if (items.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { items.size })
    val tokens = MaterialTheme.nuvio
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = tokens.colors.background
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Auto-scroll effect
    LaunchedEffect(pagerState.currentPage, items.size) {
        if (items.size <= 1) return@LaunchedEffect
        delay(HERO_CAROUSEL_AUTO_SCROLL_DELAY_MS)
        while (pagerState.isScrollInProgress) {
            delay(100L)
        }
        val nextPage = (pagerState.currentPage + 1) % items.size
        pagerState.animateScrollToPage(
            page = nextPage,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
    ) {
        val screenWidth = maxWidth
        val isTablet = screenWidth >= 600.dp

        // Dynamic responsive poster sizing: 2:3 ratio
        val posterWidth = if (isTablet) {
            (screenWidth * 0.38f).coerceIn(240.dp, 320.dp)
        } else {
            (screenWidth * 0.60f).coerceIn(200.dp, 260.dp)
        }
        val posterHeight = posterWidth * 1.5f
        val horizontalPadding = if (isTablet) 32.dp else 20.dp
        val horizontalContentPadding = ((screenWidth - posterWidth) / 2).coerceAtLeast(16.dp)

        // 4. SOFT-GRADIENT BACKGROUND: Dark cinematic background using NetMax theme colors
        // Blurred atmospheric bloom behind the center poster
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(posterWidth * 1.6f, posterHeight * 1.25f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.24f),
                            primaryColor.copy(alpha = 0.08f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // Vignette top & bottom to maintain high readability and dark edges
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            backgroundColor.copy(alpha = 0.70f),
                            backgroundColor,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            HomeHeroCategoryHeader(
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected,
                horizontalPadding = horizontalPadding,
                statusBarTop = statusBarTop,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. MAIN HERO POSTER CAROUSEL: 2:3 ratio portrait posters with smooth interpolation
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = horizontalContentPadding),
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val item = items[page]
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                val scale = lerp(1f, 0.86f, pageOffset)
                val alpha = lerp(1f, 0.55f, pageOffset)
                val elevation = ((1f - pageOffset) * 14.dp).coerceAtLeast(0.dp)

                Box(
                    modifier = Modifier
                        .width(posterWidth)
                        .height(posterHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                shape = RoundedCornerShape(22.dp)
                                clip = true
                            }
                            .shadow(
                                elevation = elevation,
                                shape = RoundedCornerShape(22.dp),
                                clip = false,
                            )
                            .clickable(enabled = onItemClick != null) {
                                onItemClick?.invoke(item)
                            },
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        AsyncImage(
                            model = item.poster?.takeIf { it.isNotBlank() } ?: item.banner,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 6. HERO MOVIE INFORMATION: Immediately below the active poster
            val activeItem = items.getOrNull(pagerState.currentPage.coerceIn(items.indices))
            if (activeItem != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clickable(enabled = onItemClick != null) { onItemClick?.invoke(activeItem) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Dynamic Movie Title
                    Text(
                        text = activeItem.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTablet) 23.sp else 20.sp,
                        ),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Dynamic metadata row: Year • Genre • Duration • Rating
                    val metaTokens = remember(activeItem) {
                        buildList {
                            // Release Year
                            val year = extractHeroReleaseYear(activeItem)
                            if (!year.isNullOrBlank()) {
                                add(year)
                            }

                            // Genre / Category
                            val genres = activeItem.genres.take(2).joinToString(" • ").ifBlank {
                                activeItem.type.replaceFirstChar(Char::uppercase)
                            }
                            if (genres.isNotBlank()) {
                                add(genres)
                            }

                            // Duration
                            val duration = extractHeroDuration(activeItem.releaseInfo)
                            if (!duration.isNullOrBlank()) {
                                add(duration)
                            }

                            // Rating
                            val rating = activeItem.imdbRating?.takeIf { it.isNotBlank() }
                            if (rating != null) {
                                add("★ $rating")
                            }
                        }
                    }

                    if (metaTokens.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            metaTokens.forEachIndexed { index, token ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(3.5.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.45f)),
                                    )
                                }
                                Text(
                                    text = token,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontWeight = if (token.startsWith("★")) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                    ),
                                    color = if (token.startsWith("★")) {
                                        Color(0xFFFFD700)
                                    } else {
                                        Color.White.copy(alpha = 0.72f)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun extractHeroReleaseYear(meta: MetaPreview): String? {
    meta.releaseInfo?.let { raw ->
        extractReleaseYearForDisplay(raw)?.let { return it.toString() }
        val match = Regex("""\b(19|20)\d{2}\b""").find(raw)
        if (match != null) return match.value
    }
    meta.rawReleaseDate?.let { raw ->
        extractReleaseYearForDisplay(raw)?.let { return it.toString() }
        val match = Regex("""\b(19|20)\d{2}\b""").find(raw)
        if (match != null) return match.value
    }
    return null
}

private fun extractHeroDuration(releaseInfo: String?): String? {
    if (releaseInfo.isNullOrBlank()) return null
    val match = Regex("""(\d+\s*(?:h|hr|hrs|m|min|mins)\b.*)""", RegexOption.IGNORE_CASE).find(releaseInfo)
    return match?.value?.trim()
}
