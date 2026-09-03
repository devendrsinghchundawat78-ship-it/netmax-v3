package com.nuvio.app.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

/**
 * Liquid Glass styling inspired by modern fluid glass materials with real-time blur,
 * specular refraction highlights, and performance-tuned downsampled sampling.
 */
object LiquidGlassDefaults {
    val BlurRadius: Dp = 24.dp
    val PillShape: Shape = RoundedCornerShape(NuvioTokens.Radius.full)
    val CardShape: Shape = RoundedCornerShape(18.dp)
    val ButtonShape: Shape = CircleShape

    @Composable
    fun glassBrush(isLight: Boolean, alphaFactor: Float = 1f): Brush {
        return if (isLight) {
            Brush.verticalGradient(
                listOf(
                    Color(0xFFFFFFFF).copy(alpha = 0.82f * alphaFactor),
                    Color(0xFFF2F2F7).copy(alpha = 0.65f * alphaFactor),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color(0xFF282830).copy(alpha = 0.58f * alphaFactor),
                    Color(0xFF141418).copy(alpha = 0.68f * alphaFactor),
                ),
            )
        }
    }

    @Composable
    fun borderBrush(isLight: Boolean, alphaFactor: Float = 1f): Brush {
        return if (isLight) {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.95f * alphaFactor),
                    Color.White.copy(alpha = 0.40f * alphaFactor),
                    Color(0xFFD0D0D8).copy(alpha = 0.25f * alphaFactor),
                ),
            )
        } else {
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.48f * alphaFactor),
                    Color.White.copy(alpha = 0.16f * alphaFactor),
                    Color.White.copy(alpha = 0.05f * alphaFactor),
                ),
            )
        }
    }
}

/**
 * Applies a Liquid Glass effect with real-time blur, refraction rim gradient, and smooth clipping.
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape = LiquidGlassDefaults.PillShape,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    borderWidth: Dp = 1.dp,
    alphaFactor: Float = 1f,
): Modifier {
    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f

    if (!isEnabled) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .border(borderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), shape)
    }

    val glassBg = LiquidGlassDefaults.glassBrush(isLight, alphaFactor)
    val glassBorder = LiquidGlassDefaults.borderBrush(isLight, alphaFactor)

    return this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(state = hazeState) {
                    blurRadius = LiquidGlassDefaults.BlurRadius
                    inputScale = HazeInputScale.Fixed(0.5f) // Half-res input scale: lag-free on all mobile GPUs
                    noiseFactor = 0.025f
                }
            } else {
                Modifier
            },
        )
        .background(glassBg)
        .border(borderWidth, glassBorder, shape)
}

/**
 * A floating liquid glass pill button for back buttons, close buttons, and top action buttons.
 */
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(
                shape = LiquidGlassDefaults.ButtonShape,
                hazeState = hazeState,
                isEnabled = isEnabled,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = size / 2),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Floating liquid glass back button.
 */
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

/**
 * Floating liquid glass top app bar container.
 */
@Composable
fun LiquidGlassTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    isEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .liquidGlass(
                shape = LiquidGlassDefaults.PillShape,
                hazeState = hazeState,
                isEnabled = isEnabled,
            )
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
                    LiquidGlassBackButton(
                        onBack = onBack,
                        hazeState = hazeState,
                        isEnabled = isEnabled,
                        size = 36.dp,
                        iconSize = 18.dp,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                actions()
            }
        }
    }
}
