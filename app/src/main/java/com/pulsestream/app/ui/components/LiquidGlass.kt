package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.liquidGlassBottomSurface
import com.pulsestream.app.ui.theme.liquidGlassTopSurface

/*
 * "Liquid glass" surface language.
 *
 * A performance-first glass surface. Catalog cards use inexpensive gradients;
 * floating overlays get stronger translucency, edge lighting and depth without
 * forcing a blur pass over every poster during scrolling.
 */

/** Cut-glass rim light: crisp bright edges, strongest top-left, defined bottom edge. */
val LiquidRimBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.50f),
        Color.White.copy(alpha = 0.14f),
        Color.White.copy(alpha = 0.05f),
        Color.White.copy(alpha = 0.26f)
    )
)

/**
 * Soft highlight along the top of the surface: white at [alpha] fading to
 * transparent over the top [fraction] of the height. Draw this on an already
 * clipped surface.
 */
fun Modifier.topSheen(alpha: Float = 0.06f, fraction: Float = 0.35f): Modifier =
    drawWithCache {
        val endY = size.height * fraction
        val brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
            startY = 0f,
            endY = if (endY > 0f) endY else 1f
        )
        onDrawBehind { drawRect(brush) }
    }

/** Applies the full liquid-glass treatment (fill, sheen, accent glow, rim) clipped to [shape]. */
@Composable
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(28.dp)): Modifier {
    val palette = LocalPalette.current
    val tint = palette.accent2
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    // Sample F "Frosted Clean" + cut edges + pills (user's pick
                    // 2026-10-20): milky frosted fill — the background reads as
                    // diffused color through the panel, not sharp detail.
                    liquidGlassTopSurface(palette.background).copy(alpha = 0.62f),
                    liquidGlassBottomSurface(palette.background).copy(alpha = 0.74f)
                )
            ),
            shape
        )
        // Frost wash: a soft white diffusion layer that sells the frosted look.
        .drawWithCache {
            val wash = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.03f),
                    Color.White.copy(alpha = 0.07f)
                )
            )
            onDrawBehind { drawRect(wash) }
        }
        .topSheen(alpha = 0.12f, fraction = 0.35f)
        .drawWithCache {
            if (size.minDimension <= 0f) {
                // Zero-size layout pass: a radial gradient needs a positive radius.
                onDrawBehind { }
            } else {
                val glow = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.06f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = size.maxDimension
                )
                onDrawBehind { drawRect(glow) }
            }
        }
        // Defined 1.dp cut edge — the bright rim is what reads as "cut glass".
        .border(1.dp, LiquidRimBrush, shape)
}
