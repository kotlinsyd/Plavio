package com.pulsestream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * App theme. The palette (accent theme + AMOLED black) is provided through
 * [LocalPalette] so changing it in Settings re-skins the whole app.
 */
@Composable
fun PulseStreamTheme(
    palette: AppPalette = LocalPalette.current,
    content: @Composable () -> Unit
) {
    val scheme = darkColorScheme(
        primary = palette.accent,
        onPrimary = Color(0xFF062025),
        secondary = palette.accent2,
        onSecondary = Color(0xFF2A0A22),
        tertiary = palette.accent2,
        background = palette.background,
        onBackground = TextPrimary,
        surface = palette.panel,
        onSurface = TextPrimary,
        surfaceVariant = if (palette.amoled) Color(0xFF111111) else Color(0xFF1A1A26),
        onSurfaceVariant = TextSecondary,
        outline = GlassBorder,
        error = Color(0xFFFF6B6B)
    )
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}
