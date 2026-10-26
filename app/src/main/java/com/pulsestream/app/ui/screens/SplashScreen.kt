package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.R
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToInt

private const val SPLASH_TOTAL_MS = 2150

// Sample G "Orbit Build" timeline (locked by the owner 2026-10-26,
// supersedes Sample B): the weave's two squares start apart on an
// orbit and spiral into the locked weave, the glow blooms behind
// them, a shine sweep crosses the locked mark, the Plavio wordmark
// rises whole, the weave-gradient line grows center-out under it,
// and the tagline fades in last.
private const val ORBIT_MS = 850
private const val FADE_MS = 300
private const val GLOW_START_MS = 400
private const val GLOW_MS = 500
private const val SHINE_START_MS = 950
private const val SHINE_MS = 550
private const val WORD_START_MS = 880
private const val WORD_MS = 590
private const val LINE_START_MS = 1345
private const val LINE_MS = 420
private const val TAGLINE_START_MS = 1600
private const val TAGLINE_MS = 460

// Weave identity gradient (violet -> pink -> peach).
private val WeaveGradient = listOf(
    Color(124, 58, 237),
    Color(168, 85, 247),
    Color(219, 39, 119),
    Color(253, 186, 116)
)

private fun phase(t: Float, startMs: Int, durMs: Int): Float =
    ((t * SPLASH_TOTAL_MS - startMs) / durMs).coerceIn(0f, 1f)

private fun easeOutCubic(x: Float): Float = 1f - (1f - x).pow(3)

/**
 * Launch animation — Sample G "Orbit Build" (owner-locked 2026-10-26,
 * ships in v0.14.5, supersedes Sample B): the weave's two halves
 * spiral in from an orbit and lock, the glow blooms, a shine sweep
 * crosses the locked mark, "Plavio" rises whole, weave-gradient
 * line grows under the word, tagline fades in. ~2.15s, then hands
 * off to Home. Splash-waits-for-feed is unchanged (owner's order
 * 2026-10-24): the animation is the minimum stay; the orbit spins
 * under the tagline while the feed is still loading, with an 8s
 * safety cap in the caller so a dead network can't trap the splash.
 */
@Composable
fun SplashScreen(feedReady: Boolean, onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var wordWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val t by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_TOTAL_MS),
        label = "splashClock"
    )
    var animDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_TOTAL_MS.toLong() + 120L)
        animDone = true
    }
    LaunchedEffect(animDone, feedReady) {
        if (animDone && feedReady) onDone()
    }

    val orbitT = easeOutCubic(phase(t, 0, ORBIT_MS))
    val fadeT = phase(t, 0, FADE_MS)
    val glowT = easeOutCubic(phase(t, GLOW_START_MS, GLOW_MS))
    val shineT = phase(t, SHINE_START_MS, SHINE_MS)
    val wordT = easeOutCubic(phase(t, WORD_START_MS, WORD_MS))
    val lineT = easeOutCubic(phase(t, LINE_START_MS, LINE_MS))
    val tagT = phase(t, TAGLINE_START_MS, TAGLINE_MS)

    // The weave pre-split along its diagonal (feathered mask, exact
    // composite at rest): the two halves orbit independently, then
    // read as the single locked mark.
    val markTlBitmap = remember(context) {
        ImageBitmap.imageResource(context.resources, R.drawable.weave_mark_tl)
    }
    val markBrBitmap = remember(context) {
        ImageBitmap.imageResource(context.resources, R.drawable.weave_mark_br)
    }

    GlowBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Glow first: a radial gradient drawn in code — violet core
            // melting to transparency well inside its bounds, so no
            // rectangular asset edge can ever show (owner's fix
            // 2026-10-26; the old weave_glow.png had opaque corners).
            // Then the two weave halves spiral in over it, and the
            // shine sweep crosses the locked mark (BlendMode.SrcIn
            // keeps the band only where the weave is drawn).
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                Canvas(Modifier.size(200.dp)) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0f to Color(124, 58, 237).copy(alpha = 0.30f * glowT),
                                0.45f to Color(219, 39, 119).copy(alpha = 0.13f * glowT),
                                0.75f to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            center = center,
                            radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f
                    )
                }
                Canvas(
                    Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            val s = 0.92f + 0.08f * orbitT
                            scaleX = s
                            scaleY = s
                            alpha = fadeT
                        }
                ) {
                    val dst = IntSize(size.width.toInt(), size.height.toInt())
                    // Orbit: halves start ~40dp out at opposite angles
                    // (-120 degrees and +60) and spiral in as the
                    // radius eases to zero and the angle sweeps +55.
                    val radiusPx = (1f - orbitT) * 40.dp.toPx()
                    val ang = Math.toRadians((-120.0 + 55.0 * orbitT))
                    val dxTl = (kotlin.math.cos(ang) * radiusPx).roundToInt()
                    val dyTl = (kotlin.math.sin(ang) * radiusPx).roundToInt()
                    drawImage(
                        markTlBitmap,
                        dstSize = dst,
                        dstOffset = IntOffset(dxTl, dyTl)
                    )
                    drawImage(
                        markBrBitmap,
                        dstSize = dst,
                        dstOffset = IntOffset(-dxTl, -dyTl)
                    )
                    if (shineT > 0f && shineT < 1f) {
                        val bandW = size.width * 0.42f
                        val cx = -size.width * 0.45f +
                            (size.width * 1.9f) * shineT
                        withTransform({
                            rotate(-24f, center)
                            translate(left = cx - bandW / 2f, top = -size.height * 0.35f)
                        }) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0f),
                                        Color.White.copy(alpha = 0.85f),
                                        Color.White.copy(alpha = 0f)
                                    )
                                ),
                                size = androidx.compose.ui.geometry.Size(
                                    bandW, size.height * 1.7f
                                ),
                                blendMode = BlendMode.SrcIn
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // The wordmark rises whole (no per-letter build).
            Text(
                text = "Plavio",
                color = TextPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .alpha(wordT)
                    .graphicsLayer { translationY = (1f - wordT) * 44f }
                    .onGloballyPositioned { wordWidthPx = it.size.width.toFloat() }
            )
            Spacer(Modifier.height(12.dp))
            // Weave-gradient line growing center-out under the word.
            val lineWidth = with(density) { (wordWidthPx * 0.78f).toDp() }
            Box(
                modifier = Modifier
                    .width(if (lineWidth > 0.dp) lineWidth else 110.dp)
                    .height(3.dp)
                    .graphicsLayer {
                        scaleX = lineT
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .background(
                        Brush.horizontalGradient(WeaveGradient),
                        RoundedCornerShape(50)
                    )
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your streaming universe",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alpha(tagT)
            )
            Spacer(Modifier.height(18.dp))
            // The signature orbit: spins under the tagline while the
            // splash waits for the home feed (owner's order 2026-10-24).
            OrbitDots(modifier = Modifier.size(40.dp).alpha(tagT))
        }
    }
}
