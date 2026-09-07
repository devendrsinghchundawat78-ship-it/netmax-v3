package com.nuvio.app.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The colour maths behind the "Custom" theme: how a picked colour is read, written back and expanded into
 * a palette. Everything the mixer sheet in Settings -> Appearance does is expressed through these
 * functions, so a change here is a change to what the user sees.
 */
class ThemeCustomColorTest {
    /** The two colours the app itself uses for body text (see Theme.kt). */
    private val lightText = Color(0xF5 / 255f, 0xF7 / 255f, 0xF8 / 255f)
    private val darkText = Color(0x17 / 255f, 0x19 / 255f, 0x1D / 255f)

    @Test
    fun `hex parsing accepts the shapes the field can end up with`() {
        assertEquals("#1E88E5", ThemeCustomColor.encodeHex(ThemeCustomColor.parseHex("1e88e5")))
        assertEquals("#1E88E5", ThemeCustomColor.encodeHex(ThemeCustomColor.parseHex("#1E88E5")))
        assertEquals("#AA0000", ThemeCustomColor.encodeHex(ThemeCustomColor.parseHex("#a00")))
        assertEquals("#112233", ThemeCustomColor.encodeHex(ThemeCustomColor.parseHex("  #112233  ")))
    }

    @Test
    fun `hex parsing refuses everything else`() {
        assertNull(ThemeCustomColor.decodeHex(null))
        assertNull(ThemeCustomColor.decodeHex(""))
        assertNull(ThemeCustomColor.decodeHex("#12"))
        assertNull(ThemeCustomColor.decodeHex("#gggggg"))
        assertNull(ThemeCustomColor.decodeHex("1E88E55"))
        // An alpha channel has no meaning for a theme accent and both orders are seen in the wild,
        // so the eight digit form is refused instead of guessing which half to drop.
        assertNull(ThemeCustomColor.decodeHex("801E88E5"))
    }

    @Test
    fun `a corrupt stored colour falls back to the default accent`() {
        assertEquals(
            ThemeCustomColor.parseHex(ThemeCustomColor.DEFAULT_ACCENT_HEX),
            ThemeCustomColor.parseHex("not-a-colour"),
        )
        assertEquals(ThemeCustomColor.DEFAULT_ACCENT_HEX, ThemeCustomColor.accentHex)
    }

    @Test
    fun `hex round trips every colour`() {
        listOf("#000000", "#FFFFFF", "#6E7BFF", "#E53935", "#0F9D58", "#FF6D00", "#123456")
            .forEach { hex -> assertEquals(hex, ThemeCustomColor.encodeHex(ThemeCustomColor.parseHex(hex)), hex) }
    }

    @Test
    fun `derived colours are always opaque`() {
        listOf("#FFFFFF", "#000000", "#43A047").forEach { hex ->
            val color = ThemeCustomColor.decodeHex(hex)!!
            assertEquals(1f, color.alpha, hex)
            val palette = ThemeCustomColor.paletteFor(color)
            listOf(
                palette.secondary,
                palette.secondaryVariant,
                palette.background,
                palette.backgroundElevated,
                palette.backgroundCard,
                palette.focusRing,
                palette.focusBackground,
            ).forEach { derived -> assertEquals(1f, derived.alpha, "$hex -> $derived") }
        }
    }

    @Test
    fun `hue and the mixer controls round trip`() {
        val green = ThemeCustomColor.parseHex("#43A047")
        assertEquals(123f, ThemeCustomColor.hueOf(green), 1f)
        assertEquals(0f, ThemeCustomColor.hueOf(Color(0f, 0f, 0f)))

        val (saturation, brightness) = ThemeCustomColor.saturationBrightnessOf(green)
        assertEquals(green, ThemeCustomColor.colorFrom(ThemeCustomColor.hueOf(green), saturation, brightness))

        assertEquals(Color(1f, 0f, 0f), ThemeCustomColor.colorFrom(0f, 1f, 1f))
        assertEquals(Color(0f, 1f, 0f), ThemeCustomColor.colorFrom(120f, 1f, 1f))
        assertEquals(Color(0f, 0f, 1f), ThemeCustomColor.colorFrom(240f, 1f, 1f))
    }

    @Test
    fun `the mixer controls clamp what they are given`() {
        assertEquals(Color(1f, 0f, 0f), ThemeCustomColor.colorFrom(-720f, 3f, 5f))
        assertEquals(Color(0f, 0f, 0f), ThemeCustomColor.colorFrom(180f, 0.5f, 0f))
    }

    @Test
    fun `a mid-tone pick gives a dark app tinted with that colour`() {
        val red = ThemeCustomColor.parseHex("#D93025")
        assertFalse(ThemeCustomColor.isLightAccent(red))

        val palette = ThemeCustomColor.paletteFor(red)
        // The page is the picked colour bleeding into black: dark enough for body text, but not grey.
        assertTrue(ThemeCustomColor.luminance(palette.background) < 0.06f, palette.background.toString())
        assertNotSameish(palette.background, Color(0f, 0f, 0f))
        assertTrue(
            ThemeCustomColor.luminance(palette.backgroundElevated) > ThemeCustomColor.luminance(palette.background),
            "sheets must rise above the page",
        )
        assertTrue(
            ThemeCustomColor.luminance(palette.backgroundCard) > ThemeCustomColor.luminance(palette.backgroundElevated),
            "cards must rise above sheets",
        )
    }

