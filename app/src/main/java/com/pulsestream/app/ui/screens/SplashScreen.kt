package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val SPLASH_TOTAL_MS = 2150
private const val LETTERS_START_MS = 100
private const val LETTER_STAGGER_MS = 165
private const val LETTER_MS = 640
private const val LINE_START_MS = 1020
private const val LINE_MS = 620
private const val TAGLINE_START_MS = 1600
private const val TAGLINE_MS = 460
private const val O_START_MS = 880
private const val O_MS = 700
private const val TRI_START_MS = 1420
private const val TRI_MS = 380
private const val RING_START_MS = 1560
private const val RING_MS = 560

private val Mint = Color(0xFF3DDC97)
private val Cyan = Color(0xFF38BDF8)

private fun phase(t: Float, startMs: Int, durMs: Int): Float =
    ((t * SPLASH_TOTAL_MS - startMs) / durMs).coerceIn(0f, 1f)

/**
 * Launch animation — Sample D "Letter Build" (locked design, v0.12.5):
 * the "Plavi" letters drop in one by one from above with a soft bounce,
 * a thin cyan→mint gradient line sweeps in left-to-right under the word,
 * and the tagline fades in last. The final "o" is Sample K "Grow + Ring"
 * (owner's pick, 2026-10-22 — see SplashO below): a tiny mint disc with
 * the play triangle grows to the letters' size, exactly in line.
 * Replaces the earlier gradient wordmark + connecting-dots loader.
 * Same job as before: ~2.15s, then hands off to Home, masking initial
 * load. (Slowed down a little in v0.12.7 — the owner found 1.6s too
 * quick to enjoy: wider letter stagger, softer drops, later tagline.)
 */
@Composable
fun SplashScreen(feedReady: Boolean, onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var wordWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
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
    // Owner's order 2026-10-24: the animation is the minimum stay. If
    // the home feed is still loading, the splash waits here (orbit
    // spinning under the tagline); if the feed is already loaded it
    // leaves right as the animation ends.
    LaunchedEffect(animDone, feedReady) {
        if (animDone && feedReady) onDone()
    }

    GlowBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
          // Word + line share one start-aligned column so the line sits
          // exactly like in the logo: left-aligned with the word and
          // ~78% of its width (measured: 315px line under a 405px word).
          Column(horizontalAlignment = Alignment.Start) {
            Row(
                modifier = Modifier.onGloballyPositioned {
                    wordWidthPx = it.size.width.toFloat()
                }
            ) {
                "Plavi".forEachIndexed { i, ch ->
                    val raw = phase(t, LETTERS_START_MS + i * LETTER_STAGGER_MS, LETTER_MS)
                    val drop = EaseOutBounce.transform(raw)
                    Text(
                        text = ch.toString(),
                        color = TextPrimary,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            // Letters appear as they start falling, then stay.
                            .alpha((raw * 3f).coerceIn(0f, 1f))
                            .graphicsLayer {
                                translationY = (1f - drop) * -64f
                            }
                    )
                }
                SplashO(t)
            }
            Spacer(Modifier.height(14.dp))
            // Gradient line sweeping in left-to-right under the wordmark,
            // spanning exactly the word's measured width.
            val lineT = phase(t, LINE_START_MS, LINE_MS)
            val lineWidth = with(density) { (wordWidthPx * 0.78f).toDp() }
            Box(
                modifier = Modifier
                    .width(if (lineWidth > 0.dp) lineWidth else 110.dp)
                    .height(3.dp)
                    .graphicsLayer {
                        scaleX = lineT
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .background(
                        Brush.horizontalGradient(listOf(Cyan, Mint)),
                        RoundedCornerShape(50)
                    )
            )
          } // end word + line column
            Spacer(Modifier.height(16.dp))
            val tagT = phase(t, TAGLINE_START_MS, TAGLINE_MS)
            Text(
                // Tagline (v0.12.8, owner's order): back to the words
                // that used to sit under "Plavio" at the top-left of
                // Home — "Your streaming universe". (The motto stays on
                // the PlavioHD promo card; this is the startup only.)
                text = "Your streaming universe",
                color = TextSecondary.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.alpha(tagT)
            )
            Spacer(Modifier.height(18.dp))
            // The signature orbit (owner's order 2026-10-24): part of
            // the startup now, spinning under the tagline while the
            // splash waits for the home feed.
            OrbitDots(modifier = Modifier.size(40.dp).alpha(tagT))
        }
    }
}

