package com.nuvio.app.features.netmax.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Thinking Orbs for AI Assistant interfaces from libraries.dev (https://libraries.dev/orbs.html).
 * Implements the official 3D projection, depth shading, Fibonacci distribution, and z-sorting.
 */
enum class OrbState {
    Working,    // Orbits: concentric particle orbits
    Searching,  // Globe: rotating 3D globe with scanning waves
    Connecting, // Web: dynamic constellation of connected nodes
    Shaping,    // Morph: fluid shape-shifting geometry
    Breathing,  // Ring: pulsing breathing halo
}

private data class OrbDot(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val white: Float,
    val a: Float = 1f,
)

private data class OrbLine(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val white: Float,
    val a: Float = 1f,
    val w: Float = 1f,
)

private data class OrbFrame(
    val dots: List<OrbDot>,
    val lines: List<OrbLine>,
)

@Composable
fun ThinkingOrb(
    modifier: Modifier = Modifier,
    state: OrbState = OrbState.Searching,
    size: Dp = 64.dp,
    speed: Float = 1f,
    tint: Color? = null,
    isDark: Boolean = true,
) {
    val infiniteTransition = rememberInfiniteTransition()
    // Drive continuous animation clock in seconds
    val clockSeconds by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    val sizePx = size.value
    val time = clockSeconds * speed

    Canvas(modifier = modifier.size(size)) {
        val width = this.size.width
        val height = this.size.height
        val scale = width / sizePx

        val frame = when (state) {
            OrbState.Working -> computeOrbitsFrame(sizePx, time)
            OrbState.Searching -> computeGlobeFrame(sizePx, time)
            OrbState.Connecting -> computeWebFrame(sizePx, time)
            OrbState.Shaping -> computeMorphFrame(sizePx, time)
            OrbState.Breathing -> computeRingFrame(sizePx, time)
        }

        // 1. Draw lines first so nodes sit on top of their edges
        for (line in frame.lines) {
            val color = resolveInkColor(line.white, line.a, isDark, tint)
            drawLine(
                color = color,
                start = Offset(line.x1 * scale, line.y1 * scale),
                end = Offset(line.x2 * scale, line.y2 * scale),
                strokeWidth = line.w * scale,
                cap = StrokeCap.Round,
            )
        }

        // 2. Draw z-sorted dots
        for (dot in frame.dots) {
            val color = resolveInkColor(dot.white, dot.a, isDark, tint)
            drawCircle(
                color = color,
                radius = dot.r * scale,
                center = Offset(dot.x * scale, dot.y * scale),
            )
        }
    }
}

// --- Mathematical Engine ported from libraries.dev/packages/thinking-orbs ---

private fun resolveInkColor(w: Float, alpha: Float, isDark: Boolean, tint: Color?): Color {
    val clampedW = w.coerceIn(0f, 1f)
    val clampedA = alpha.coerceIn(0f, 1f)
    val factor = if (isDark) 1f - clampedW else clampedW
    return if (tint == null) {
        Color(factor, factor, factor, clampedA)
    } else {
        val specular = (factor - 0.65f).coerceAtLeast(0f) / 0.35f
        Color(
            red = (tint.red + (1f - tint.red) * specular * 0.65f).coerceIn(0f, 1f),
            green = (tint.green + (1f - tint.green) * specular * 0.65f).coerceIn(0f, 1f),
            blue = (tint.blue + (1f - tint.blue) * specular * 0.65f).coerceIn(0f, 1f),
            alpha = (clampedA * (0.4f + 0.6f * factor)).coerceIn(0f, 1f),
        )
    }
}

private fun radiusScale(size: Float, pow: Float = 0.6f): Float =
    (size / 300f).toDouble().let { kotlin.math.pow(it, pow.toDouble()).toFloat() }

private fun hashD(a: Float, b: Float): Float {
    val h = sin(a * 12.9898f + b * 78.233f) * 43758.5453f
    return h - floor(h)
}

private fun vnoise(x: Float, y: Float): Float {
    val xi = floor(x)
    val yi = floor(y)
    var fx = x - xi
    var fy = y - yi
    fx = fx * fx * (3f - 2f * fx)
    fy = fy * fy * (3f - 2f * fy)
    val a = hashD(xi, yi)
    val b = hashD(xi + 1f, yi)
    val c = hashD(xi, yi + 1f)
    val d = hashD(xi + 1f, yi + 1f)
    return a + (b - a) * fx + (c - a) * fy + (a - b - c + d) * fx * fy
}

private fun fibDir(i: Int, n: Int): Triple<Float, Float, Float> {
    val golden = PI.toFloat() * (3f - sqrt(5f))
    val y = 1f - (2f * (i + 0.5f)) / n
    val rad = sqrt(max(0f, 1f - y * y))
    val a = i * golden
    return Triple(rad * cos(a), y, rad * sin(a))
}

private fun makeProj(
    yaw: Float,
    tilt: Float,
    cx: Float,
    cy: Float,
    scale: Float,
): (Float, Float, Float) -> Triple<Float, Float, Float> {
    val st = sin(tilt)
    val ct = cos(tilt)
    val sy = sin(yaw)
    val cyw = cos(yaw)
    return { x, y, z ->
        val x1 = x * cyw + z * sy
        val z1 = -x * sy + z * cyw
        val y1 = y * ct - z1 * st
        val z2 = y * st + z1 * ct
        Triple(cx + x1 * scale, cy - y1 * scale, z2)
    }
}

private fun finalizeFrame(dots: List<OrbDot>, lines: List<OrbLine>, rMin: Float = 0.35f): OrbFrame {
    val visible = dots.filter { it.a >= 0.02f }.map {
        if (it.r < rMin) it.copy(r = rMin) else it
    }.sortedBy { it.z }
    return OrbFrame(dots = visible, lines = lines.filter { it.a >= 0.02f })
}

// 1. Orbits (Working State)
private fun computeOrbitsFrame(size: Float, t: Float): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    val R = (size / 2f) * 0.82f
    val pt = makeProj(t * 0.25f, 0.35f, cx, cy, 1f)
    val rs = radiusScale(size, 0.6f)

    val dots = mutableListOf<OrbDot>()
    val orbitN = 9
    val ghostN = 32
    val particles = 3

    for (orb in 0 until orbitN) {
        val h1 = hashD(orb.toFloat(), 1.7f)
        val h2 = hashD(orb.toFloat(), 5.2f)
        val h3 = hashD(orb.toFloat(), 8.9f)
        val ro = R * (0.45f + 0.52f * h1)
        val th = h1 * 2f * PI.toFloat()
        val phi = acos((2f * h2 - 1f).coerceIn(-1f, 1f))

        val nx = sin(phi) * cos(th)
        val ny = cos(phi)
        val nz = sin(phi) * sin(th)
        var ux = -ny
        var uy = nx
        val uz = 0f
        val ul = max(1e-6f, sqrt(ux * ux + uy * uy))
        ux /= ul
        uy /= ul
        val vx = ny * uz - nz * uy
        val vy = nz * ux - nx * uz
        val vz = nx * uy - ny * ux
        val orbitSpeed = (0.35f + 0.65f * h3) * (if (h3 > 0.5f) 1f else -1f)

        // Ghost trail orbit path
        for (k in 0 until ghostN step 2) {
            val a = (k.toFloat() / ghostN) * 2f * PI.toFloat()
            val (px, py, z) = pt(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro,
            )
            val depth = (z / ro + 1f) / 2f
            dots.add(
                OrbDot(
                    x = px,
                    y = py,
                    z = z,
                    r = 0.9f * rs,
                    white = 0.72f,
                    a = 0.45f * (0.35f + 0.65f * depth),
                )
            )
        }

        // Active particles on orbit
        for (m in 0 until particles) {
            val a = t * orbitSpeed + (m.toFloat() / particles) * 2f * PI.toFloat() + h2 * 6f
            val (px, py, z) = pt(
                (ux * cos(a) + vx * sin(a)) * ro,
                (uy * cos(a) + vy * sin(a)) * ro,
                (uz * cos(a) + vz * sin(a)) * ro,
            )
            val depth = (z / ro + 1f) / 2f
            dots.add(
                OrbDot(
                    x = px,
                    y = py,
                    z = z,
                    r = (1.3f + 1.8f * depth) * rs,
                    white = 0.25f - 0.2f * depth,
                    a = 0.8f + 0.2f * depth,
                )
            )
        }
    }
    return finalizeFrame(dots, emptyList())
}

