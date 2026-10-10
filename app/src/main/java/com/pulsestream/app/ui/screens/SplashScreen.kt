package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.ui.components.DotsLoader
import com.pulsestream.app.ui.theme.LocalPalette
import kotlinx.coroutines.delay

private const val SPLASH_TOTAL_MS = 2100
private const val LETTERS_START_MS = 150
private const val LETTER_STAGGER_MS = 70
private const val LETTER_MS = 320
private const val DOTS_START_MS = 600
private const val DOTS_MS = 400

private fun phase(t: Float, startMs: Int, durMs: Int): Float =
    ((t * SPLASH_TOTAL_MS - startMs) / durMs).coerceIn(0f, 1f)

/**
 * Launch animation: the "Plavio" wordmark letters stagger in, with the app's
 * signature connecting-dots loader running beneath it.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var started by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    // Same accent gradient the dots loader uses, swept across the wordmark.
    val nameBrush = remember(palette.accent, palette.accent2) {
        Brush.horizontalGradient(listOf(palette.accent, palette.accent2))
    }
    val t by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = SPLASH_TOTAL_MS, easing = LinearEasing),
        label = "splashClock"
    )
    LaunchedEffect(Unit) {
        started = true
        delay(SPLASH_TOTAL_MS.toLong() + 120L)
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B16)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.Center) {
                "Plavio".forEachIndexed { i, ch ->
                    val lt = phase(t, LETTERS_START_MS + i * LETTER_STAGGER_MS, LETTER_MS)
                    Text(
                        text = ch.toString(),
                        style = TextStyle(
                            brush = nameBrush,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .alpha(lt)
                            .graphicsLayer { translationY = (1f - lt) * 26f }
                    )
                }
            }
            Spacer(Modifier.height(26.dp))
            val dotsT = phase(t, DOTS_START_MS, DOTS_MS)
            Box(Modifier.alpha(dotsT)) {
                DotsLoader()
            }
        }
    }
}