/**
 * The final "o" — Sample K "Grow + Ring" (owner's pick, 2026-10-22):
 * a tiny mint circle grows until it matches the other letters' size,
 * the play triangle fades in, and one soft mint ring pulses out.
 * Alignment is exact by construction: an invisible twin of the letter
 * this replaces holds the slot (same layout box, so the word's width,
 * height and baseline never move), and the disc is placed from the
 * twin's own measured ink box — diameter = the ink height exactly
 * (in the logo the disc IS the o it replaces), bottom on the ink's
 * own bottom edge, which includes the round letter's natural
 * overshoot. No up, no down, no bigger, no smaller (the owner's
 * condition on the pick).
 */
@Composable
private fun SplashO(t: Float) {
    val density = LocalDensity.current
    // Geometry comes from the twin letter's OWN layout result
    // (delivered via onTextLayout below): the exact ink box the
    // renderer gives the real "o" — this device, this font, this
    // size, this font-scale. The disc fills that box exactly, so it
    // cannot sit up, down, bigger or smaller than the letter it
    // replaces. (v0.12.16 measured a separate string with a 1.53x
    // multiplier and shipped a disc ~3.5x the letters' height,
    // swallowing the "i" — root cause found in the owner's
    // screenshot, fixed here in v0.12.17.)
    var oLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // getBoundingBox() is the LINE box (ascent + descent + leading), not
    // the glyph's ink — using it made the disc ~2x the letters' height
    // (v0.12.17 screenshot: disc swallowed the "i"). The real ink of the
    // "o" is measured with a Paint set to the same typeface (bold
    // default), size (40sp incl. font scale) and read relative to the
    // layout's own baseline. Logo proportions (measured from the logo
    // file): disc = 1.05 x the x-height-ish ink of "o", bottom sitting
    // on the o's natural overshoot below the baseline.
    val inkPx = remember(density) {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = if (android.os.Build.VERSION.SDK_INT >= 28)
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, 700, false)
            else android.graphics.Typeface.DEFAULT_BOLD
            textSize = with(density) { 40.sp.toPx() }
        }
        val r = android.graphics.Rect()
        paint.getTextBounds("o", 0, 1, r)
        // top/bottom are relative to the baseline (negative = above).
        Triple(r.top.toFloat(), r.bottom.toFloat(), r.height().toFloat())
    }
    val layout = oLayout
    val bbox = layout?.getBoundingBox(0)
    val baselinePx = layout?.getLineBaseline(0) ?: 0f
    val diameterPx = inkPx.third * 1.02f
    val centerX = bbox?.let { it.left + it.width / 2f } ?: 0f
    val inkBottom = baselinePx + inkPx.second

    val growRaw = phase(t, O_START_MS, O_MS)
    val grow = CubicBezierEasing(0.25f, 1.35f, 0.35f, 1f).transform(growRaw)
    val triAlpha = phase(t, TRI_START_MS, TRI_MS)
    val ringRaw = phase(t, RING_START_MS, RING_MS)

    Box {
        // Invisible twin of the replaced letter: identical layout box.
        Text(
            text = "o",
            color = Color.Transparent,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            onTextLayout = { oLayout = it }
        )
        if (growRaw > 0f && bbox != null) {
            val d = with(density) { diameterPx.toDp() }
            val left = with(density) { (centerX - diameterPx / 2f).toDp() }
            val top = with(density) { (inkBottom - diameterPx).toDp() }
            if (ringRaw > 0f && ringRaw < 1f) {
                Box(
                    modifier = Modifier
                        .offset(left, top)
                        .size(d)
                        .graphicsLayer {
                            val rs = 0.85f + 0.47f * ringRaw
                            scaleX = rs
                            scaleY = rs
                            alpha = (1f - ringRaw) * 0.7f
                        }
                        .border(2.dp, Mint, CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .offset(left, top)
                    .size(d)
                    .graphicsLayer {
                        val gs = 0.06f + 0.94f * grow
                        scaleX = gs
                        scaleY = gs
                        alpha = (growRaw * 3f).coerceIn(0f, 1f)
                    }
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF45E5A8), Color(0xFF17C98F))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(d * 0.44f)
                        .alpha(triAlpha)
                ) {
                    val w = size.width
                    val h = size.height
                    drawPath(
                        Path().apply {
                            moveTo(w * 0.32f, h * 0.20f)
                            lineTo(w * 0.32f, h * 0.80f)
                            lineTo(w * 0.82f, h * 0.50f)
                            close()
                        },
                        Color(0xFF0A1526)
                    )
                }
            }
        }
    }
}
