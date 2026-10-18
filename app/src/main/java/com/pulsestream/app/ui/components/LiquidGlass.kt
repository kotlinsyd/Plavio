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

/*
 * "Liquid glass" surface language.
 *
 * A glass pane is built from four cheap layers (all plain gradients, no
 * blur and no per-card shadows, so long lists stay smooth):
 *   1. a translucent fill that is brighter at the top than the bottom,
 *   2. a soft inner sheen along the top edge (light catching the surface),
 *   3. a faint accent-coloured glow in the bottom-right corner,
 *   4. a specular rim: a 1dp border that is bright on the top-left edge,
 *      fades through the middle and picks up again at the bottom-right.
 */

/** Specular rim light for glass edges: bright top-left, soft bottom-right. */
val LiquidRimBrush: Brush = Brush.linearGradient(
    colors = listOf(
        Color.White.copy(alpha = 0.55f),
        Color.White.copy(alpha = 0.10f),
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = 0.26f)
    )
)

/**
 * Soft highlight along the top of the surface: white at [alpha] fading to
 * transparent over the top [fraction] of the height. Draw this on an already
 * clipped surface.
 */
fun Modifier.topSheen(alpha: Float = 0.10f, fraction: Float = 0.5f): Modifier =
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
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val tint = LocalPalette.current.accent2
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.14f),
                    Color.White.copy(alpha = 0.05f)
                )
            ),
            shape
        )
        .topSheen()
        .drawWithCache {
            if (size.minDimension <= 0f) {
                // Zero-size layout pass: a radial gradient needs a positive radius.
                onDrawBehind { }
            } else {
                val glow = Brush.radialGradient(
                    colors = listOf(tint.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width, size.height),
                    radius = size.maxDimension
                )
                onDrawBehind { drawRect(glow) }
            }
        }
        .border(1.dp, LiquidRimBrush, shape)
}