// 2. Globe (Searching State)
private fun computeGlobeFrame(size: Float, t: Float): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    val R = (size / 2f) * 0.80f
    val pt = makeProj(t * 0.35f, 0.45f, cx, cy, R)
    val rs = radiusScale(size, 0.6f)

    val dots = mutableListOf<OrbDot>()
    val lines = mutableListOf<OrbLine>()
    val nPoints = 50

    // Fibonacci sphere with scan waves
    for (i in 0 until nPoints) {
        val (dx, dy, dz) = fibDir(i, nPoints)
        val (px, py, z) = pt(dx, dy, dz)
        val depth = (z + 1f) / 2f
        val scan = sin(dy * 5f - t * 4f) * 0.5f + 0.5f
        val pulse = 1f + 0.4f * scan

        dots.add(
            OrbDot(
                x = px,
                y = py,
                z = z,
                r = (1.2f + 1.4f * depth) * pulse * rs,
                white = 0.45f - 0.4f * (depth * scan),
                a = 0.4f + 0.6f * depth,
            )
        )
    }

    // Latitude rings
    val ringCount = 5
    for (r in 1..ringCount) {
        val lat = ((r.toFloat() / (ringCount + 1)) - 0.5f) * PI.toFloat() * 0.75f
        val y = sin(lat)
        val rad = cos(lat)
        val segs = 24
        for (s in 0 until segs) {
            val a1 = (s.toFloat() / segs) * 2f * PI.toFloat()
            val a2 = ((s + 1).toFloat() / segs) * 2f * PI.toFloat()
            val (x1, y1, z1) = pt(rad * cos(a1), y, rad * sin(a1))
            val (x2, y2, z2) = pt(rad * cos(a2), y, rad * sin(a2))
            val depth = ((z1 + z2) / 2f + 1f) / 2f
            if (depth > 0.45f) {
                lines.add(
                    OrbLine(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        white = 0.55f,
                        a = 0.3f * depth,
                        w = 0.75f * rs,
                    )
                )
            }
        }
    }

    return finalizeFrame(dots, lines)
}

