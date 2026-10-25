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

/**
 * Applies the full liquid-glass treatment (fill, sheen, accent glow, rim) clipped to [shape].
 *
 * [darken] (v0.12.3, dock-only): extra dark tint layered over the clear
 * fill — raises the fill alphas and lays a translucent black wash on top.
 * The dock dropped it in v0.12.4, then v0.12.5 brought back a measured
 * 0.10 (owner's order: a little more blur AND ~10% more dark).
 *
 * [frost] (v0.12.4, dock only): raises the diffusion wash alphas so the
 * glass reads more frosted — foreground icons stay legible over bright
 * content. This codebase's glass is deliberately
 * performance-first (no live backdrop-blur pass), so the wash is how
 * "more blur" is expressed. Default 0 keeps every other surface as-is.
 */
@Composable
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(28.dp), darken: Float = 0f, frost: Float = 0f): Modifier {
    val palette = LocalPalette.current
    val tint = palette.accent2
    // A surface that explicitly asks for frost (the bottom dock) keeps
    // its frosted liquid-glass finish even in flat themes like Onyx —
    // owner's order 2026-10-25: the capsule bar must feel frosted.
    val frostHere = palette.frosted || frost > 0f
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                if (!frostHere) {
                    // Solid fallback (low-end devices): same theme colours,
                    // flat opaque surfaces instead of translucent glass.
                    listOf(palette.panel, palette.panel)
                } else if (palette.light) {
                    // Normal mode (owner's Sample A, 2026-10-24): warm-white
                    // glass — cards and the dock read as white surfaces
                    // sitting on the ivory page.
                    listOf(
                        palette.panel.copy(alpha = (0.82f + darken).coerceAtMost(0.97f)),
                        palette.panel.copy(alpha = (0.90f + darken).coerceAtMost(0.98f))
                    )
                } else {
                    listOf(
                        // Clear glass (v0.12.0, user's order): the fill drops to
                        // ~0.26/0.34 alpha so roughly 75% of what's behind the
                        // panel shows through — mirror-like clarity instead of
                        // the old milky frost (was 0.62/0.74).
                        liquidGlassTopSurface(palette.background).copy(alpha = (0.26f + darken).coerceAtMost(0.85f)),
                        liquidGlassBottomSurface(palette.background).copy(alpha = (0.34f + darken).coerceAtMost(0.9f))
                    )
                }
            ),
            shape
        )
        .then(
            if (darken > 0f) {
                Modifier.background(Color.Black.copy(alpha = darken * 0.6f), shape)
            } else {
                Modifier
            }
        )
        // Whisper of diffusion only — the heavy frost wash is gone.
        // [frost] thickens the wash slightly (dock, v0.12.4).
        .drawWithCache {
            val wash = Brush.verticalGradient(
                colors = if (!frostHere) {
                    listOf(Color.Transparent, Color.Transparent, Color.Transparent)
                } else if (palette.light) {
                    listOf(
                        Color.Black.copy(alpha = 0.03f),
                        Color.Black.copy(alpha = 0.015f),
                        Color.Black.copy(alpha = 0.02f)
                    )
                } else {
                    listOf(
                        Color.White.copy(alpha = (0.04f + frost).coerceAtMost(0.3f)),
                        Color.White.copy(alpha = (0.015f + frost * 0.5f).coerceAtMost(0.2f)),
                        Color.White.copy(alpha = (0.03f + frost).coerceAtMost(0.3f))
                    )
                }
            )
            onDrawBehind { drawRect(wash) }
        }
        .then(
            if (frostHere) Modifier.topSheen(alpha = 0.09f, fraction = 0.35f)
            else Modifier
        )
        .drawWithCache {
            if (size.minDimension <= 0f || !frostHere) {
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
        // In Normal mode the rim flips to the ink border (a white rim is
        // invisible on warm-white glass).
        .then(
            if (palette.light || !frostHere) {
                Modifier.border(1.dp, palette.glassBorder, shape)
            } else {
                Modifier.border(1.dp, LiquidRimBrush, shape)
            }
        )
}
