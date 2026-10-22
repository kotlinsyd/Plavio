package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.ui.components.GlowBackground
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

private val Mint = Color(0xFF3DDC97)
private val Cyan = Color(0xFF38BDF8)

private fun phase(t: Float, startMs: Int, durMs: Int): Float =
    ((t * SPLASH_TOTAL_MS - startMs) / durMs).coerceIn(0f, 1f)

/**
 * Launch animation — Sample D "Letter Build" (locked design, v0.12.5):
 * the "Plavio" letters drop in one by one from above with a soft bounce
 * (letters white, final "o" in mint), a thin cyan→mint gradient line sweeps
 * in left-to-right under the word, and the tagline fades in last.
 * Replaces the earlier gradient wordmark + connecting-dots loader.
 * Same job as before: ~2.15s, then hands off to Home, masking initial
 * load. (Slowed down a little in v0.12.7 — the owner found 1.6s too
 * quick to enjoy: wider letter stagger, softer drops, later tagline.)
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    var wordWidthPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current
    val t by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_TOTAL_MS),
        label = "splashClock"
    )
    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_TOTAL_MS.toLong() + 120L)
        onDone()
    }

    GlowBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Row(
                modifier = Modifier.onGloballyPositioned {
                    wordWidthPx = it.size.width.toFloat()
                }
            ) {
                "Plavio".forEachIndexed { i, ch ->
                    val raw = phase(t, LETTERS_START_MS + i * LETTER_STAGGER_MS, LETTER_MS)
                    val drop = EaseOutBounce.transform(raw)
                    val isLast = i == "Plavio".lastIndex
                    Text(
                        text = ch.toString(),
                        color = if (isLast) Mint else TextPrimary,
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
            }
            Spacer(Modifier.height(14.dp))
            // Gradient line sweeping in left-to-right under the wordmark,
            // spanning exactly the word's measured width.
            val lineT = phase(t, LINE_START_MS, LINE_MS)
            val lineWidth = with(density) { wordWidthPx.toDp() }
            Box(
                modifier = Modifier
                    .width(if (lineWidth > 0.dp) lineWidth else 140.dp)
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
        }
    }
}
