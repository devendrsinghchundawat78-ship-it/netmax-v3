package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.isIos
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.action_close
import nuvio.composeapp.generated.resources.player_video_settings_brightness
import nuvio.composeapp.generated.resources.player_video_settings_contrast
import nuvio.composeapp.generated.resources.player_video_settings_deband
import nuvio.composeapp.generated.resources.player_video_settings_deband_desc
import nuvio.composeapp.generated.resources.player_video_settings_gamma
import nuvio.composeapp.generated.resources.player_video_settings_hdr_peak_detection
import nuvio.composeapp.generated.resources.player_video_settings_hdr_peak_detection_desc
import nuvio.composeapp.generated.resources.player_video_settings_interpolation
import nuvio.composeapp.generated.resources.player_video_settings_interpolation_desc
import nuvio.composeapp.generated.resources.player_video_settings_output_preset
import nuvio.composeapp.generated.resources.player_video_settings_reset_tuning
import nuvio.composeapp.generated.resources.player_video_settings_saturation
import nuvio.composeapp.generated.resources.player_video_settings_title
import nuvio.composeapp.generated.resources.player_video_settings_tone_mapping
import nuvio.composeapp.generated.resources.player_visual_enhancer_desc
import nuvio.composeapp.generated.resources.player_visual_enhancer_fine_tune
import nuvio.composeapp.generated.resources.player_visual_enhancer_presets
import nuvio.composeapp.generated.resources.player_visual_enhancer_title
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

@Composable
internal fun IosVideoSettingsModal(
    visible: Boolean,
    settings: PlayerSettingsUiState,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanel(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            PlayerPanelHeader(
                title = stringResource(Res.string.player_video_settings_title),
            ) {
                PlayerDialogButton(
                    label = stringResource(Res.string.player_video_settings_reset_tuning),
                    onClick = {
                        PlayerSettingsRepository.resetIosVideoOutputTuning()
                        onSettingsChanged()
                    },
                )
                PlayerDialogButton(
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                )
            }

            Spacer(Modifier.height(16.dp))

            var activeTab by remember { mutableIntStateOf(0) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == 0) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { activeTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Visual Enhancer",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                            ),
                            color = if (activeTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == 1) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { activeTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Audio Equalizer",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                            ),
                            color = if (activeTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (activeTab == 1) {
                    com.nuvio.app.features.equalizer.EqualizerPanel(
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                // Visual Enhancer Hero Section
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = if (settings.visualEnhancerEnabled) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(16.dp),
                        ),
                    color = if (settings.visualEnhancerEnabled) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                    },
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        color = if (settings.visualEnhancerEnabled) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (settings.visualEnhancerEnabled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(modifier = Modifier.size(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.player_visual_enhancer_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                )
                                Text(
                                    text = stringResource(Res.string.player_visual_enhancer_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                )
                            }

                            Spacer(modifier = Modifier.size(8.dp))

                            Switch(
                                checked = settings.visualEnhancerEnabled,
                                onCheckedChange = { enabled ->
                                    PlayerSettingsRepository.setVisualEnhancerEnabled(enabled)
                                    onSettingsChanged()
                                },
                            )
                        }

                        AnimatedVisibility(
                            visible = settings.visualEnhancerEnabled,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                )

                                OptionGroup(
                                    title = stringResource(Res.string.player_visual_enhancer_presets),
                                    options = VisualEnhancerMode.entries,
                                    selected = settings.visualEnhancerMode,
                                    label = { it.label },
                                    description = { it.description },
                                    onSelect = { mode ->
                                        PlayerSettingsRepository.setVisualEnhancerMode(mode)
                                        onSettingsChanged()
                                    },
                                )

                                AnimatedVisibility(
                                    visible = settings.visualEnhancerMode == VisualEnhancerMode.Custom,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically(),
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Text(
                                            text = stringResource(Res.string.player_visual_enhancer_fine_tune),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                        )

                                        PictureSlider(
                                            title = stringResource(Res.string.player_video_settings_brightness),
                                            value = settings.iosBrightness,
                                            onValueChanged = {
                                                PlayerSettingsRepository.setIosBrightness(it)
                                                onSettingsChanged()
                                            },
                                        )
                                        PictureSlider(
                                            title = stringResource(Res.string.player_video_settings_contrast),
                                            value = settings.iosContrast,
                                            onValueChanged = {
                                                PlayerSettingsRepository.setIosContrast(it)
                                                onSettingsChanged()
                                            },
                                        )
                                        PictureSlider(
                                            title = stringResource(Res.string.player_video_settings_saturation),
                                            value = settings.iosSaturation,
                                            onValueChanged = {
                                                PlayerSettingsRepository.setIosSaturation(it)
                                                onSettingsChanged()
                                            },
                                        )
                                        PictureSlider(
                                            title = stringResource(Res.string.player_video_settings_gamma),
                                            value = settings.iosGamma,
                                            onValueChanged = {
                                                PlayerSettingsRepository.setIosGamma(it)
                                                onSettingsChanged()
                                            },
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                        .clickable { activeTab = 1 }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Text("Audio Equalizer & Surround", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    Text("Open →", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                if (isIos) {
                    OptionGroup(
                        title = stringResource(Res.string.player_video_settings_output_preset),
                        options = IosVideoOutputPreset.entries,
                        selected = settings.iosVideoOutputPreset,
                        label = { it.localizedLabel() },
                        description = { it.localizedDescription() },
                        onSelect = {
                            PlayerSettingsRepository.setIosVideoOutputPreset(it)
                            onSettingsChanged()
                        },
                    )

                    ToggleRow(
                        title = stringResource(Res.string.player_video_settings_hdr_peak_detection),
                        description = stringResource(Res.string.player_video_settings_hdr_peak_detection_desc),
                        checked = settings.iosHdrComputePeakEnabled,
                        onCheckedChange = {
                            PlayerSettingsRepository.setIosHdrComputePeakEnabled(it)
                            onSettingsChanged()
                        },
                    )

                    OptionGroup(
                        title = stringResource(Res.string.player_video_settings_tone_mapping),
                        options = IosToneMappingMode.entries,
                        selected = settings.iosToneMappingMode,
                        label = { it.label },
                        onSelect = {
                            PlayerSettingsRepository.setIosToneMappingMode(it)
                            onSettingsChanged()
                        },
                    )

                    ToggleRow(
                        title = stringResource(Res.string.player_video_settings_deband),
                        description = stringResource(Res.string.player_video_settings_deband_desc),
                        checked = settings.iosDebandEnabled,
                        onCheckedChange = {
                            PlayerSettingsRepository.setIosDebandEnabled(it)
                            onSettingsChanged()
                        },
                    )
                    ToggleRow(
                        title = stringResource(Res.string.player_video_settings_interpolation),
                        description = stringResource(Res.string.player_video_settings_interpolation_desc),
                        checked = settings.iosInterpolationEnabled,
                        onCheckedChange = {
                            PlayerSettingsRepository.setIosInterpolationEnabled(it)
                            onSettingsChanged()
                        },
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(text = title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(text = description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PictureSlider(
    title: String,
    value: Int,
    onValueChanged: (Int) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(text = value.toString(), color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChanged(it.roundToInt().coerceIn(-50, 50)) },
            valueRange = -50f..50f,
            steps = 99,
        )
    }
}

@Composable
private fun <T> OptionGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable ((T) -> String)? = null,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(option) },
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = label(option), color = MaterialTheme.colorScheme.onSurface)
                            val subtitle = description?.invoke(option)
                            if (!subtitle.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