    @Test
    fun `a near-white pick is used as the light background itself`() {
        val white = ThemeCustomColor.parseHex("#F5F5F5")
        assertTrue(ThemeCustomColor.isLightAccent(white))

        val palette = ThemeCustomColor.paletteFor(white)
        assertEquals(white, palette.background)
        assertTrue(ThemeCustomColor.luminance(palette.backgroundCard) < ThemeCustomColor.luminance(palette.background))
    }

    @Test
    fun `the picked colour survives the palette untouched`() {
        listOf("#6E7BFF", "#D93025", "#FB8C00", "#F5F5F5", "#171717").forEach { hex ->
            val color = ThemeCustomColor.parseHex(hex)
            val palette = ThemeCustomColor.paletteFor(color)

            assertEquals(hex, palette.nativeAccentHex, hex)
            assertEquals(color, palette.secondary, hex)
            assertTrue(palette.accentGradient.contains(color), hex)
            assertEquals(4, palette.accentGradient.distinct().size, "gradient stops must differ: $hex")
        }
    }

    @Test
    fun `body text stays readable on the derived page`() {
        // Sweep every hue at full saturation: the worst case for contrast, since a vivid colour is what
        // a user is most likely to pick and it is the one that used to wash the page out.
        var hue = 0f
        var seen = 0
        while (hue < 360f) {
            val palette = ThemeCustomColor.paletteFor(ThemeCustomColor.colorFrom(hue, 1f, 0.6f))
            val readable = ThemeCustomColor.contrastRatio(palette.background, lightText) >= 4.5f ||
                ThemeCustomColor.contrastRatio(palette.background, darkText) >= 4.5f
            assertTrue(readable, "hue ${hue.roundToInt()} has no readable body text colour")
            hue += 5f
            seen++
        }
        assertEquals(72, seen)
    }

    @Test
    fun `labels on the accent stay readable`() {
        // 3:1 is the bar the app already holds button labels to: they are set in a bold label style, and
        // the accent itself is never adjusted to buy contrast (what the user picked must stay visible).
        var hue = 0f
        var worst = 21f
        var worstHue = 0f
        while (hue < 360f) {
            val color = ThemeCustomColor.colorFrom(hue, 1f, 0.55f)
            val palette = ThemeCustomColor.paletteFor(color)
            val ratio = ThemeCustomColor.contrastRatio(palette.secondary, palette.onSecondary)
            if (ratio < worst) {
                worst = ratio
                worstHue = hue
            }
            assertTrue(ratio >= 3f, "hue ${hue.roundToInt()} needs a bolder label colour")
            hue += 3f
        }
        assertTrue(worst >= 3f, "worst hue ${worstHue.roundToInt()} measured ${String.format("%.2f", worst)}")
    }

    @Test
    fun `the best text colour is the more readable of the two`() {
        assertEquals(lightText, ThemeCustomColor.bestTextOn(Color(0f, 0f, 0f)))
        assertEquals(darkText, ThemeCustomColor.bestTextOn(Color(1f, 1f, 1f)))
        assertTrue(ThemeCustomColor.contrastRatio(ThemeCustomColor.bestTextOn(Color(0f, 0f, 0f)), Color(0f, 0f, 0f)) > 10f)
        assertTrue(ThemeCustomColor.contrastRatio(ThemeCustomColor.bestTextOn(Color(1f, 1f, 1f)), Color(1f, 1f, 1f)) > 10f)
    }

    @Test
    fun `contrast of identical colours is one and of black on white is twenty one`() {
        assertEquals(1f, ThemeCustomColor.contrastRatio(Color(0.5f, 0.5f, 0.5f), Color(0.5f, 0.5f, 0.5f)), 0.01f)
        assertEquals(21f, ThemeCustomColor.contrastRatio(Color(0f, 0f, 0f), Color(1f, 1f, 1f)), 0.01f)
    }

    @Test
    fun `a mid-tone accent never gets an unreadable label`() {
        // The two colours that broke down before: too light for the app's white text, too dark for black.
        listOf("#F5F5F5", "#FB8C00", "#9E9E9E", "#6E7BFF", "#D93025").forEach { hex ->
            val palette = ThemeCustomColor.paletteFor(ThemeCustomColor.parseHex(hex))
            assertTrue(
                ThemeCustomColor.contrastRatio(palette.secondary, palette.onSecondary) >= 3f,
                "$hex needs a legible label colour",
            )
        }
    }

    private fun assertNotSameish(first: Color, second: Color) =
        assertTrue(first != second, "the page must carry a tint, not pure black")
}
