package com.pulsestream.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Smoky slate, rather than near-black, is the default Liquid Glass canvas.
// AMOLED remains a separate opt-in palette in [paletteFor].
val BgDeep = Color(0xFF202630)
val BgPanel = Color(0xFF303A47)
// Raw values of the DARK design (the original constants). The public
// names below read the ACTIVE palette, so Normal (light) mode re-skins
// every screen that uses them; in Dark mode they resolve to exactly
// these values.
private val DarkTextPrimary = Color(0xFFF6F7FA)
private val DarkTextSecondary = Color(0xFFC5CDD7)
private val DarkGlassFill = Color.White.copy(alpha = 0.10f)
private val DarkGlassBorder = Color.White.copy(alpha = 0.18f)

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
val GlassWhite: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.glassFill
val GlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.glassBorder

/** Opaque neutral fills used by the shared liquid-glass surface treatment. */
fun liquidGlassTopSurface(background: Color): Color = lerp(background, Color.White, 0.10f)
fun liquidGlassBottomSurface(background: Color): Color = lerp(background, Color.White, 0.06f)

/**
 * Accent color themes. PULSE is the default identity — since v0.12.7 it
 * carries the startup logo's own colors (owner's order: the green from
 * the startup animation, used in the app wherever possible): mint
 * #3DDC97 primary, cyan #38BDF8 secondary, the pair the splash wordmark
 * and its sweep line are drawn in. The others re-skin every accent in
 * the app (buttons, toggles, progress, glows, selected states) through
 * [LocalPalette].
 */
enum class AccentTheme(val label: String, val primary: Color, val secondary: Color) {
    WHITE("White", Color.White, Color(0xFFD9D9DE)),
    PULSE("Pulse", Color(0xFF3DDC97), Color(0xFF38BDF8)),
    CRIMSON("Crimson", Color(0xFFF43F5E), Color(0xFFFB923C)),
    OCEAN("Ocean", Color(0xFF38BDF8), Color(0xFF6366F1)),
    VIOLET("Violet", Color(0xFFA78BFA), Color(0xFFF0ABFC)),
    EMERALD("Emerald", Color(0xFF34D399), Color(0xFF22D3EE)),
    AMBER("Amber", Color(0xFFFBBF24), Color(0xFFF97316)),
    ROSE("Rose", Color(0xFFEC407A), Color(0xFFF48FB1));

