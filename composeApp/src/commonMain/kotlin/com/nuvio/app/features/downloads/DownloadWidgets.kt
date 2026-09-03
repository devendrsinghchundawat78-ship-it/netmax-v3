package com.nuvio.app.features.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.nuvio.app.core.ui.liquidGlass
import com.nuvio.app.features.streams.StreamItem

@Composable
fun DownloadMiniWidget(
    modifier: Modifier = Modifier,
    item: DownloadItem?,
) {
    if (item == null) return
    var expanded by remember(item.id) { mutableStateOf(false) }

    val progress by animateFloatAsState(
        targetValue = item.progressFraction,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "download_progress",
    )

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .liquidGlass(shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (item.status == DownloadStatus.Downloading && item.totalBytes != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(50.dp),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
            } else if (item.status == DownloadStatus.Downloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(50.dp),
                    strokeWidth = 3.dp,
                )
            }
            IconButton(onClick = { expanded = true }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = when (item.status) {
                        DownloadStatus.Completed -> Icons.Default.CloudDownload
                        DownloadStatus.Paused -> Icons.Default.PlayArrow
                        else -> Icons.Default.CloudDownload
                    },
                    contentDescription = "Download progress",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }

    if (expanded) {
        DownloadProgressPopup(
            item = item,
            onDismiss = { expanded = false },
        )
    }
}

@Composable
private fun DownloadProgressPopup(
    item: DownloadItem,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .liquidGlass(shape = RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
            }

            val progress = item.progressFraction
            if (item.totalBytes != null) {
                Text("${(progress * 100).toInt()}% • ${formatDownloadBytes(item.downloadedBytes)} / ${formatDownloadBytes(item.totalBytes)}")
            } else {
                Text("${formatDownloadBytes(item.downloadedBytes)} downloaded")
            }

            if (item.totalBytes != null) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeWidth = 6.dp,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (item.status) {
                    DownloadStatus.Downloading -> {
                        Button(onClick = { DownloadsRepository.pauseDownload(item.id); onDismiss() }) {
                            Icon(Icons.Default.Pause, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Pause")
                        }
                    }
                    DownloadStatus.Paused,
                    DownloadStatus.Failed,
                    -> {
                        Button(onClick = { DownloadsRepository.resumeDownload(item.id); onDismiss() }) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Resume")
                        }
                    }
                    DownloadStatus.Completed -> Unit
                }
                TextButton(onClick = { DownloadsRepository.cancelDownload(item.id); onDismiss() }) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun DownloadSourcePickerDialog(
    sources: List<StreamItem>,
    title: String,
    onDismiss: () -> Unit,
    onSelected: (StreamItem) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .liquidGlass(shape = RoundedCornerShape(28.dp))
                .padding(18.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Downloadable sources only", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources.take(30), key = { "${it.addonId}:${it.playableDirectUrl}:${it.behaviorHints.filename}" }) { stream ->
                    DownloadSourceRow(stream = stream, onClick = { onSelected(stream) })
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadSourceRow(
    stream: StreamItem,
    onClick: () -> Unit,
) {
    val size = DownloadSourceResolver.sourceSizeBytes(stream)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .liquidGlass(shape = RoundedCornerShape(18.dp), alphaFactor = 0.72f)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .liquidGlass(shape = CircleShape, alphaFactor = 0.65f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stream.addonName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${stream.downloadQualityLabel} • ${stream.downloadFileExtension.uppercase()}${size?.let { " • ${formatDownloadBytes(it)}" } ?: ""}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Icon(Icons.Default.Speed, null, modifier = Modifier.size(20.dp))
    }
}

private fun formatDownloadBytes(bytes: Long): String {
    var value = bytes.coerceAtLeast(0L).toDouble()
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${value.toLong()} ${units[index]}"
    } else {
        val rounded = kotlin.math.round(value * 10.0) / 10.0
        "${rounded} ${units[index]}"
    }
}
