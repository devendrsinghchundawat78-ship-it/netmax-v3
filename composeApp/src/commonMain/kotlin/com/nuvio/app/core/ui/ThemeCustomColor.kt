package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * The user's own theme colour.
 *
 * The built-in [AppTheme] entries stay the source of truth for every shipped palette; this object only
 * carries the single colour the user mixed in Settings -> Appearance, plus the maths that turns that one
 * colour into a full [ThemeColorPalette] (accent, gradient, focus ring, tinted backgrounds) so a custom
 * theme looks as deliberate as a preset one.
 */
object ThemeCustomColor {
    /** Used until the user picks a colour, and as the fallback for a corrupt stored value. */
    const val DEFAULT_ACCENT_HEX: String = "#6E7BFF"

    /**
     * Luminance from which a picked colour is bright enough to be the app *background* (a light theme).
     * Below it the app keeps its dark surfaces and the picked colour is used as the accent, exactly like
     * the Crimson / Ocean / Violet presets.
     */
    const val LIGHT_ACCENT_MIN_LUMINANCE: Float = 0.72f

    /* The app's own text colours (see Theme.kt): the derived page has to work with one of them. */
    private const val MIN_PAGE_CONTRAST = 5.0f
    private const val MIN_LABEL_CONTRAST = 4.5f
    private const val MAX_DARK_TINT = 0.045f
    private const val DARK_TINT_STEP = 0.003f
    private const val MAX_PAGE_CORRECTION_STEPS = 24

    var accentHex: String = DEFAULT_ACCENT_HEX
        internal set

    /** The stored colour, already validated: a malformed value falls back to [DEFAULT_ACCENT_HEX]. */
    val accent: Color
        get() = decodeHex(accentHex) ?: parseHex(DEFAULT_ACCENT_HEX)

    /** True when [color] is bright enough to carry the light app surfaces instead of the dark ones. */
    fun isLightAccent(color: Color): Boolean = luminance(color) > LIGHT_ACCENT_MIN_LUMINANCE

    /**
     * Builds the whole palette from one colour.
     *
     * The picked colour is used untouched as the accent (what the user chose is what they get);
     * everything else is derived from it, following how the preset palettes are shaped.
     */
    fun paletteFor(color: Color): ThemeColorPalette {
        val lightTheme = isLightAccent(color)
        val background = if (lightTheme) dimmedForReadability(color) else tintedForReadability(color)
        val onAccent = bestTextOn(color)
        val secondaryVariant = if (lightTheme) dimmed(color, 0.18f) else tintOver(color, BLACK, 0.68f)
        val brighter = lifted(color, if (lightTheme) 0.10f else 0.22f)
        val deep = dimmed(color, 0.28f)
        return ThemeColorPalette(
            secondary = color,
            secondaryVariant = secondaryVariant,
            accentGradient = listOf(deep, color, brighter, secondaryVariant),
            nativeAccentHex = encodeHex(color),
            onSecondary = onAccent,
            onSecondaryVariant = bestTextOn(secondaryVariant),
            focusRing = brighter,
            focusBackground = tintOver(color, if (lightTheme) WHITE else BLACK, if (lightTheme) 0.88f else 0.22f),
            background = background,
            // Surfaces step away from the page so sheets and cards never merge into it: darker for a
            // light theme, lighter (and only slightly) for the tinted dark one.
            backgroundElevated = if (lightTheme) dimmed(background, 0.05f) else lifted(background, 0.03f),
            backgroundCard = if (lightTheme) dimmed(background, 0.09f) else lifted(background, 0.062f),
        )
    }

    /**
     * The text colour for a surface filled with a custom accent. The app's own two text colours are
     * preferred, so a chip still looks native; a mid-tone accent (a light orange, a pale blue) clears
     * neither, and there pure black or white is used rather than shipping an unreadable label.
     */
    fun bestTextOn(surface: Color): Color {
        if (contrastRatio(LIGHT_TEXT, surface) >= MIN_LABEL_CONTRAST) return LIGHT_TEXT
        if (contrastRatio(DARK_TEXT, surface) >= MIN_LABEL_CONTRAST) return DARK_TEXT
        return if (luminance(surface) >= 0.5f) Color(0f, 0f, 0f) else Color(1f, 1f, 1f)
    }

