package com.nuvio.app.features.netmax.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Liquid Metal chrome ring and metallic specular effect from libraries.dev (https://libraries.dev/metal.html).
 * Provides a high-crafted iridescent chrome sheen for buttons, pills, and badges.
 */
@Composable
fun LiquidMetalPill(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val shimmerPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f, // 2 * PI
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    val chromeBrush = remember(shimmerPhase, accentColor) {
        val sx = cos(shimmerPhase) * 120f
        val sy = sin(shimmerPhase) * 120f

        val baseHighlight = accentColor?.copy(alpha = 0.85f) ?: Color(0xFFF3F4F6)
        val midTone = accentColor?.copy(alpha = 0.35f) ?: Color(0xFF9CA3AF)
        val deepTone = accentColor?.copy(alpha = 0.15f) ?: Color(0xFF374151)

        Brush.linearGradient(
            colors = listOf(
                baseHighlight,
                midTone,
                deepTone,
                baseHighlight.copy(alpha = 0.95f),
                deepTone,
                midTone,
                baseHighlight,
            ),
            start = Offset(-sx, -sy),
            end = Offset(sx + 300f, sy + 150f),
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF222226),
                        Color(0xFF161619),
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = chromeBrush,
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                        onClick = onClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/**
 * Liquid Metal chrome icon ring for avatars or actions.
 */
@Composable
fun LiquidMetalRing(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ringWidth: Dp = 1.5.dp,
    accentColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.283185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    val metalBrush = remember(angle, accentColor) {
        val dx = cos(angle) * 80f
        val dy = sin(angle) * 80f
        val hi = accentColor ?: Color(0xFFE5E7EB)
        val lo = accentColor?.copy(alpha = 0.2f) ?: Color(0xFF4B5563)
        Brush.linearGradient(
            colors = listOf(hi, lo, hi, lo, hi),
            start = Offset(-dx, -dy),
            end = Offset(dx + 100f, dy + 100f),
        )
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .border(width = ringWidth, brush = metalBrush, shape = CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
