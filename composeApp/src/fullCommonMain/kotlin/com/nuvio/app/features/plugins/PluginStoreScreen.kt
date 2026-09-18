package com.nuvio.app.features.plugins

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioInfoBadge
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioScreen
import com.nuvio.app.core.ui.NuvioScreenHeader
import com.nuvio.app.core.ui.NuvioSurfaceCard
import com.nuvio.app.features.plugins.runtime.PluginRuntime
import kotlinx.coroutines.launch

private enum class PluginStoreFilter(val label: String) {
    ALL("All"),
    INSTALLED("Installed"),
    AVAILABLE("Available"),
    CLOUDSTREAM("CloudStream"),
    NETMAX("NetMax"),
}

@Composable
fun PluginStoreScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        PluginRepository.initialize()
    }

    NuvioScreen(
        modifier = modifier.fillMaxSize(),
    ) {
        stickyHeader {
            NuvioScreenHeader(
                title = "Plugin Store",
                onBack = onBack,
                actions = {
                    val uiState by PluginRepository.uiState.collectAsStateWithLifecycle()
                    val isAnyRefreshing = uiState.repositories.any { it.isRefreshing }
                    if (isAnyRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = { PluginRepository.refreshAll() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Plugins",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            )
        }

        item {
            PluginStorePageContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
fun PluginStorePageContent(
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        PluginRepository.initialize()
    }

    val uiState by PluginRepository.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(PluginStoreFilter.ALL) }

    var configuringScraper by remember { mutableStateOf<PluginScraper?>(null) }
    var configuringLayout by remember { mutableStateOf<com.nuvio.app.features.plugins.runtime.PluginSettingsLayout?>(null) }

    val allScrapers = uiState.scrapers
    val installedCount = allScrapers.count { it.enabled }
    val availableCount = allScrapers.count { !it.enabled }

    val filteredScrapers = remember(allScrapers, searchQuery, selectedFilter) {
        val query = searchQuery.trim().lowercase()
        allScrapers.filter { scraper ->
            // Search filter
            val matchesSearch = query.isEmpty() ||
                scraper.name.lowercase().contains(query) ||
                scraper.description.lowercase().contains(query) ||
                scraper.repositoryUrl.lowercase().contains(query) ||
                scraper.supportedTypes.any { it.lowercase().contains(query) }

            if (!matchesSearch) return@filter false

            // Category filter
            when (selectedFilter) {
                PluginStoreFilter.ALL -> true
                PluginStoreFilter.INSTALLED -> scraper.enabled
                PluginStoreFilter.AVAILABLE -> !scraper.enabled
                PluginStoreFilter.CLOUDSTREAM -> scraper.formats?.contains("cs3") == true || scraper.filename.endsWith(".cs3") || scraper.repositoryUrl.contains("cloudstream", ignoreCase = true)
                PluginStoreFilter.NETMAX -> !scraper.formats.orEmpty().contains("cs3") && !scraper.filename.endsWith(".cs3") && !scraper.repositoryUrl.contains("cloudstream", ignoreCase = true)
            }
        }.sortedWith(
            compareByDescending<PluginScraper> { it.enabled }
                .thenBy { it.name.lowercase() }
        )
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Search Input
        NuvioInputField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = "Search ${allScrapers.size} plugins and providers...",
        )

        // Filter Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PluginStoreFilter.entries.forEach { filter ->
                val count = when (filter) {
                    PluginStoreFilter.ALL -> allScrapers.size
                    PluginStoreFilter.INSTALLED -> installedCount
                    PluginStoreFilter.AVAILABLE -> availableCount
                    PluginStoreFilter.CLOUDSTREAM -> allScrapers.count { it.formats?.contains("cs3") == true || it.filename.endsWith(".cs3") || it.repositoryUrl.contains("cloudstream", ignoreCase = true) }
                    PluginStoreFilter.NETMAX -> allScrapers.count { !it.formats.orEmpty().contains("cs3") && !it.filename.endsWith(".cs3") && !it.repositoryUrl.contains("cloudstream", ignoreCase = true) }
                }

                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Summary Bar & Quick Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${filteredScrapers.size} plugins shown • $installedCount installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (filteredScrapers.any { !it.enabled }) {
                    Text(
                        text = "Install All",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                filteredScrapers.filterNot { it.enabled }.forEach { scraper ->
                                    PluginRepository.toggleScraper(scraper.id, true)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                if (filteredScrapers.any { it.enabled }) {
                    Text(
                        text = "Uninstall All",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                filteredScrapers.filter { it.enabled }.forEach { scraper ->
                                    PluginRepository.toggleScraper(scraper.id, false)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Empty state
        if (filteredScrapers.isEmpty()) {
            NuvioSurfaceCard {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No plugins matching \"$searchQuery\"" else "No plugins available in this section",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            // Plugins List
            filteredScrapers.forEach { scraper ->
                PluginStoreCard(
                    scraper = scraper,
                    onConfigureClick = if (scraper.hasSettings) {
                        {
                            coroutineScope.launch {
                                val layout = PluginRuntime.getPluginSettingsLayout(scraper.code, scraper.id)
                                if (layout != null) {
                                    configuringScraper = scraper
                                    configuringLayout = layout
                                }
                            }
                        }
                    } else null,
                )
            }
        }
    }

    configuringScraper?.let { scraper ->
        configuringLayout?.let { layout ->
            PluginSettingsDialog(
                scraper = scraper,
                layout = layout,
                onDismiss = {
                    configuringScraper = null
                    configuringLayout = null
                },
            )
        }
    }
}

@Composable
private fun PluginStoreCard(
    scraper: PluginScraper,
    onConfigureClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val isCs3 = scraper.formats?.contains("cs3") == true || scraper.filename.endsWith(".cs3")
    val isInstalled = scraper.enabled

    NuvioSurfaceCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Icon
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isInstalled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Extension,
                            contentDescription = null,
                            tint = if (isInstalled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = scraper.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )

                            if (isInstalled) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Installed",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = scraper.description.ifBlank { "Streaming provider extension" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Action Buttons: Settings + Install/Uninstall
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onConfigureClick != null) {
                        IconButton(
                            onClick = onConfigureClick,
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }

                    if (isInstalled) {
                        OutlinedButton(
                            onClick = { PluginRepository.toggleScraper(scraper.id, false) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Uninstall",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            )
                        }
                    } else {
                        Button(
                            onClick = { PluginRepository.toggleScraper(scraper.id, true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Install",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }

            // Badges row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Engine format badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isCs3) Color(0xFF6C5CE7).copy(alpha = 0.18f)
                            else Color(0xFF00B894).copy(alpha = 0.18f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = if (isCs3) "CS3" else "JS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (isCs3) Color(0xFF9D8DF1) else Color(0xFF00D2A0),
                    )
                }

                if (scraper.repositoryUrl.contains("cloudstream", ignoreCase = true)) {
                    NuvioInfoBadge(text = "CloudStream")
                } else {
                    NuvioInfoBadge(text = "NetMax")
                }

                if (scraper.supportedTypes.isNotEmpty()) {
                    NuvioInfoBadge(text = scraper.supportedTypes.joinToString(" • "))
                }

                if (scraper.version.isNotBlank()) {
                    NuvioInfoBadge(text = "v${scraper.version}")
                }
            }
        }
    }
}