    /** WCAG contrast ratio between two colours (1 = identical, 21 = black on white). */
    fun contrastRatio(first: Color, second: Color): Float {
        fun channel(shift: Int, color: Color): Float {
            val raw = ((opaqueArgb(color) shr shift) and 0xFF) / 255f
            return if (raw <= 0.03928f) raw / 12.92f else ((raw + 0.055f) / 1.055f).pow(2.4f)
        }
        fun relativeLuminance(color: Color): Float =
            0.2126f * channel(16, color) + 0.7152f * channel(8, color) + 0.0722f * channel(0, color)
        val high = maxOf(relativeLuminance(first), relativeLuminance(second))
        val low = minOf(relativeLuminance(first), relativeLuminance(second))
        return (high + 0.05f) / (low + 0.05f)
    }

    /** Parses `#RGB` and `#RRGGBB` (the leading `#` is optional); `null` when it is not a colour. */
    fun decodeHex(value: String?): Color? {
        val rgb = rgbHexOf(value) ?: return null
        return Color(
            rgb.substring(0, 2).toInt(16) / 255f,
            rgb.substring(2, 4).toInt(16) / 255f,
            rgb.substring(4, 6).toInt(16) / 255f,
        )
    }

    /** Normalises any accepted input to exactly six hex digits (`RRGGBB`), or `null` when it is not a colour. */
    fun rgbHexOf(value: String?): String? {
        val cleaned = value?.trim()?.removePrefix("#")?.uppercase() ?: return null
        // Deliberately not accepting the 8 digit `#AARRGGBB` form: an alpha channel has no meaning for a
        // theme accent and both orders are seen in the wild, so guessing either one is worse than asking
        // for the plain 6 digit value the field already shows.
        return when {
            cleaned.length == 3 && cleaned.all(::isHexDigit) -> cleaned.map { "$it$it" }.joinToString("")
            cleaned.length == 6 && cleaned.all(::isHexDigit) -> cleaned
            else -> null
        }
    }

    /** Same as [decodeHex] with the default colour as the fallback, for places that must render something. */
    fun parseHex(value: String): Color = decodeHex(value) ?: decodeHex(DEFAULT_ACCENT_HEX)!!

    /** `#RRGGBB`, upper case, alpha dropped — the format the theme storage and the native tab bar use. */
    fun encodeHex(color: Color): String = "#%06X".format(opaqueArgb(color) and 0x00FFFFFF)

    /** Relative luminance, which is how the app decides between dark and light content colours. */
    fun luminance(color: Color): Float {
        val argb = opaqueArgb(color)
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        return 0.2126f * red + 0.7152f * green + 0.0722f * blue
    }

    /** Hue of a colour in degrees (0..360) — what the hue bar of the picker shows. */
    fun hueOf(color: Color): Float = hueOf(opaqueArgb(color))

    /** Saturation and brightness, both 0..1, i.e. the position inside the picker panel. */
    fun saturationBrightnessOf(color: Color): Pair<Float, Float> {
        val argb = opaqueArgb(color)
        val max = maxChannel(argb)
        val min = minChannel(argb)
        val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
        return saturation.coerceIn(0f, 1f) to (max / 255f).coerceIn(0f, 1f)
    }

    /** Colour from the picker controls: hue in degrees, saturation and brightness 0..1 (opaque result). */
    fun colorFrom(hueDegrees: Float, saturation: Float, brightness: Float): Color =
        colorFromHsb(hueDegrees.coerceIn(0f, 360f), saturation.coerceIn(0f, 1f), brightness.coerceIn(0f, 1f))

    /**
     * Dark-theme page: the picked colour bleeding into black, with the bleed dialled back until body
     * text clears [MIN_PAGE_CONTRAST]. A mid-tone colour (a plain red or blue) would otherwise wash the
     * page out, which is what keeps every custom theme as readable as the shipped dark presets.
     */
    private fun tintedForReadability(color: Color): Color {
        var weight = MAX_DARK_TINT
        var candidate = tintOver(color, BLACK, weight)
        while (contrastRatio(candidate, LIGHT_TEXT) < MIN_PAGE_CONTRAST && weight > DARK_TINT_STEP) {
            weight -= DARK_TINT_STEP
            candidate = tintOver(color, BLACK, weight)
        }
        return candidate
    }