// 3. Web (Connecting State)
private fun computeWebFrame(size: Float, t: Float): OrbFrame {
    val cx = size / 2f
    val cy = size / 2f
    val R = (size / 2f) * 0.80f
    val pt = makeProj(t * 0.2f, 0.32f, cx, cy, R)
    val rs = radiusScale(size, 0.6f)

    val nodeN = 24
    val thr = 0.75f
    val nodes = mutableListOf<Triple<Float, Float, Float>>()

    for (i in 0 until nodeN) {
        val d = fibDir(i, nodeN)
        val x = d.first + 0.25f * (vnoise(i * 0.31f + 9f, t * 0.24f) - 0.5f) * 2f
        val y = d.second + 0.25f * (vnoise(i * 0.53f + 27f, t * 0.21f) - 0.5f) * 2f
        val z = d.third + 0.25f * (vnoise(i * 0.77f + 55f, t * 0.27f) - 0.5f) * 2f
        val l = max(1e-6f, sqrt(x * x + y * y + z * z))
        nodes.add(Triple(x / l, y / l, z / l))
    }

    val lines = mutableListOf<OrbLine>()
    val dots = mutableListOf<OrbDot>()

    // Connections between nearby nodes
    for (i in 0 until nodeN) {
        for (j in i + 1 until nodeN) {
            val dx = nodes[i].first - nodes[j].first
            val dy = nodes[i].second - nodes[j].second
            val dz = nodes[i].third - nodes[j].third
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist >= thr) continue

            val (x1, y1, z1) = pt(nodes[i].first, nodes[i].second, nodes[i].third)
            val (x2, y2, z2) = pt(nodes[j].first, nodes[j].second, nodes[j].third)
            val depth = ((z1 + z2) / 2f + 1f) / 2f
            lines.add(
                OrbLine(
                    x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                    white = 0.42f,
                    a = (1f - dist / thr) * (0.35f + 0.65f * depth),
                    w = max(0.8f, 0.9f * rs),
                )
            )
        }
    }

    // Nodes
    for (i in 0 until nodeN) {
        val (px, py, z) = pt(nodes[i].first, nodes[i].second, nodes[i].third)
        val depth = (z + 1f) / 2f
        val pulse = 1f + 0.25f * sin(t * 1.6f + i * 2.7f)
        dots.add(
            OrbDot(
                x = px,
                y = py,
                z = z,
                r = (1.4f + 1.8f * depth) * pulse * rs,
                white = 0.55f - 0.45f * depth,
                a = 0.6f + 0.4f * depth,
            )
        )
    }

    // Moving signal packet along a pair
    val pairIndexA = (floor(t * 0.8f).toInt() % nodeN).coerceIn(0, nodeN - 1)
    val pairIndexB = ((pairIndexA + 5) % nodeN)
    val fraction = (t * 0.8f) - floor(t * 0.8f)
    val sx = nodes[pairIndexA].first + (nodes[pairIndexB].first - nodes[pairIndexA].first) * fraction
    val sy = nodes[pairIndexA].second + (nodes[pairIndexB].second - nodes[pairIndexA].second) * fraction
    val sz = nodes[pairIndexA].third + (nodes[pairIndexB].third - nodes[pairIndexA].third) * fraction
    val sl = max(1e-6f, sqrt(sx * sx + sy * sy + sz * sz))
    val (spx, spy, spz) = pt(sx / sl, sy / sl, sz / sl)
    dots.add(
        OrbDot(
            x = spx,
            y = spy,
            z = spz + 0.1f,
            r = 3.2f * rs,
            white = 0.05f,
            a = 0.95f,
        )
    )

    return finalizeFrame(dots, lines)
}

