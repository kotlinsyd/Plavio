package com.pulsestream.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteContrastTest {
    @Test
    fun defaultPaletteKeepsTextReadableOnCanvasPanelsAndGlass() {
        val palette = paletteFor(AccentTheme.PULSE, amoled = false)
        val surfaces = listOf(
            palette.background,
            palette.panel,
            liquidGlassTopSurface(palette.background),
            liquidGlassBottomSurface(palette.background)
        )

        surfaces.forEach { surface ->
            assertTrue("Primary text contrast on $surface", contrastRatio(TextPrimary, surface) >= 4.5)
            assertTrue("Secondary text contrast on $surface", contrastRatio(TextSecondary, surface) >= 4.5)
        }
        assertTrue(luminance(palette.panel) > luminance(palette.background))
        assertTrue(luminance(liquidGlassTopSurface(palette.background)) > luminance(palette.panel))
        assertTrue(luminance(liquidGlassBottomSurface(palette.background)) > luminance(palette.background))
    }

    @Test
    fun amoledOptInRetainsPureBlackCanvasAndNearBlackPanel() {
        val palette = paletteFor(AccentTheme.PULSE, amoled = true)

        assertEquals(Color.Black, palette.background)
        assertEquals(Color(0xFF0A0A0A), palette.panel)
        assertTrue(palette.amoled)
        assertTrue(contrastRatio(TextPrimary, palette.background) >= 4.5)
        assertTrue(contrastRatio(TextPrimary, palette.panel) >= 4.5)
    }

    private fun contrastRatio(foreground: Color, background: Color): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun linear(channel: Float): Double {
            val value = channel.toDouble()
            return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
    }
}