    /** Light-theme page: the picked colour itself, only pulled towards black if text would wash out. */
    private fun dimmedForReadability(color: Color): Color {
        var value = color
        var step = 0
        while (contrastRatio(value, DARK_TEXT) < MIN_PAGE_CONTRAST && step < MAX_PAGE_CORRECTION_STEPS) {
            value = dimmed(value, 0.04f)
            step++
        }
        return value
    }

    /**
     * Opaque ARGB int of a Compose colour. Written from the channels instead of `toArgb()` so the
     * conversion stays correct for any colour space and rounds the same way in both directions.
     */
    private fun opaqueArgb(color: Color): Int {
        fun channel(value: Float): Int = (value * 255f + 0.5f).toInt().coerceIn(0, 255)
        return 0xFF000000.toInt() or
            (channel(color.red) shl 16) or
            (channel(color.green) shl 8) or
            channel(color.blue)
    }

    /** `weight` = 1 keeps [color], 0 keeps [base]: used to shade backgrounds towards black. */
    private fun tintOver(color: Color, base: Color, weight: Float): Color {
        val tint = opaqueArgb(color)
        val background = opaqueArgb(base)
        val clamped = weight.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val from = (tint shr shift) and 0xFF
            val to = (background shr shift) and 0xFF
            return (to + (from - to) * clamped).toInt().coerceIn(0, 255)
        }
        return Color(channel(16) / 255f, channel(8) / 255f, channel(0) / 255f)
    }

    /** Multiplies every channel down; a light surface always ends up just under the page. */
    private fun dimmed(color: Color, amount: Float): Color {
        val argb = opaqueArgb(color)
        fun channel(shift: Int): Int = (((argb shr shift) and 0xFF) * (1f - amount)).toInt().coerceIn(0, 255)
        return Color(channel(16) / 255f, channel(8) / 255f, channel(0) / 255f)
    }

    /** Pushes every channel towards white; a dark surface always ends up just above the page. */
    private fun lifted(color: Color, amount: Float): Color {
        val argb = opaqueArgb(color)
        fun channel(shift: Int): Int {
            val value = (argb shr shift) and 0xFF
            return (value + (255 - value) * amount).toInt().coerceIn(0, 255)
        }
        return Color(channel(16) / 255f, channel(8) / 255f, channel(0) / 255f)
    }

    private fun isHexDigit(char: Char): Boolean = char in '0'..'9' || char in 'A'..'F' || char in 'a'..'f'

    private fun hueOf(argb: Int): Float {
        val red = ((argb shr 16) and 0xFF) / 255f
        val green = ((argb shr 8) and 0xFF) / 255f
        val blue = (argb and 0xFF) / 255f
        val max = maxOf(red, green, blue)
        val delta = max - minOf(red, green, blue)
        if (delta == 0f) return 0f
        val hue = when (max) {
            red -> ((green - blue) / delta) % 6f
            green -> (blue - red) / delta + 2f
            else -> (red - green) / delta + 4f
        }
        return (hue * 60f + 360f) % 360f
    }

    private fun maxChannel(argb: Int): Int =
        maxOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun minChannel(argb: Int): Int =
        minOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun colorFromHsb(hue: Float, saturation: Float, brightness: Float): Color {
        val chroma = brightness * saturation
        val second = chroma * (1f - abs((hue / 60f) % 2f - 1f))
        val min = brightness - chroma
        val channels = when {
            hue < 60f -> floatArrayOf(chroma, second, 0f)
            hue < 120f -> floatArrayOf(second, chroma, 0f)
            hue < 180f -> floatArrayOf(0f, chroma, second)
            hue < 240f -> floatArrayOf(0f, second, chroma)
            hue < 300f -> floatArrayOf(second, 0f, chroma)
            else -> floatArrayOf(chroma, 0f, second)
        }
        return Color(
            red = (channels[0] + min).coerceIn(0f, 1f),
            green = (channels[1] + min).coerceIn(0f, 1f),
            blue = (channels[2] + min).coerceIn(0f, 1f),
        )
    }

    private val BLACK = Color(0f, 0f, 0f)
    private val WHITE = Color(1f, 1f, 1f)
    /* The app's own body-text colours, exactly as Theme.kt sets onBackground/onSurface. */
    private val LIGHT_TEXT = Color(0xF5 / 255f, 0xF7 / 255f, 0xF8 / 255f)
    private val DARK_TEXT = Color(0x17 / 255f, 0x19 / 255f, 0x1D / 255f)
}
