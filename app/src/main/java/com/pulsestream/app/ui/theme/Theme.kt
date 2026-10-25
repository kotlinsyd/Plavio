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
        // Normal mode — Sample A "Warm Ivory" (owner's pick 2026-10-24).
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF04120B),
            secondary = palette.accent2,
            onSecondary = Color(0xFF04120B),
            tertiary = palette.accent2,
            background = Color(0xFFF4EFE6),
            onBackground = Color(0xFF171A20),
            surface = Color(0xFFFFFBF2),
            onSurface = Color(0xFF171A20),
            surfaceVariant = Color(0xFFEAE3D3),
            onSurfaceVariant = Color(0xFF6F6A5E),
            outline = Color(0xFFD8D0BE),
            error = Color(0xFFB3261E)
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF062025),
            secondary = palette.accent2,
            onSecondary = Color(0xFF2A0A22),
            tertiary = palette.accent2,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.panel,
            onSurface = palette.textPrimary,
            surfaceVariant = if (palette.amoled) Color(0xFF111111) else Color(0xFF39434F),
            onSurfaceVariant = palette.textSecondary,
            outline = palette.glassBorder,
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
