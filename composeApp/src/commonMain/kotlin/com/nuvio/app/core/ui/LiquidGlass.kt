package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.settings.LiquidGlassSettings
import com.nuvio.app.features.settings.LiquidGlassSettingsRepository
import com.nuvio.app.features.settings.ThemeSettingsRepository
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

object LiquidGlassDefaults {
    val BlurRadius: Dp = 24.dp
    val PillShape: Shape = RoundedCornerShape(NuvioTokens.Radius.full)
    val CardShape: Shape = RoundedCornerShape(18.dp)
    val ButtonShape: Shape = CircleShape

    @Composable
    fun glassBrush(isLight: Boolean, settings: LiquidGlassSettings): Brush {
        val vibrancy = (0.85f + settings.vibrancy * 0.15f).coerceIn(0.7f, 1.25f)
        if (settings.enhancedLiquidGlass) {
            return if (isLight) {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.40f * vibrancy),
                        Color(0xFFF2F2F7).copy(alpha = 0.28f * vibrancy),
                    ),
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF32323C).copy(alpha = 0.26f * vibrancy),
                        Color(0xFF131318).copy(alpha = 0.38f * vibrancy),
                    ),
                )
            }
        }
        return if (isLight) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.68f * vibrancy),
                    Color(0xFFF2F2F7).copy(alpha = 0.56f * vibrancy),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF303038).copy(alpha = 0.50f * vibrancy),
                    Color(0xFF111116).copy(alpha = 0.64f * vibrancy),
                ),
            )
        }
    }

    @Composable
    fun borderBrush(isLight: Boolean, settings: LiquidGlassSettings): Brush {
        val chroma = settings.chromaticAberration
        val base = if (settings.enhancedLiquidGlass) {
            if (isLight) {
                listOf(
                    Color.White.copy(alpha = 0.92f),
                    Color.White.copy(alpha = 0.44f),
                    Color(0xFFD8D8E0).copy(alpha = 0.28f),
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.58f),
                    Color.White.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.08f),
                )
            }
        } else {
            if (isLight) {
                listOf(
                    Color.White.copy(alpha = 0.95f),
                    Color.White.copy(alpha = 0.40f),
                    Color(0xFFD0D0D8).copy(alpha = 0.25f),
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.48f),
                    Color.White.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.05f),
                )
            }
        }
        if (chroma <= 0.01f) return Brush.verticalGradient(base)
        val edge = (0.18f * chroma).coerceIn(0f, 0.18f)
        return Brush.horizontalGradient(
            listOf(
                Color(0xFF72D9FF).copy(alpha = edge),
                base[0],
                base[1],
                base[2],
                Color(0xFFFF79D8).copy(alpha = edge),
            ),
        )
    }

    fun refractionBrush(settings: LiquidGlassSettings, isLight: Boolean): Brush {
        val amount = settings.refractionAmount.coerceIn(0f, 1f)
        val height = settings.refractionHeight.coerceIn(0.05f, 1f)
        if (settings.enhancedLiquidGlass) {
            val tint = if (isLight) Color.White else Color(0xFFF2F8FF)
            return Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to tint.copy(alpha = 0.36f * amount),
                    (height * 0.45f) to tint.copy(alpha = 0.12f * amount),
                    height to tint.copy(alpha = 0.02f * amount),
                    (height + 0.15f).coerceAtMost(1f) to Color.Transparent,
                    1f to Color.Transparent,
                ),
            )
        }
        val tint = if (isLight) Color.White else Color(0xFFE9F5FF)
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0f to tint.copy(alpha = 0.26f * amount),
                height to tint.copy(alpha = 0.05f * amount),
                (height + 0.18f).coerceAtMost(1f) to Color.Transparent,
                1f to Color.Transparent,
            ),
        )
    }
}

@Composable
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassDefaults.PillShape,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    borderWidth: Dp = 1.dp,
    alphaFactor: Float = 1f,
): Modifier {
    LiquidGlassSettingsRepository.ensureLoaded()
    val settings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
    val effectiveEnabled = isEnabled && settings.enabled
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    if (!effectiveEnabled) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape)
    }

    val glassBg = LiquidGlassDefaults.glassBrush(isLight, settings)
    val glassBorder = LiquidGlassDefaults.borderBrush(isLight, settings)
    val tintAlpha = (settings.surfaceOpacity * alphaFactor).coerceIn(0f, 1f)
    val depth = (settings.depthEffect * (if (settings.enhancedLiquidGlass) 18f else 14f)).dp
    val blurRadius = if (settings.enhancedLiquidGlass) {
        (settings.blurRadius * 1.25f).coerceAtLeast(28f).dp
    } else {
        settings.blurRadius.dp
    }
    val noise = if (settings.enhancedLiquidGlass) {
        0.012f
    } else {
        (0.01f + settings.vibrancy * 0.015f).coerceIn(0.01f, 0.04f)
    }

    return this
        .shadow(depth, shape, clip = false)
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(state = hazeState) {
                    this.blurRadius = blurRadius
                    inputScale = HazeInputScale.Fixed(0.5f)
                    noiseFactor = noise
                }
            } else Modifier
        )
        .background(glassBg, shape)
        .background(settings.surfaceTint.copy(alpha = tintAlpha), shape)
        .background(LiquidGlassDefaults.refractionBrush(settings, isLight), shape)
        .border(borderWidth, glassBorder, shape)
}

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    LiquidGlassSettingsRepository.ensureLoaded()
    val settings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
    val resolvedTint = tint ?: settings.textColor

    BoxWithLiquidGlassButton(
        onClick = onClick,
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        hazeState = hazeState,
        isEnabled = isEnabled,
        size = size,
        iconSize = iconSize,
        tint = resolvedTint,
        interactionSource = interactionSource,
    )
}

@Composable
private fun BoxWithLiquidGlassButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier,
    hazeState: HazeState?,
    isEnabled: Boolean,
    size: Dp,
    iconSize: Dp,
    tint: Color,
    interactionSource: MutableInteractionSource,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .liquidGlass(shape = LiquidGlassDefaults.ButtonShape, hazeState = hazeState, isEnabled = isEnabled)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun LiquidGlassBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    contentDescription: String? = "Back",
    size: Dp = 42.dp,
    iconSize: Dp = 20.dp,
) {
    LiquidGlassIconButton(
        onClick = onBack,
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = contentDescription,
        modifier = modifier,
        hazeState = hazeState,
        isEnabled = isEnabled,
        size = size,
        iconSize = iconSize,
    )
}

@Composable
fun LiquidGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    LiquidGlassSettingsRepository.ensureLoaded()
    val settings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(shape = LiquidGlassDefaults.PillShape, hazeState = hazeState, isEnabled = isEnabled)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                if (onBack != null) {
                    LiquidGlassBackButton(onBack = onBack, hazeState = hazeState, isEnabled = isEnabled, size = 36.dp, iconSize = 18.dp)
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = settings.textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) { actions() }
        }
    }
}
