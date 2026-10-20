package com.pulsestream.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * App theme. The palette (accent theme + AMOLED black) is provided through
 * [LocalPalette] so changing it in Settings re-skins the whole app.
 * Supports dark (default) and light modes via [isLight].
 */
@Composable
fun PulseStreamTheme(
    palette: AppPalette = LocalPalette.current,
    isLight: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            secondary = palette.accent2,
            onSecondary = Color.White,
            tertiary = palette.accent2,
            background = Color(0xFFF5F5F5),
            onBackground = Color(0xFF1A1A1A),
            surface = Color.White,
            onSurface = Color(0xFF1A1A1A),
            surfaceVariant = Color(0xFFE8E8E8),
            onSurfaceVariant = Color(0xFF666666),
            outline = Color(0xFFE0E0E0),
            error = Color(0xFFD32F2F)
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF062025),
            secondary = palette.accent2,
            onSecondary = Color(0xFF2A0A22),
            tertiary = palette.accent2,
            background = palette.background,
            onBackground = TextPrimary,
            surface = palette.panel,
            onSurface = TextPrimary,
            surfaceVariant = if (palette.amoled) Color(0xFF111111) else Color(0xFF39434F),
            onSurfaceVariant = TextSecondary,
            outline = GlassBorder,
            error = Color(0xFFFF6B6B)
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            content = content
        )
    }
}
