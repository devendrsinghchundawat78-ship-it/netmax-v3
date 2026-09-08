package com.nuvio.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.shadow.Shadow
import com.nuvio.app.features.settings.LiquidGlassSettings
import com.nuvio.app.features.settings.LiquidGlassSettingsRepository
import androidx.compose.material3.MaterialTheme

/**
 * True "liquid glass" surface backed by the Backdrop library
 * (https://github.com/Kyant0/AndroidLiquidGlass).
 *
 * Renders the layer captured by [backdrop] with a vibrancy color filter,
 * gaussian blur and an AGSL lens-refraction effect (the signature iOS-style
 * edge distortion, incl. chromatic dispersion). The app's existing
 * Liquid Glass appearance settings drive the parameters so the popup stays
 * in sync with what the user configured in Settings > Appearance.
 *
 * Safety / graceful degradation:
 * - Android API < 31 (no RenderEffect): falls back to the opaque
 *   [fallbackColor] surface, i.e. exactly the pre-glass visuals.
 * - Android API 31-32: blur + tint still render; the lens effect is a no-op
 *   inside the library (no RuntimeShader), so nothing crashes.
 * - If liquid glass is disabled in settings, the fallback surface is used.
 * - If [backdrop] is null (no layer attached in this window), the fallback
 *   surface is used.
 */
@Composable
fun Modifier.backdropLiquidGlass(
    backdrop: Backdrop?,
    shape: Shape,
    fallbackColor: Color,
    contentDimAlpha: Float = 0.32f,
    tintAlphaOverride: Float? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    LiquidGlassSettingsRepository.ensureLoaded()
    val settings by LiquidGlassSettingsRepository.uiState.collectAsStateWithLifecycle()
    val activeBackdrop = backdrop.takeIf { settings.enabled && isRenderEffectSupported() }
    if (activeBackdrop == null) {
        return this.background(fallbackColor, shape)
    }

    val isLight = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val surfaceAlpha = if (tintAlphaOverride != null) {
        tintAlphaOverride.coerceIn(0f, 1f)
    } else if (settings.enhancedLiquidGlass) {
        (settings.surfaceOpacity * 0.75f).coerceIn(0f, 0.5f)
    } else {
        settings.surfaceOpacity
    }

    return drawBackdrop(
        backdrop = activeBackdrop,
        shape = { shape },
        effects = { applyBackdropGlassEffects(settings) },
        shadow = { Shadow.Default },
        layerBlock = layerBlock,
        onDrawSurface = {
            if (isLight) {
                // Bright frosted panel so dark menu text stays readable in light themes.
                drawRect(Color.White.copy(alpha = (0.42f + surfaceAlpha * 0.5f).coerceIn(0f, 0.85f)))
            } else {
                // Dim the sampled content so the panel matches the dark scrim around it.
                drawRect(Color.Black.copy(alpha = contentDimAlpha))
                drawRect(settings.surfaceTint.copy(alpha = surfaceAlpha))
            }
        },
    )
}

private fun BackdropEffectScope.applyBackdropGlassEffects(settings: LiquidGlassSettings) {
    vibrancy()
    val blurPx = settings.blurRadius.dp.toPx()
    if (blurPx > 0f) {
        blur(blurPx)
    }
    val refractionHeightPx = 18.dp.toPx() * settings.refractionHeight.coerceIn(0f, 1f)
    val refractionAmountPx = 32.dp.toPx() * settings.refractionAmount.coerceIn(0f, 1f)
    if (refractionHeightPx > 0f && refractionAmountPx > 0f) {
        lens(
            refractionHeight = refractionHeightPx,
            refractionAmount = refractionAmountPx,
            depthEffect = settings.depthEffect > 0.5f,
            chromaticAberration = settings.chromaticAberration > 0.05f,
        )
    }
}
