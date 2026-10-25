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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.pulsestream.app.ui.theme.AppTheme
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
        if (!isTv && palette.glowAlpha > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val glow1 = palette.accent
            val glow2 = palette.accent2
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1.copy(alpha = 0.16f * palette.glowAlpha), Color.Transparent),
                    center = Offset(w * 0.12f, h * 0.06f),
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = Offset(w * 0.12f, h * 0.06f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow2.copy(alpha = 0.13f * palette.glowAlpha), Color.Transparent),
                    center = Offset(w * 0.92f, h * 0.55f),
                    radius = w * 0.8f
                ),
                radius = w * 0.8f,
                center = Offset(w * 0.92f, h * 0.55f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glow1.copy(alpha = 0.07f * palette.glowAlpha), Color.Transparent),
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
    // Pill-leaning radius (user's order 2026-10-20): big soft corners.
    val shape = RoundedCornerShape(28.dp)
    val clickableMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .then(clickableMod)
            .tvFocusRing(shape)
            .liquidGlass(shape),
        content = content
    )
}

/** A compact glass control with a reliable 48dp minimum touch target. */
@Composable
fun GlassIconButton(
    image: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary
) {
    // Full pill for small glass controls (user's order 2026-10-20).
    val shape = RoundedCornerShape(50)
    IconButton(
        onClick = onClick,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .liquidGlass(shape)
    ) {
        Icon(image, contentDescription = contentDescription, tint = tint)
    }
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
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    showRatingChip: Boolean = false,
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
                model = tmdbSizedImage(meta.poster, "w342"),
                contentDescription = meta.name,
                modifier = Modifier.fillMaxSize()
            )
            // WATCHED pill for watched movies (owner's Sample B pick,
            // 2026-10-24). Series posters stay clean \u2014 episodes carry
            // their own pills on the detail page.
            val watchedKeys by com.pulsestream.app.data.WatchedMarks.keys.collectAsState()
            if (meta.type == "movie" &&
                com.pulsestream.app.data.WatchedMarks.movieKey(meta.id) in watchedKeys
            ) {
                WatchedPill(Modifier.align(Alignment.BottomStart).padding(6.dp))
            }
            // H-A rating chip (owner's pick 2026-10-24): a small glass
            // pill on the poster's top-left — mint star, white value.
            // Opt-in via [showRatingChip] (Home rails only); every other
            // PosterCard caller keeps the clean poster. Shown only when
            // the title carries a rating.
            if (showRatingChip) {
                meta.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFF040A12).copy(alpha = 0.72f), RoundedCornerShape(50))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "★",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                            ),
                            color = LocalPalette.current.accent
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            rating,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
            // Poster only — no corner tag on channel cards.
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
        modifier = modifier.sizeIn(minHeight = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = androidx.compose.foundation.layout.PaddingValues()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(LocalPalette.current.gradient, RoundedCornerShape(28.dp))
                // Glossy top highlight + rim light, like a glass button.
                .topSheen(alpha = 0.16f, fraction = 0.55f)
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.08f))
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
 * TMDB serves the same artwork at fixed widths — rails ask for the width
 * they actually draw instead of decoding a w780/original upload into a
 * 128dp card. Non-TMDB URLs (and malformed ones) pass through untouched.
 */
internal fun tmdbSizedImage(url: String?, segment: String): String? {
    if (url == null) return null
    val marker = "image.tmdb.org/t/p/"
    val start = url.indexOf(marker)
    if (start < 0) return url
    val rest = url.substring(start + marker.length)
    val slash = rest.indexOf('/')
    if (slash < 0) return url
    return url.substring(0, start + marker.length) + segment + rest.substring(slash)
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
 * Sample C (owner's pick 2026-10-24): the You May Like loading tile — a
 * soft light sweep over a dark tile with two faint title bars, no
 * spinner. Shown while TMDB recommendations load.
 */
@Composable
fun ShimmerPoster(modifier: Modifier = Modifier) {
    var widthPx by remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerSweep"
    )
    Box(
        modifier = modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset((sweep * widthPx).roundToInt(), 0) }
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .height(9.dp)
                    .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
            )
            Box(
                Modifier
                    .width(36.dp)
                    .height(9.dp)
                    .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(99.dp))
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
    dotCount: Int = 7
) {
    val palette = LocalPalette.current
    val reduced = com.pulsestream.app.data.rememberReducedMotion()
    val wave = if (reduced) 0.25f else {
        val transition = rememberInfiniteTransition(label = "dots")
        val animated by transition.animateFloat(initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1500, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "dotsWave")
        animated
    }
    Canvas(
        modifier
            .width((dotCount * 20).dp)
            .height(26.dp)
    ) {
        // Wave (owner's pick 2026-10-25, Sample B): a pulse of light starts
        // at the CENTER dot and travels out to both edges and back. The old
        // left-to-right wave read as a tight blob — and the Home call site
        // squashed this 5-dot canvas into a 34.dp box, packing the dots.
        // Dots are now spaced across the full canvas width and sized from
        // the spacing, so they can never crowd each other.
        val n = dotCount.coerceAtLeast(1)
        val center = (n - 1) / 2f
        val maxD = center.coerceAtLeast(0.5f)
        val tri = if (wave < 0.5f) wave * 2f else 2f - wave * 2f
        val r = tri * (maxD + 0.6f)
        val spacing = size.width / n
        val cy = size.height / 2f
        val baseR = minOf(spacing * 0.30f, size.height * 0.30f)
        for (i in 0 until n) {
            val d = kotlin.math.abs(i - center)
            val g0 = (1f - kotlin.math.abs(r - d) / 1.1f).coerceIn(0f, 1f)
            val g = g0 * g0
            drawCircle(
                color = (if (palette.appTheme == AppTheme.ONYX) palette.accent
                    else lerp(palette.accent, palette.accent2, d / maxD))
                    .copy(alpha = 0.30f + 0.70f * g),
                radius = baseR * (0.55f + 0.75f * g),
                center = Offset(spacing * (i + 0.5f), cy)
            )
        }
    }
}

