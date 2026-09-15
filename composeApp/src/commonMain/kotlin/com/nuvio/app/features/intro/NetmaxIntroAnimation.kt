package com.nuvio.app.features.intro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.netmax_logo
import org.jetbrains.compose.resources.painterResource
import kotlin.random.Random

object NetmaxIntroState {
    var hasPlayedIntro: Boolean = true
}

private data class IntroParticle(
    val xRatio: Float,
    val yRatio: Float,
    val radius: Float,
    val alpha: Float,
    val speedY: Float,
    val color: Color,
)

@Composable
fun NetmaxIntroAnimation(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val totalTimeMs = 5800L

    // Overall progress from 0f to 1f
    val progress = remember { Animatable(0f) }
    var isDismissed by remember { mutableStateOf(false) }

    fun finishIntro() {
        if (!isDismissed) {
            isDismissed = true
            NetmaxIntroState.hasPlayedIntro = true
            onFinished()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = totalTimeMs.toInt(),
                easing = LinearEasing,
            ),
        )
        finishIntro()
    }

    val currentProgress = progress.value

    // Timing phases (0f to 1f normalized over 5.8s)
    // 0.0 - 0.25: Atmospheric particle emergence
    // 0.20 - 0.50: Volumetric backlight reveal behind logo
    // 0.40 - 0.75: 3D logo reveal & slow camera push-in
    // 0.60 - 0.85: Specular energy light sweep
    // 0.72 - 0.92: NETMAX typography reveal
    // 0.92 - 1.00: Fade to black
    val backlightAlpha = when {
        currentProgress < 0.18f -> 0f
        currentProgress < 0.45f -> ((currentProgress - 0.18f) / 0.27f).coerceIn(0f, 1f)
        currentProgress < 0.88f -> 1f
        else -> (1f - ((currentProgress - 0.88f) / 0.12f)).coerceIn(0f, 1f)
    }

    val logoAlpha = when {
        currentProgress < 0.28f -> 0f
        currentProgress < 0.55f -> ((currentProgress - 0.28f) / 0.27f).coerceIn(0f, 1f)
        currentProgress < 0.90f -> 1f
        else -> (1f - ((currentProgress - 0.90f) / 0.10f)).coerceIn(0f, 1f)
    }

    // Slow push-in / dolly zoom effect
    val logoScale = 0.88f + (currentProgress * 0.20f)

    // Energy sweep progress (-0.5f to 1.5f) across logo
    val sweepProgress = when {
        currentProgress < 0.52f -> -0.4f
        currentProgress < 0.82f -> ((currentProgress - 0.52f) / 0.30f) * 1.6f - 0.3f
        else -> 1.5f
    }

    // NETMAX typography reveal
    val typoAlpha = when {
        currentProgress < 0.65f -> 0f
        currentProgress < 0.82f -> ((currentProgress - 0.65f) / 0.17f).coerceIn(0f, 1f)
        currentProgress < 0.90f -> 1f
        else -> (1f - ((currentProgress - 0.90f) / 0.10f)).coerceIn(0f, 1f)
    }

    // Floating Stardust Particles
    val particles = remember {
        val rand = Random(42)
        val colors = listOf(
            Color(0xFFFF2E56), // NetMax Red
            Color(0xFFE50914),
            Color(0xFFFF79B0),
            Color(0xFFFFFFFF),
            Color(0xFFB0C4DE),
        )
        List(40) {
            IntroParticle(
                xRatio = rand.nextFloat(),
                yRatio = rand.nextFloat(),
                radius = rand.nextFloat() * 2.5f + 1f,
                alpha = rand.nextFloat() * 0.55f + 0.25f,
                speedY = rand.nextFloat() * 0.08f + 0.03f,
                color = colors[rand.nextInt(colors.size)],
            )
        }
    }

    val particleTime = rememberInfiniteTransition(label = "particle_time")
    val particlePhase by particleTime.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particle_phase",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { finishIntro() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Atmospheric Particles Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            particles.forEach { p ->
                val currentYRatio = (p.yRatio - (particlePhase * p.speedY)) % 1f
                val y = if (currentYRatio < 0f) currentYRatio + 1f else currentYRatio
                val x = p.xRatio * width
                val finalAlpha = (p.alpha * (1f - (currentProgress * 0.3f))).coerceIn(0f, 1f)

                drawCircle(
                    color = p.color.copy(alpha = finalAlpha),
                    radius = p.radius.dp.toPx(),
                    center = Offset(x, y * height),
                )
            }
        }

        // Volumetric Backlight Halo
        if (backlightAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .scale(1f + currentProgress * 0.35f)
                    .alpha(backlightAlpha * 0.85f)
                    .blur(64.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFF1B4E).copy(alpha = 0.70f),
                                Color(0xFF990033).copy(alpha = 0.40f),
                                Color(0xFF330018).copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }

        // Center Content: Logo and Typography
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale)
                .alpha(logoAlpha),
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Exact NetMax TV Logo Icon
                Image(
                    painter = painterResource(Res.drawable.netmax_logo),
                    contentDescription = "NetMax",
                    modifier = Modifier
                        .size(140.dp),
                    contentScale = ContentScale.Fit,
                )

                // Specular Light Sweep Overlay across logo surface
                if (sweepProgress in -0.3f..1.3f) {
                    Canvas(
                        modifier = Modifier
                            .size(140.dp),
                    ) {
                        val sweepCenter = size.width * sweepProgress
                        val beamWidth = size.width * 0.35f

                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.12f),
                                    Color.White.copy(alpha = 0.65f),
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                ),
                                start = Offset(sweepCenter - beamWidth, 0f),
                                end = Offset(sweepCenter + beamWidth, size.height),
                            ),
                            blendMode = BlendMode.Screen,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cinematic "NETMAX" Wordmark Typography
            Text(
                text = "NETMAX",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 10.sp,
                ),
                color = Color.White.copy(alpha = typoAlpha),
                modifier = Modifier
                    .alpha(typoAlpha)
                    .padding(start = 10.sp.value.dp), // compensate for letter spacing
            )
        }

        // Skip Button in Top Right Corner
        Surface(
            onClick = { finishIntro() },
            shape = RoundedCornerShape(18.dp),
            color = Color.White.copy(alpha = 0.14f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 20.dp)
                .zIndex(10f),
        ) {
            Text(
                text = "SKIP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}
