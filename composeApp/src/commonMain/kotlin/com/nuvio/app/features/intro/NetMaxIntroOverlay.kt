package com.nuvio.app.features.intro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.netmax_logo
import org.jetbrains.compose.resources.imageResource

/**
 * Plays once per cold start: the NetMax logo emerges from darkness, a light
 * sweep and dust particles cross it, the wordmark snaps to full colour with a
 * short impact, then everything fades into the app beneath.
 *
 * Rendering is a single Canvas whose per-frame values derive from one progress
 * state: no per-frame allocations, no recomposition — only draw + layer
 * invalidation, so the animation stays smooth even on low-end devices.
 */
@Composable
internal fun NetMaxIntroOverlay(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        IntroSoundController.stop()
        onFinished()
    }

    LaunchedEffect(Unit) {
        NetMaxIntroSession.playedThisProcess = true
        // The sound is normally preloaded from MainActivity; wait briefly only
        // when decoding is still in flight so audio and visuals stay in sync.
        IntroSoundController.awaitReady(700L)
        runCatching { IntroSoundController.play() }
        val durationNanos = INTRO_DURATION_MS * 1_000_000f
        var startNanos = -1L
        while (isActive && !finished) {
            val frameNanos = withFrameNanos { it }
            if (startNanos == -1L) startNanos = frameNanos
            val elapsed = (frameNanos - startNanos).coerceAtLeast(0L).toFloat()
            progress = (elapsed / durationNanos).coerceIn(0f, 1f)
            if (elapsed >= durationNanos) break
        }
        finish()
    }

    val logo = imageResource(Res.drawable.netmax_logo)
    val particles = remember { IntroParticles() }
    val layerPaint = remember { Paint() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { finish() }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val seconds = progress * INTRO_DURATION_SECONDS
                    // Final beat: smooth fade into the Home screen.
                    val fade = smoothStep(phaseFraction(seconds, PHASE_SHARP_END, INTRO_DURATION_SECONDS))
                    alpha = 1f - fade
                    // Pseudo-3D camera: tilted while the logo emerges, tiny punch on impact.
                    rotationX = 22f * (1f - easeOutCubic(phaseFraction(seconds, PHASE_EMERGE_START, PHASE_EMERGE_END)))
                    val punch = 1f + 0.018f * sin(PI.toFloat() * phaseFraction(seconds, IMPACT_START, IMPACT_START + 0.3f))
                    val drift = 1f + 0.012f * phaseFraction(seconds, IMPACT_START + 0.3f, INTRO_DURATION_SECONDS)
                    scaleX = punch * drift
                    scaleY = punch * drift
                    cameraDistance = 24.dp.toPx()
                },
        ) {
            drawIntroFrame(
                progress = progress,
                logo = logo,
                particles = particles,
                layerPaint = layerPaint,
            )
        }
    }
}

/** Process-wide guard: the intro plays on cold starts, not on every recomposition. */
internal object NetMaxIntroSession {
    @Volatile
    var playedThisProcess: Boolean = false
}

private const val INTRO_DURATION_MS = 4_000f
private const val INTRO_DURATION_SECONDS = 4.0f

private const val PHASE_EMERGE_START = 0.5f
private const val PHASE_EMERGE_END = 1.5f
private const val PHASE_SWEEP_END = 2.5f
private const val IMPACT_START = 2.5f
private const val PHASE_SHARP_END = 3.2f

private val ParticleWhite = Color.White
private val ParticleRed = Color(0xFFFF5A5A)
private val GlowRed = Color(0xFF6E0E0E)

private fun phaseFraction(seconds: Float, start: Float, end: Float): Float =
    if (end <= start) {
        if (seconds >= end) 1f else 0f
    } else {
        ((seconds - start) / (end - start)).coerceIn(0f, 1f)
    }

private fun easeOutCubic(x: Float): Float {
    val inv = 1f - x.coerceIn(0f, 1f)
    return 1f - inv * inv * inv
}

