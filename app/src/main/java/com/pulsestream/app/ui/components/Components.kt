package com.pulsestream.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsestream.app.data.MetaPreview
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/** Full-screen deep background with soft accent radial glows.
 * On TV the 3 fullscreen radial gradients are skipped (flat background):
 * re-executing 3 fullscreen gradient shaders on every animation frame is
 * fill-rate-bound on weak TV GPUs and turned small focus animations into
 * jank. Mobile keeps the glows. */
@Composable
fun GlowBackground(content: @Composable BoxScope.() -> Unit) {
    val palette = LocalPalette.current
    val isTv = rememberIsTv()
    Box(modifier = Modifier.fillMaxSize().background(palette.background)) {
        if (!isTv) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val glow1 = palette.accent
            val glow2 = palette.accent2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.06f),
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = Offset(w * 0.12f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow2.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(w * 0.92f, h * 0.55f),
                    radius = w * 0.8f
                ),
                radius = w * 0.8f,
                center = Offset(w * 0.92f, h * 0.55f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1.copy(alpha = 0.07f), Color.Transparent),
                    center = Offset(w * 0.4f, h * 1.02f),
                    radius = w * 0.9f
                ),
                radius = w * 0.9f,
                center = Offset(w * 0.4f, h * 1.02f)
            )
            }
        }
        content()
    }
}

/** Liquid-glass card: gradient fill, top sheen, accent glow and a specular rim (see [liquidGlass]). */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val clickableMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .then(clickableMod)
            .tvFocusRing(shape)
            .liquidGlass(shape),
        content = content
    )
}

/**
 * Poster card with glow shadow for catalog rows / grids.
 *
 * When [onLongClick] is provided, the card itself handles the combined
 * tap/long-press gesture: a plain inner `clickable` would consume the press
 * and a parent's long-press handler would never fire (this is what made
 * "long-press to pin" unreachable on the Live tab).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    meta: MetaPreview,
    badge: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gestureModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Column(
        modifier = modifier
            .width(128.dp)
            .then(gestureModifier)
            .focusable()
            .tvFocusRing(RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .liquidGlass(RoundedCornerShape(16.dp))
        ) {
            SafePosterImage(
                model = meta.poster,
                contentDescription = meta.name,
                modifier = Modifier.fillMaxSize()
            )
            if (badge != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = meta.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = MaterialTheme.typography.bodySmall.lineHeight
        )
        val sub = meta.releaseInfo?.take(4)
        if (sub != null) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onMoreClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }
        if (onMoreClick != null) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "View all $title",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(LocalPalette.current.gradient, RoundedCornerShape(28.dp))
                // Glossy top highlight + rim light, like a glass button.
                .topSheen(alpha = 0.28f, fraction = 0.55f)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.08f))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(horizontal = 28.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0A0A12)
            )
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        DotsLoadingIndicator(message)
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            GradientButton("Retry", onRetry)
        }
    }
}

@Composable
fun EmptyState(title: String, message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(LocalPalette.current.gradient),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFF0A0A12), style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                GradientButton(actionLabel, onAction)
            }
        }
    }
}

/**
 * Crash-safe image: guards null/blank URLs (Coil would attempt-and-fail on
 * blank strings from sloppy addon data) and swaps in a glyph placeholder on
 * load error instead of leaving a broken frame.
 */
@Composable
fun SafePosterImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val url = (model as? String)?.takeIf { it.isNotBlank() }
    var loadFailed by remember(model) { mutableStateOf(false) }
    Box(
        modifier = modifier.background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (!loadFailed && url != null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = { loadFailed = true }
            )
        } else {
            Text(
                "▶",
                color = TextSecondary.copy(alpha = 0.45f),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}


/**
 * Connecting-dots loading animation in the app's accent gradient: a wave of
 * light travels through dots linked by glowing segments. Used everywhere
 * loading happens: app launch, pull-to-refresh, search, and the player.
 */
@Composable
fun DotsLoader(
    modifier: Modifier = Modifier,
    dotCount: Int = 5
) {
    val palette = LocalPalette.current
    val transition = rememberInfiniteTransition(label = "dots")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "dotsWave"
    )
    Canvas(
        modifier
            .width((dotCount * 22).dp)
            .height(26.dp)
    ) {
        val spacing = size.width / (dotCount + 1)
        val cy = size.height / 2f
        val centers = List(dotCount) { i -> Offset(spacing * (i + 1), cy) }
        // Glow peaks as the wave passes each position (0..1).
        fun glow(index: Float): Float {
            val d = (((index / dotCount) - wave) % 1f + 1f) % 1f
            val c = 0.5f + 0.5f * kotlin.math.cos(d * 2f * kotlin.math.PI.toFloat())
            return c * c
        }
        // Connecting segments light up between lit dots.
        for (i in 0 until dotCount - 1) {
            val g = maxOf(glow(i.toFloat()), glow((i + 1).toFloat()))
            drawLine(
                color = palette.accent.copy(alpha = 0.10f + 0.55f * g),
                start = centers[i],
                end = centers[i + 1],
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        // Dots colored across the accent gradient.
        centers.forEachIndexed { i, c ->
            val g = glow(i.toFloat())
            val t = if (dotCount == 1) 0f else i / (dotCount - 1).toFloat()
            drawCircle(
                color = lerp(palette.accent, palette.accent2, t)
                    .copy(alpha = 0.35f + 0.65f * g),
                radius = (4.5f + 2.5f * g).dp.toPx(),
                center = c
            )
        }
    }
}

@Composable
fun DotsLoadingIndicator(message: String = "Loading…") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DotsLoader()
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Big gradient Play button with an icon, used on the detail screen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: String = "Play",
    isLoading: Boolean = false,
    onLongClick: (() -> Unit)? = null
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(52.dp)
            .tvFocusRing(RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .then(
                if (enabled && !isLoading) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlass(RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = text,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Liquid-glass secondary button. For primary actions use [PlayButton].
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .tvFocusRing(shape)
            .liquidGlass(shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}