@Composable
fun DotsLoadingIndicator(message: String = "Loading…") {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        OrbitDots(modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** A rating line whose \u2605 wears [starColor] \u2014 the accent colour
 *  drives the small things everywhere (owner's order 2026-10-25). */
fun accentStarText(text: String, starColor: Color): AnnotatedString = buildAnnotatedString {
    text.split("\u2605").forEachIndexed { index, part ->
        if (index > 0) {
            withStyle(SpanStyle(color = starColor, fontWeight = FontWeight.Bold)) { append("\u2605") }
        }
        append(part)
    }
}

/** Small glass "\u2713 WATCHED" pill (owner's Sample B pick, 2026-10-24). */
@Composable
fun WatchedPill(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color(0xC7050A12), RoundedCornerShape(50))
            .border(1.dp, Color(0xFF3DDC97).copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = Color(0xFF3DDC97),
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "WATCHED",
            color = Color(0xFF3DDC97),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * The signature dots, circling (owner's B1 Orbit pick, 2026-10-24): a
 * glowing head dot orbits an exact ring with four fading tail dots
 * behind it, over a faint dotted ring \u2014 the refresh DotsLoader's
 * family, in circle form. Geometry is computed on one true circle
 * (design space 40 units, ring radius 13.5) so nothing wobbles.
 */
@Composable
fun OrbitDots(modifier: Modifier = Modifier) {
    val palette = LocalPalette.current
    val reduced = com.pulsestream.app.data.rememberReducedMotion()
    val rotation = if (reduced) 0f else {
        val transition = rememberInfiniteTransition(label = "orbit")
        val animated by transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "orbitRotation"
        )
        animated
    }
    Canvas(modifier) {
        val unit = size.minDimension / 40f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val ringR = 13.5f * unit
        fun pos(angleDeg: Float): Offset {
            val rad = Math.toRadians(angleDeg.toDouble())
            return Offset(
                cx + ringR * Math.cos(rad).toFloat(),
                cy + ringR * Math.sin(rad).toFloat()
            )
        }
        // Faint dotted ring underneath (12 dots, every 30 degrees).
        for (i in 0 until 12) {
            drawCircle(
                color = (if (palette.appTheme == AppTheme.ONYX) palette.accent
                    else lerp(palette.accent, palette.accent2, i / 11f)).copy(alpha = 0.22f),
                radius = 1.4f * unit,
                center = pos(i * 30f)
            )
        }
        // Tail behind the head: evenly spaced on the same circle,
        // shrinking and fading (B1's exact sample values).
        val tailR = listOf(2.5f, 2.05f, 1.65f, 1.3f)
        val tailA = listOf(0.75f, 0.55f, 0.38f, 0.24f)
        for (i in 4 downTo 1) {
            drawCircle(
                color = (if (palette.appTheme == AppTheme.ONYX) palette.accent
                    else lerp(palette.accent, palette.accent2, i / 4f)).copy(alpha = tailA[i - 1]),
                radius = tailR[i - 1] * unit,
                center = pos(rotation - 35f * i)
            )
        }
        // Head with its soft glow.
        val head = pos(rotation)
        drawCircle(color = palette.accent.copy(alpha = 0.28f), radius = 6f * unit, center = head)
        drawCircle(color = palette.accent, radius = 3.1f * unit, center = head)
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
    loadingMore: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    resumeProgress: Float? = null
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
                OrbitDots(modifier = Modifier.size(28.dp))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
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
                    // Resume progress bar
                    if (resumeProgress != null && resumeProgress > 0) {
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(
                                    Color.White.copy(alpha = 0.3f),
                                    RoundedCornerShape(2.dp)
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(resumeProgress.coerceIn(0f, 1f))
                                    .background(Color.White, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
        if (loadingMore && !isLoading) {
            // Streams are still arriving: the button is already playable,
            // and our signature dots circle at its side while more come
            // (owner's B1 Orbit pick, 2026-10-24 \u2014 replaces the plain
            // little circle, same place at the button's right side).
            OrbitDots(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .size(26.dp)
            )
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
            .sizeIn(minHeight = 52.dp)
            .alpha(if (enabled) 1f else 0.45f)
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
