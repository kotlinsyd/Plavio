package com.pulsestream.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Smoky slate, rather than near-black, is the default Liquid Glass canvas.
// AMOLED remains a separate opt-in palette in [paletteFor].
val BgDeep = Color(0xFF202630)
val BgPanel = Color(0xFF303A47)
val TextPrimary = Color(0xFFF6F7FA)
val TextSecondary = Color(0xFFC5CDD7)

val GlassWhite = Color.White.copy(alpha = 0.10f)
val GlassBorder = Color.White.copy(alpha = 0.18f)

/** Opaque neutral fills used by the shared liquid-glass surface treatment. */
fun liquidGlassTopSurface(background: Color): Color = lerp(background, Color.White, 0.19f)
fun liquidGlassBottomSurface(background: Color): Color = lerp(background, Color.White, 0.11f)

/**
 * Accent color themes. PULSE is the default neon cyan/magenta identity;
 * the others re-skin every accent in the app (buttons, toggles, progress,
 * glows, selected states) through [LocalPalette].
 */
enum class AccentTheme(val label: String, val primary: Color, val secondary: Color) {
    PULSE("Pulse", Color(0xFF22D3EE), Color(0xFFE879F9)),
    CRIMSON("Crimson", Color(0xFFF43F5E), Color(0xFFFB923C)),
    OCEAN("Ocean", Color(0xFF38BDF8), Color(0xFF6366F1)),
    EMERALD("Emerald", Color(0xFF34D399), Color(0xFF22D3EE)),
    AMBER("Amber", Color(0xFFFBBF24), Color(0xFFF97316)),
    VIOLET("Violet", Color(0xFFA78BFA), Color(0xFFF0ABFC));

    companion object {
        fun byName(name: String?): AccentTheme =
            values().firstOrNull { it.name == name } ?: PULSE
    }
}

/**
 * Resolved colors for the current theme. Provided at the root via [LocalPalette];
 * every screen reads its accent/background from here so a theme change
 * re-skins the whole app without touching individual components.
 */
data class AppPalette(
    val accent: Color,
    val accent2: Color,
    val background: Color,
    val panel: Color,
    val amoled: Boolean
) {
    val gradient: Brush
        get() = Brush.horizontalGradient(listOf(accent, accent2))
}

val LocalPalette = compositionLocalOf {
    AppPalette(
        accent = AccentTheme.PULSE.primary,
        accent2 = AccentTheme.PULSE.secondary,
        background = BgDeep,
        panel = BgPanel,
        amoled = false
    )
}

/** Builds the palette for an accent theme + AMOLED-black toggle. */
fun paletteFor(accent: AccentTheme, amoled: Boolean) = AppPalette(
    accent = accent.primary,
    accent2 = accent.secondary,
    background = if (amoled) Color.Black else BgDeep,
    panel = if (amoled) Color(0xFF0A0A0A) else BgPanel,
    amoled = amoled
)