private fun smoothStep(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun DrawScope.drawIntroFrame(
    progress: Float,
    logo: ImageBitmap?,
    particles: IntroParticles,
    layerPaint: Paint,
) {
    val seconds = progress * INTRO_DURATION_SECONDS
    val w = size.width
    val h = size.height

    // Pure black base.
    drawRect(Color.Black, size = size)

    // Logo destination: ~72% of the width, capped so tablets stay elegant.
    val logoAspect = 1066f / 330f
    val targetW = min(w * 0.72f, 620.dp.toPx())
    val targetH = targetW / logoAspect
    val left = (w - targetW) / 2f
    val top = (h - targetH) / 2f - h * 0.02f
    val dst = Rect(left, top, left + targetW, top + targetH)

    val emerge = easeOutCubic(phaseFraction(seconds, PHASE_EMERGE_START, PHASE_EMERGE_END))
    val sweep = phaseFraction(seconds, PHASE_EMERGE_END, PHASE_SWEEP_END)
    val sharpFast = phaseFraction(seconds, IMPACT_START, IMPACT_START + 0.22f)
    val flash = if (seconds > IMPACT_START) exp(-(seconds - IMPACT_START) / 0.10f) else 0f

    // Ambient red glow breathing behind the logo.
    val breathe = 0.04f * sin(2f * PI.toFloat() * seconds * 0.8f)
    val glowAlpha = (0.30f * emerge + 0.08f * sweep + 0.30f * flash + breathe).coerceIn(0f, 0.6f)
    if (glowAlpha > 0.005f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(GlowRed.copy(alpha = glowAlpha), Color.Transparent),
                center = dst.center,
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = dst.center,
        )
    }

    // Dust particles, denser while the light sweeps across.
    particles.draw(
        scope = this,
        seconds = seconds,
        visible = max2(emerge, sweep),
        flash = flash,
    )

    if (logo != null && emerge > 0f) {
        val logoAlpha = min(1f, 0.80f * emerge + 0.15f * sweep + 0.05f * sharpFast)
        val saturation = (0.15f + 0.35f * emerge + 0.30f * sweep + 0.20f * sharpFast).coerceIn(0.1f, 1f)
        val punch = 1f + 0.045f * sin(PI.toFloat() * sharpFast)
        val rise = (1f - emerge) * 26.dp.toPx()
        val dstOffset = IntOffset(dst.left.roundToInt(), dst.top.roundToInt())
        val dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt())

        // Isolated layer so the sweep (SrcAtop) only lights the logo pixels.
        drawContext.canvas.saveLayer(dst.inflate(24.dp.toPx()), layerPaint)
        withTransform({
            scale(punch, punch, pivot = dst.center)
            translate(0f, rise)
        }) {
            drawImage(
                image = logo,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(logo.width, logo.height),
                dstOffset = dstOffset,
                dstSize = dstSize,
                alpha = logoAlpha,
                colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }),
            )
            if (sweep > 0f && sweep < 1f) {
                val centerX = (-0.35f + 1.7f * sweep) * w
                val band = 0.30f * w
                val sheen = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.32f), Color.Transparent),
                    start = Offset(centerX - band, dst.bottom + 0.25f * dst.height),
                    end = Offset(centerX + band, dst.top - 0.25f * dst.height),
                )
                drawRect(
                    brush = sheen,
                    topLeft = Offset(-w, -h),
                    size = Size(3f * w, 3f * h),
                    blendMode = BlendMode.SrcAtop,
                )
            }
            if (flash > 0.02f) {
                drawImage(
                    image = logo,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(logo.width, logo.height),
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    alpha = 0.55f * flash.coerceIn(0f, 1f),
                    blendMode = BlendMode.Plus,
                )
            }
        }
        drawContext.canvas.restore()
    }

    // Cinematic vignette.
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = Offset(w / 2f, h / 2f),
            radius = max2(w, h) * 0.75f,
        ),
        size = size,
    )

    // Impact flash.
    if (flash > 0.02f) {
        drawRect(Color.White.copy(alpha = 0.12f * flash.coerceIn(0f, 1f)), size = size)
    }
}

private fun max2(a: Float, b: Float): Float = if (a > b) a else b

/**
 * Deterministic particle field: positions are generated once with a fixed seed
 * and every frame is a pure function of time, so drawing allocates nothing.
 */
private class IntroParticles(seed: Long = 20260912L) {

    private class Particle(
        val x: Float,
        val y: Float,
        val depth: Float,
        val radiusDp: Float,
        val driftX: Float,
        val driftY: Float,
        val twinkleSpeed: Float,
        val twinklePhase: Float,
        val red: Boolean,
        val spark: Boolean,
        val sparkAngle: Float,
    )

    private val particles: List<Particle> = run {
        val random = kotlin.random.Random(seed)
        buildList {
            repeat(34) {
                add(
                    Particle(
                        x = random.nextFloat(),
                        y = random.nextFloat(),
                        depth = 0.35f + random.nextFloat() * 0.65f,
                        radiusDp = 0.9f + random.nextFloat() * 1.6f,
                        driftX = (random.nextFloat() - 0.5f) * 6f,
                        driftY = 5f + random.nextFloat() * 11f,
                        twinkleSpeed = 0.5f + random.nextFloat() * 1.1f,
                        twinklePhase = random.nextFloat(),
                        red = random.nextFloat() < 0.18f,
                        spark = false,
                        sparkAngle = 0f,
                    ),
                )
            }
            repeat(14) {
                add(
                    Particle(
                        x = 0.18f + random.nextFloat() * 0.64f,
                        y = 0.38f + random.nextFloat() * 0.24f,
                        depth = 0.6f + random.nextFloat() * 0.4f,
                        radiusDp = 1.1f + random.nextFloat() * 1.8f,
                        driftX = (random.nextFloat() - 0.5f) * 4f,
                        driftY = 4f + random.nextFloat() * 8f,
                        twinkleSpeed = 0.7f + random.nextFloat() * 1.2f,
                        twinklePhase = random.nextFloat(),
                        red = random.nextFloat() < 0.4f,
                        spark = true,
                        sparkAngle = random.nextFloat() * 2f * PI.toFloat(),
                    ),
                )
            }
        }
    }

    fun draw(scope: DrawScope, seconds: Float, visible: Float, flash: Float) {
        if (visible <= 0.01f) return
        val w = scope.size.width
        val h = scope.size.height
        val burst = easeOutCubic(phaseFraction(seconds, IMPACT_START, IMPACT_START + 0.45f))
        for (p in particles) {
            var px = p.x * w + p.driftX * seconds * p.depth
            var py = p.y * h - p.driftY * seconds * p.depth
            if (p.spark && burst > 0f) {
                px += cos(p.sparkAngle) * 0.05f * w * burst
                py += sin(p.sparkAngle) * 0.04f * w * burst
            }
            val twinkle = 0.55f + 0.45f * sin(2f * PI.toFloat() * (seconds * p.twinkleSpeed + p.twinklePhase))
            val alpha = (visible * twinkle * p.depth * 0.8f + flash * 0.25f).coerceIn(0f, 0.85f)
            if (alpha <= 0.02f) continue
            val color = (if (p.red) ParticleRed else ParticleWhite).copy(alpha = alpha)
            scope.drawCircle(
                color = color,
                radius = p.radiusDp * p.depth * scope.density,
                center = Offset(px, py),
            )
        }
    }
}