// 4. Morph (Shaping State)
private fun computeMorphFrame(size: Float, t: Float): OrbFrame {
    val dots = mutableListOf<OrbDot>()
    val c2 = size / 2f
    val nDots = 42
    val cycle = (t * 0.6f) % 3f
    val shapeStep = floor(cycle).toInt()
    val blend = (cycle - shapeStep) * (cycle - shapeStep) * (3f - 2f * (cycle - shapeStep))
    val radiusBase = size * 0.38f

    for (k in 0 until nDots) {
        val f = k.toFloat() / nDots
        val angle = f * 2f * PI.toFloat() - PI.toFloat() / 2f

        // Circle coordinates
        val cx = cos(angle)
        val cy = sin(angle)

        // Square coordinates
        val sqCos = cos(angle)
        val sqSin = sin(angle)
        val maxCoord = max(kotlin.math.abs(sqCos), kotlin.math.abs(sqSin))
        val sx = if (maxCoord > 1e-4f) sqCos / maxCoord * 0.85f else 0f
        val sy = if (maxCoord > 1e-4f) sqSin / maxCoord * 0.85f else 0f

        // Triangle coordinates
        val triAngle = angle + PI.toFloat() / 2f
        val rTri = (1f / (cos(triAngle % (2f * PI.toFloat() / 3f) - PI.toFloat() / 3f))).coerceIn(0.6f, 1.4f) * 0.75f
        val tx = cos(angle) * rTri
        val ty = sin(angle) * rTri

        val (x, y) = when (shapeStep) {
            0 -> (cx + (tx - cx) * blend) to (cy + (ty - cy) * blend)
            1 -> (tx + (sx - tx) * blend) to (ty + (sy - ty) * blend)
            else -> (sx + (cx - sx) * blend) to (sy + (cy - sy) * blend)
        }

        val pulse = 1f + 0.04f * sin(t * 2.5f + f * 4f)
        dots.add(
            OrbDot(
                x = c2 + x * radiusBase * pulse,
                y = c2 + y * radiusBase * pulse,
                z = 0f,
                r = 2.2f * radiusScale(size),
                white = 0.2f,
                a = 0.85f,
            )
        )
    }

    return finalizeFrame(dots, emptyList())
}

// 5. Ring (Breathing State)
private fun computeRingFrame(size: Float, t: Float): OrbFrame {
    val dots = mutableListOf<OrbDot>()
    val cx = size / 2f
    val cy = size / 2f
    val baseR = size * 0.36f
    val nDots = 48
    val rs = radiusScale(size)

    val breathe = 1f + 0.12f * sin(t * 1.8f)
    for (i in 0 until nDots) {
        val a = (i.toFloat() / nDots) * 2f * PI.toFloat()
        val wobble = 1f + 0.05f * sin(a * 4f + t * 2.5f)
        val r = baseR * breathe * wobble
        val x = cx + cos(a) * r
        val y = cy + sin(a) * r
        dots.add(
            OrbDot(
                x = x,
                y = y,
                z = sin(a * 2f + t),
                r = (1.5f + 0.8f * sin(a * 3f + t)) * rs,
                white = 0.25f,
                a = 0.7f + 0.3f * sin(a * 2f + t),
            )
        )
    }

    return finalizeFrame(dots, emptyList())
}
