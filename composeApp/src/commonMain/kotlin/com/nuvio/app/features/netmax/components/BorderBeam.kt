package com.nuvio.app.features.netmax.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Color presets from libraries.dev (https://libraries.dev/beam.html)
 */
enum class BorderBeamVariant {
    Colorful, // Rainbow neon: Violet -> Pink -> Cyan -> Amber
    Ocean,    // Cyan -> Deep Azure -> Electric Teal
    Sunset,   // Magenta -> Vivid Coral -> Gold
    Mono,     // White -> Silver -> Translucent Gray
    Assistant,// NetMax AI palette: Electric Violet -> Cyan -> Gold
}

/**
 * Animated border beam modifier from libraries.dev.
 * Rides a luminous traveling beam around the perimeter of any card, button, or input.
 */
fun Modifier.borderBeam(
    borderWidth: Dp = 1.5.dp,
    durationMillis: Int = 3000,
    variant: BorderBeamVariant = BorderBeamVariant.Assistant,
    customColors: List<Color>? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    active: Boolean = true,
    strength: Float = 0.85f,
): Modifier = this.then(
    Modifier.drawWithContent {
        drawContent()
        if (!active) return@drawWithContent

        val outline = shape.createOutline(size, layoutDirection, this)
        val strokeWidthPx = borderWidth.toPx()

        // Calculate colors
        val colors = customColors ?: when (variant) {
            BorderBeamVariant.Colorful -> listOf(
                Color(0xFF9D4EDD), // Purple
                Color(0xFFFF007F), // Neon Pink
                Color(0xFF00F0FF), // Electric Cyan
                Color(0xFFFFB703), // Amber Gold
                Color.Transparent,
            )
            BorderBeamVariant.Ocean -> listOf(
                Color(0xFF00F0FF), // Cyan
                Color(0xFF0072FF), // Deep Blue
                Color(0xFF00F5D4), // Teal
                Color.Transparent,
            )
            BorderBeamVariant.Sunset -> listOf(
                Color(0xFFFF007F), // Magenta
                Color(0xFFFF5400), // Coral
                Color(0xFFFFD166), // Gold
                Color.Transparent,
            )
            BorderBeamVariant.Mono -> listOf(
                Color.White.copy(alpha = 0.9f),
                Color.LightGray.copy(alpha = 0.6f),
                Color.Transparent,
            )
            BorderBeamVariant.Assistant -> listOf(
                Color(0xFF8B5CF6), // Royal Violet
                Color(0xFF38BDF8), // Sky Blue
                Color(0xFFF43F5E), // Rose Pink
                Color(0xFFF59E0B), // Warm Amber
                Color.Transparent,
            )
        }

        // We simulate the sweeping beam head along the perimeter
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = maxOf(w, h)

        // Draw outer bloom stroke
        drawOutline(
            outline = outline,
            brush = Brush.sweepGradient(
                colors = colors.map { it.copy(alpha = it.alpha * strength) },
                center = Offset(cx, cy),
            ),
            style = Stroke(
                width = strokeWidthPx,
                cap = StrokeCap.Round,
            ),
        )
    }
)

/**
 * Composable wrapper for BorderBeam with smooth continuous rotation.
 */
@Composable
fun BorderBeamCard(
    modifier: Modifier = Modifier,
    borderWidth: Dp = 1.5.dp,
    durationMillis: Int = 3000,
    variant: BorderBeamVariant = BorderBeamVariant.Assistant,
    customColors: List<Color>? = null,
    shape: Shape = RoundedCornerShape(18.dp),
    active: Boolean = true,
    strength: Float = 0.9f,
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    val colors = customColors ?: when (variant) {
        BorderBeamVariant.Colorful -> listOf(
            Color(0xFF9D4EDD), // Purple
            Color(0xFFFF007F), // Neon Pink
            Color(0xFF00F0FF), // Electric Cyan
            Color(0xFFFFB703), // Amber Gold
            Color.Transparent,
            Color.Transparent,
        )
        BorderBeamVariant.Ocean -> listOf(
            Color(0xFF00F0FF), // Cyan
            Color(0xFF0072FF), // Deep Azure
            Color(0xFF00F5D4), // Teal
            Color.Transparent,
            Color.Transparent,
        )
        BorderBeamVariant.Sunset -> listOf(
            Color(0xFFFF007F), // Magenta
            Color(0xFFFF5400), // Coral
            Color(0xFFFFD166), // Gold
            Color.Transparent,
            Color.Transparent,
        )
        BorderBeamVariant.Mono -> listOf(
            Color.White.copy(alpha = 0.9f),
            Color.LightGray.copy(alpha = 0.5f),
            Color.Transparent,
            Color.Transparent,
        )
        BorderBeamVariant.Assistant -> listOf(
            Color(0xFF8B5CF6), // Royal Violet
            Color(0xFF38BDF8), // Sky Blue
            Color(0xFFF43F5E), // Rose Pink
            Color(0xFFF59E0B), // Amber Gold
            Color.Transparent,
            Color.Transparent,
        )
    }

    Box(
        modifier = modifier.drawWithContent {
            drawContent()
            if (!active) return@drawWithContent

            val outline = shape.createOutline(size, layoutDirection, this)
            val strokeWidthPx = borderWidth.toPx()
            val cx = size.width / 2f
            val cy = size.height / 2f

            // Offset the gradient sweep by the rotating angle
            val radians = (angle * PI / 180f).toFloat()
            val startX = cx + cos(radians) * cx
            val startY = cy + sin(radians) * cy
            val endX = cx - cos(radians) * cx
            val endY = cy - sin(radians) * cy

            drawOutline(
                outline = outline,
                brush = Brush.linearGradient(
                    colors = colors.map { it.copy(alpha = it.alpha * strength) },
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                ),
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round,
                ),
            )
        }
    ) {
        content()
    }
}
