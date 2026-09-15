package com.nuvio.app.features.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.music.MusicDownloadManager
import com.nuvio.app.features.music.MusicSettingsRepository

internal fun LazyListScope.musicSettingsContent(
    isTablet: Boolean,
) {
    item {
        MusicSettingsSection(isTablet = isTablet)
    }
}

@Composable
private fun MusicSettingsSection(isTablet: Boolean) {
    MusicSettingsRepository.ensureLoaded()
    MusicDownloadManager.ensureLoaded()

    val settings by MusicSettingsRepository.settings.collectAsStateWithLifecycle()
    val downloadedTracks by MusicDownloadManager.downloadedTracks.collectAsStateWithLifecycle()
    val storageSize = remember(downloadedTracks) { MusicDownloadManager.getFormattedStorageSize() }

    SettingsSection(
        title = "Music System",
        isTablet = isTablet,
    ) {
        SettingsGroup(isTablet = isTablet) {
            SettingsSwitchRow(
                title = "Enable Music",
                description = "Display the Music tab in bottom navigation and enable streaming playback",
                checked = settings.enabled,
                onCheckedChange = { MusicSettingsRepository.setEnabled(it) },
                isTablet = isTablet,
            )
        }

        if (settings.enabled) {
            SettingsSection(
                title = "Audio Quality & Streaming",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = "Streaming Audio Quality",
                        description = when (settings.streamingQuality) {
                            "96kbps" -> "96 kbps (Data Saver)"
                            "160kbps" -> "160 kbps (Standard)"
                            else -> "320 kbps (High Fidelity)"
                        },
                        icon = Icons.Rounded.HighQuality,
                        isTablet = isTablet,
                        onClick = {
                            val next = when (settings.streamingQuality) {
                                "320kbps" -> "160kbps"
                                "160kbps" -> "96kbps"
                                else -> "320kbps"
                            }
                            MusicSettingsRepository.setStreamingQuality(next)
                        },
                    )

                    SettingsGroupDivider(isTablet = isTablet)

                    SettingsNavigationRow(
                        title = "Download Audio Quality",
                        description = when (settings.downloadQuality) {
                            "96kbps" -> "96 kbps (Compact)"
                            "160kbps" -> "160 kbps (Standard)"
                            else -> "320 kbps (Maximum Quality)"
                        },
                        icon = Icons.Rounded.HighQuality,
                        isTablet = isTablet,
                        onClick = {
                            val next = when (settings.downloadQuality) {
                                "320kbps" -> "160kbps"
                                "160kbps" -> "96kbps"
                                else -> "320kbps"
                            }
                            MusicSettingsRepository.setDownloadQuality(next)
                        },
                    )

                    SettingsGroupDivider(isTablet = isTablet)

                    SettingsNavigationRow(
                        title = "Streaming Provider",
                        description = settings.preferredSource,
                        icon = Icons.Rounded.MusicNote,
                        isTablet = isTablet,
                        onClick = {
                            val next = if (settings.preferredSource == "JioSaavn") "YouTube Music" else "JioSaavn"
                            MusicSettingsRepository.setPreferredSource(next)
                        },
                    )
                }
            }

            SettingsSection(
                title = "Playback & Queue",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsSwitchRow(
                        title = "Auto-Play Next in Queue",
                        description = "Automatically continue playback with the next song when current track ends",
                        checked = settings.autoPlayNext,
                        onCheckedChange = { MusicSettingsRepository.setAutoPlayNext(it) },
                        isTablet = isTablet,
                    )

                    SettingsGroupDivider(isTablet = isTablet)

                    SettingsSwitchRow(
                        title = "Audio Normalization",
                        description = "Balance playback volume levels across different tracks and albums",
                        checked = settings.volumeNormalization,
                        onCheckedChange = { MusicSettingsRepository.setVolumeNormalization(it) },
                        isTablet = isTablet,
                    )
                }
            }

            SettingsSection(
                title = "Offline Storage",
                isTablet = isTablet,
            ) {
                SettingsGroup(isTablet = isTablet) {
                    SettingsNavigationRow(
                        title = "Clear Music Downloads",
                        description = "${downloadedTracks.size} offline songs • $storageSize used",
                        icon = Icons.Rounded.DeleteOutline,
                        isTablet = isTablet,
                        onClick = {
                            MusicDownloadManager.clearAllDownloads()
                        },
                    )
                }
            }
        }
    }
}