    companion object {
        fun byName(name: String?): AccentTheme =
            values().firstOrNull { it.name == name } ?: WHITE
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
    val amoled: Boolean,
    /** True in Normal (light) mode; the dark design is the default. */
    val light: Boolean = false,
    val textPrimary: Color = DarkTextPrimary,
    val textSecondary: Color = DarkTextSecondary,
    val glassFill: Color = DarkGlassFill,
    val glassBorder: Color = DarkGlassBorder,
    /** The accent read as TEXT on surfaces; deepened in Normal mode. */
    val accentText: Color = accent,
    /** The six-theme identity this palette was built from. */
    val appTheme: AppTheme = AppTheme.ONYX,
    val accentTheme: AccentTheme = AccentTheme.PULSE,
    /**
     * Frosted glass on/off. Weak devices get the solid version of the
     * chosen theme instead (same colours, flat surfaces, no glows).
     */
    val frosted: Boolean = true,
    /** Multiplier on the ambient glow alphas in GlowBackground. */
    val glowAlpha: Float = 1f
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

/**
 * The six appearance themes (owner's pick 2026-10-25).
 * ONYX is the default for fresh installs — the owner's reference-app
 * look adopted app-wide 2026-10-25: pure black canvas, FLAT charcoal
 * cards (no glass, no glow), monochrome white accents; mint survives
 * only in the logo and splash. (Enum renamed from MIDNIGHT the same
 * day; stored "MIDNIGHT" strings map to ONYX in [AppTheme.byName].)
 * Existing installs migrate from the old dark/light + AMOLED settings
 * (see SettingsStore.appTheme).
 */
enum class AppTheme(val label: String, val blurb: String, val light: Boolean) {
    ONYX("Onyx", "Pure black mono — flat charcoal cards, white accents", false),
    WARM_IVORY("Warm Ivory", "Your current Normal, now frosted", true),
    AURORA_FROST("Aurora Frost", "The vivid one — strongest ambient colour", false),
    TRUE_BLACK("True Black", "AMOLED black — deepest blacks, best battery", false),
    TWILIGHT("Twilight", "Deep indigo night, violet-tinted glass", false),
    PEARL("Pearl", "Cool clean light — white glass over soft grey", true);

    /** Dark counterpart used to pin video surfaces (player, heroes). */
    fun darkVariant(): AppTheme = if (light) ONYX else this

    companion object {
        fun byName(name: String?): AppTheme = ONYX
    }
}

/** Builds the palette for one of the six themes. */
fun paletteForTheme(theme: AppTheme, accent: AccentTheme, frosted: Boolean = true): AppPalette {
    val base = when (theme) {
        AppTheme.ONYX -> AppPalette(
            // Onyx (owner's order 2026-10-25): the permanent theme.
            // The chosen accent applies; the default accent is White,
            // which is the approved monochrome look.
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFF070707),
            panel = Color(0xFF1B1B1D),
            amoled = false,
            textPrimary = Color.White,
            textSecondary = Color(0xFF9A9AA0),
            glassFill = Color.White.copy(alpha = 0.07f),
            glassBorder = Color.White.copy(alpha = 0.09f),
            accentText = if (accent.primary == Color.White) Color.Black else Color.White,
            glowAlpha = 0f
        )
        AppTheme.WARM_IVORY -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF4EFE6),
            panel = Color(0xFFFFFBF2),
            amoled = false,
            light = true,
            textPrimary = Color(0xFF171A20),
            textSecondary = Color(0xFF6F6A5E),
            glassFill = Color.Black.copy(alpha = 0.05f),
            glassBorder = Color(0xFF171A20).copy(alpha = 0.14f),
            accentText = lerp(accent.primary, Color.Black, 0.42f),
            glowAlpha = 1.1f
        )
        AppTheme.AURORA_FROST -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFF060B16),
            panel = Color(0xFF0E1626),
            amoled = false,
            textPrimary = Color(0xFFF2F6FC),
            textSecondary = Color(0xFFB9C8DE),
            glassFill = Color.White.copy(alpha = 0.14f),
            glassBorder = Color.White.copy(alpha = 0.24f),
            glowAlpha = 1.7f
        )
        AppTheme.TRUE_BLACK -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color.Black,
            panel = Color(0xFF0D0D0F),
            amoled = true,
            textPrimary = Color(0xFFF5F5F5),
            textSecondary = Color(0xFFB9B9C0),
            glowAlpha = 0.55f
        )
        AppTheme.TWILIGHT -> AppPalette(
            accent = Color(0xFF9D8BFF),
            accent2 = Color(0xFF38BDF8),
            background = Color(0xFF0C0A1D),
            panel = Color(0xFF171233),
            amoled = false,
            textPrimary = Color(0xFFEFEAFF),
            textSecondary = Color(0xFFC4BCE8),
            glassFill = Color.White.copy(alpha = 0.11f),
            glassBorder = Color.White.copy(alpha = 0.20f),
            accentText = Color(0xFF9D8BFF),
            glowAlpha = 1.2f
        )
        AppTheme.PEARL -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF2F4F7),
            panel = Color.White,
            amoled = false,
            light = true,
            textPrimary = Color(0xFF10131A),
            textSecondary = Color(0xFF4E586A),
            glassFill = Color.Black.copy(alpha = 0.04f),
            glassBorder = Color(0xFF10131A).copy(alpha = 0.12f),
            accentText = lerp(accent.primary, Color.Black, 0.42f),
            glowAlpha = 1.0f
        )
    }
    val themed0 = base.copy(appTheme = theme, accentTheme = accent)
    // Onyx is flat BY DESIGN (not a low-end fallback): no frost, no glow.
    val themed = if (theme == AppTheme.ONYX) {
        themed0.copy(frosted = false, glowAlpha = 0f)
    } else {
        themed0
    }
    // Solid fallback for low-end devices: same colours, flat surfaces.
    return if (frosted) themed else themed.copy(frosted = false, glowAlpha = 0f)
}

/** Builds the palette for an accent theme + AMOLED-black toggle. */
fun paletteFor(accent: AccentTheme, amoled: Boolean, light: Boolean = false): AppPalette =
    if (light) {
        // Normal mode — owner's pick 2026-10-24: Sample A "Warm Ivory".
        AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF4EFE6),
            panel = Color(0xFFFFFBF2),
            amoled = false,
            light = true,
            textPrimary = Color(0xFF171A20),
            textSecondary = Color(0xFF6F6A5E),
            glassFill = Color.Black.copy(alpha = 0.05f),
            glassBorder = Color(0xFF171A20).copy(alpha = 0.14f),
            accentText = lerp(accent.primary, Color.Black, 0.42f)
        )
    } else {
        AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = if (amoled) Color.Black else BgDeep,
            panel = if (amoled) Color(0xFF0A0A0A) else BgPanel,
            amoled = amoled
        )
    }

/**
 * The dark variant of this palette (same accent/AMOLED choices). Video
 * surfaces — the player, trailer players — are pinned to it in BOTH
 * modes: like the home/detail heroes, they stay cinematic.
 */
fun AppPalette.asDark(): AppPalette =
    if (!light) this
    else paletteForTheme(appTheme.darkVariant(), accentTheme, frosted)
