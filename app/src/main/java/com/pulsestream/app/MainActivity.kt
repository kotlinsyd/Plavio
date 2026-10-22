package com.pulsestream.app

import android.app.PictureInPictureParams
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.withContext
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.AddonShare
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.PlayerChoice
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.notifications.EpisodeNotifications
import com.pulsestream.app.data.notifications.NotificationNav
import com.pulsestream.app.util.ExternalPlayer
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.components.CrashReportDialog
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.components.UpdatePrompt
import com.pulsestream.app.ui.nav.Routes
import com.pulsestream.app.ui.screens.LiveScreen
import com.pulsestream.app.ui.screens.OnboardingScreen
import com.pulsestream.app.ui.screens.SplashScreen
import com.pulsestream.app.ui.screens.SettingsScreen
import com.pulsestream.app.ui.screens.AccountScreen
import com.pulsestream.app.ui.screens.AddonsScreen
import com.pulsestream.app.ui.screens.CatalogScreen
import com.pulsestream.app.ui.screens.DetailScreen
import com.pulsestream.app.ui.screens.FeedbackScreen
import com.pulsestream.app.ui.screens.DownloadsScreen
import com.pulsestream.app.ui.screens.HomeCatalogsScreen
import com.pulsestream.app.ui.screens.PlaybackSettingsScreen
import com.pulsestream.app.ui.screens.ActorScreen
import com.pulsestream.app.ui.screens.StreamsSettingsScreen
import com.pulsestream.app.ui.screens.SubtitleSettingsScreen
import com.pulsestream.app.ui.screens.AppearanceSettingsScreen
import com.pulsestream.app.ui.screens.AboutScreen
import com.pulsestream.app.ui.screens.LicensesScreen
import com.pulsestream.app.ui.screens.HomeScreen
import com.pulsestream.app.ui.screens.PlayerScreen
import com.pulsestream.app.ui.screens.SearchScreen
import com.pulsestream.app.ui.components.MiniTrailerOverlay
import com.pulsestream.app.ui.components.TrailerMiniController
import com.pulsestream.app.ui.components.TrailerPlayerState
import com.pulsestream.app.ui.screens.TrailerPlayerScreen
import com.pulsestream.app.ui.screens.WatchlistScreen
import com.pulsestream.app.ui.screens.ListsScreen
import com.pulsestream.app.ui.screens.ListDetailScreen
import com.pulsestream.app.ui.screens.BackupScreen
import com.pulsestream.app.ui.screens.IntegrationsScreen
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.PulseStreamTheme
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.theme.paletteFor
import com.pulsestream.app.ui.tv.TvAddonSetupScreen
import com.pulsestream.app.ui.tv.TvBottomBar
import com.pulsestream.app.ui.tv.rememberIsTv
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.pulsestream.app.ui.tv.tvFocusRing

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * True while the player screen is in the composition (set by
         * PlayerScreen's DisposableEffect). Used by onUserLeaveHint to
         * auto-enter PiP only when video is actually playing.
         */
        @Volatile
        var isPlayerActive: Boolean = false

        /**
         * TV remote: Left/Right while the player is open. Value = seek delta
         * in ms (negative = back). PlayerScreen should collect this and call
         * player.seekTo(). (Play/pause reuses [pipToggleRequest].)
         */
        val playerSeekRequest = MutableSharedFlow<Long>(extraBufferCapacity = 4)

        /**
         * Current PiP mode, updated in onPictureInPictureModeChanged.
         * PlayerScreen collects this to hide its overlay UI while floating.
         */
        val pipMode = MutableStateFlow(false)

        /** Broadcast action for the PiP window's play/pause RemoteAction. */
        const val ACTION_PIP_TOGGLE = "com.pulsestream.app.ACTION_PIP_TOGGLE"

        /**
         * Intent extra carrying a nav route (e.g. a detail page) requested
         * by a notification tap. MainActivity stashes it on
         * [com.pulsestream.app.data.notifications.NotificationNav]; PulseNav
         * navigates there once past splash/onboarding.
         */
        const val EXTRA_OPEN_ROUTE = "com.pulsestream.app.OPEN_ROUTE"

        /**
         * Last known video dimensions, kept current by PlayerScreen's
         * player listener. Used for the PiP aspect ratio when the user
         * presses Home (onUserLeaveHint has no player access).
         */
        @Volatile
        var pipVideoW: Int = 16

        @Volatile
        var pipVideoH: Int = 9

        /**
         * Last known playing state, kept current by PlayerScreen's player
         * listener so the PiP RemoteAction shows the right icon.
         */
        @Volatile
        var pipIsPlaying: Boolean = true

        /**
         * Fired by the PiP play/pause RemoteAction's broadcast receiver.
         * PlayerScreen collects this and toggles the ExoPlayer.
         */
        val pipToggleRequest = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /**
         * PiP windows only support aspect ratios roughly between 1:2.39 and
         * 2.39:1 — clamp anything wider/taller (e.g. a 9:16 portrait video
         * stays portrait, an extreme pano gets clamped, never crashes).
         */
        fun clampPipRatio(w: Int, h: Int): Rational {
            if (w <= 0 || h <= 0) return Rational(16, 9)
            val r = w.toFloat() / h.toFloat()
            return when {
                r > 2.39f -> Rational(239, 100)
                r < 1f / 2.39f -> Rational(100, 239)
                else -> Rational(w, h)
            }
        }

        /**
         * Builds PiP params with the video's (clamped) aspect ratio and a
         * play/pause RemoteAction so the floating window has transport
         * controls like YouTube's PiP.
         */
        fun buildPipParams(context: Context, playing: Boolean): PictureInPictureParams {
            val toggleIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_PIP_TOGGLE).setPackage(context.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = if (playing) {
                RemoteAction(
                    Icon.createWithResource(context, android.R.drawable.ic_media_pause),
                    "Pause",
                    "Pause video",
                    toggleIntent
                )
            } else {
                RemoteAction(
                    Icon.createWithResource(context, android.R.drawable.ic_media_play),
                    "Play",
                    "Play video",
                    toggleIntent
                )
            }
            return PictureInPictureParams.Builder()
                .setAspectRatio(clampPipRatio(pipVideoW, pipVideoH))
                .setActions(listOf(action))
                .build()
        }
    }

    /**
     * Receives the PiP window's play/pause RemoteAction taps and forwards
     * them to PlayerScreen via [pipToggleRequest].
     */
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PIP_TOGGLE) {
                pipToggleRequest.tryEmit(Unit)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAddonDeepLink(intent)
        handleNotificationRoute(intent)
        // Safety net: (re)schedule the episode check on cold start.
        // Idempotent (KEEP) and a no-op when alerts are disabled.
        EpisodeNotifications.ensureScheduled(this)
        // PiP play/pause RemoteAction receiver (explicit intent, not exported).
        val pipFilter = IntentFilter(ACTION_PIP_TOGGLE)
        ContextCompat.registerReceiver(this, pipReceiver, pipFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent {
            val app = applicationContext as PulseApp
            // If the last run crashed, show the log with a Copy button.
            var crashReport by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(Unit) {
                crashReport = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    app.readCrashReport()
                }
            }
            if (crashReport != null) {
                CrashReportDialog(
                    report = crashReport!!,
                    onDismiss = {
                        app.clearCrashReport()
                        crashReport = null
                    }
                )
            }
            // Theme state: bumped whenever Settings changes accent/AMOLED so the
            // whole app re-skins without restarting the activity.
            var themeTick by remember { mutableIntStateOf(0) }
            val palette = remember(themeTick) {
                paletteFor(
                    AccentTheme.byName(app.settings.accentName),
                    app.settings.amoledBlack
                )
            }
            val isLight = remember(themeTick) { app.settings.themeMode == "light" }
            PulseStreamTheme(palette = palette, isLight = isLight) {
                PulseNav(onThemeChanged = { themeTick++ })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAddonDeepLink(intent)
        handleNotificationRoute(intent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipReceiver) }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        // Auto-backup to Plavio Cloud when app goes to background (if logged in)
        // Note: CloudManager needs settings/repository; auto-backup is handled
        // via the AccountScreen's lifecycle instead to avoid circular dependencies.
    }

    /**
     * Refreshes the PiP window's play/pause RemoteAction icon. Called by
     * PlayerScreen's player listener on every play-state change; the params
     * only take effect while the video is actually floating.
     */
    fun updatePipActions(playing: Boolean) {
        pipIsPlaying = playing
        if (isInPictureInPictureMode) {
            setPictureInPictureParams(buildPipParams(this, playing))
        }
    }

    /**
     * Auto-enter Picture-in-Picture when the user presses Home/recents while
     * a video is playing (v0.9.0+). Only fires for explicit user leave, not
     * for in-app navigation, and honors the pipEnabled setting.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!isPlayerActive) return
        val app = applicationContext as PulseApp
        if (!app.settings.pipEnabled) return
        enterPictureInPictureMode(buildPipParams(this, pipIsPlaying))
    }

    /**
     * Tracks PiP state so PlayerScreen can hide its overlay UI while the
     * video floats (the system draws its own PiP controls instead).
     * On entering PiP, (re)applies params so the play/pause RemoteAction
     * is present with the correct icon from the first frame.
     */
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            setPictureInPictureParams(buildPipParams(this, pipIsPlaying))
        }
    }

    /**
     * plavio-addons:// deep link (manifest VIEW intent-filter): stash the
     * bundle link on the repository. PulseNav navigates to the Addons tab and
     * AddonsScreen opens its import dialog pre-filled — both cold start and
     * while the app is already running funnel through here.
     */
    private fun handleAddonDeepLink(intent: Intent?) {
        if (intent == null) return
        val dataString = intent.dataString ?: return
        // stremio:// links (e.g. from "Install" buttons on addon pages):
        // convert to https:// manifest URL and add directly.
        if (dataString.startsWith("stremio://", ignoreCase = true)) {
            val manifestUrl = "https://" + dataString.removePrefix("stremio://").removePrefix("stremio://")
            // Handle encoded URLs: stremio://addon/<encoded-url>
            val cleanUrl = if (manifestUrl.contains("/addon/")) {
                try {
                    java.net.URLDecoder.decode(manifestUrl.substringAfter("/addon/"), "UTF-8")
                } catch (_: Exception) {
                    manifestUrl.substringAfter("/addon/")
                }
            } else {
                manifestUrl
            }
            if (cleanUrl.startsWith("https://")) {
                val app = applicationContext as PulseApp
                // Store as pending manifest URL for AddonsScreen to pick up
                app.repository.setPendingManifestUrl(cleanUrl)
                return
            }
        }
        // Validates the link actually carries a decodable bundle.
        if (AddonShare.bundleFromIntent(intent).isNullOrEmpty()) return
        val link = intent.dataString ?: return
        val app = applicationContext as PulseApp
        app.repository.setPendingAddonBundle(link)
    }

    /**
     * Notification tap (episode alert): the PendingIntent carries
     * [EXTRA_OPEN_ROUTE] with a detail-page route. Stash it on
     * NotificationNav; PulseNav navigates there once past
     * splash/onboarding — same pattern as the addon deep link above.
     * The extra is consumed (removed) so a later onNewIntent without a
     * route doesn't re-trigger the navigation.
     */
    private fun handleNotificationRoute(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_OPEN_ROUTE)?.takeIf { it.isNotBlank() }
            ?: return
        intent.removeExtra(EXTRA_OPEN_ROUTE)
        NotificationNav.pendingRoute.value = route
    }
}

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)
/**
 * One item in the floating dock (v0.12.7, owner's pick — sample A
 * "Glass Shrink"): the icon WITH its name under it, proper size and
 * even gaps. [labelFraction] is the tray's open amount (1 = open,
 * 0 = shrunk): as the tray shrinks on scroll-down the name is eaten —
 * its slot collapses and it fades and crops away — until only the
 * icon remains; scrolling up grows it back. The accent pill sits
 * behind the whole segment when selected.
 */
@Composable
private fun DockItem(
    tab: Tab,
    selected: Boolean,
    labelFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                palette.accent.copy(alpha = 0.32f),
                                palette.accent.copy(alpha = 0.12f)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
                } else {
                    Modifier
                }
            )
            // No ripple: the capsule highlight on the selected tab is the
            // feedback; a square ripple clashes with the pill shape.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 3.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (selected) palette.accent else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        // The name slot: eaten as the tray shrinks — height collapses
        // (driven by the same spring value as the capsule's own size),
        // the text fades and is cropped by the closing slot.
        Box(
            modifier = Modifier
                .height((15 * labelFraction).dp)
                .clipToBounds()
                .graphicsLayer { alpha = labelFraction },
            contentAlignment = Alignment.Center
        ) {
            Text(
                tab.label,
                // 10sp (v0.12.8): at labelSmall the longest name —
                // "Downloads" — clipped to "Downlo…" on-device; the
                // smaller size + tighter segment padding fits it whole.
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (selected) palette.accent else TextSecondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Bottom-dock tabs; labels come from string resources so they follow the app language. */
@Composable
private fun appTabs() = listOf(
    Tab(Routes.HOME, stringResource(R.string.nav_home), Icons.Filled.Home),
    Tab(Routes.LIVE, stringResource(R.string.nav_live), Icons.Filled.LiveTv),
    Tab(Routes.SEARCH, stringResource(R.string.nav_search), Icons.Filled.Search),
    Tab(Routes.DOWNLOADS, stringResource(R.string.nav_downloads), Icons.Filled.Download),
    Tab(Routes.SETTINGS, stringResource(R.string.nav_settings), Icons.Filled.Settings)
)

@Composable
private fun PulseNav(onThemeChanged: () -> Unit) {
    val systemMotionEnabled = remember { com.pulsestream.app.ui.screens.systemAnimationsEnabled() }
    val navController = rememberNavController()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val app = appContext as PulseApp
    val repository = remember { app.repository }
    val listsRepository = remember { CustomListsRepository(app.database) }
    // Activity context for the "Ask every time" player chooser dialog.
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    // In-app update check (GitLab releases) on every launch; silent when nothing new.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check(appContext)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isTv = rememberIsTv()

    // Mini floating trailer player (YouTube-style): one trailer at a time,
    // floats above all tabs. Mobile only — TV keeps the full player.
    val trailerMini = remember { TrailerMiniController() }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                trailerMini.pauseForBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Deep link: a plavio-addons:// link opened the app (cold start or while
    // running — MainActivity stashed it via handleAddonDeepLink). Jump to the
    // Addons tab; AddonsScreen picks up the pending bundle and opens its
    // import dialog pre-filled. Skipped on splash/onboarding so the startup
    // flow isn't hijacked — the effect refires once the real destination
    // lands. Same nav options as the dock (single top, state kept).
    val pendingBundle by repository.pendingAddonBundle.collectAsState()
    val pendingManifestUrl by repository.pendingManifestUrl.collectAsState()
    LaunchedEffect(pendingBundle, pendingManifestUrl, currentDestination) {
        val route = currentDestination?.route
        if ((pendingBundle != null || pendingManifestUrl != null) && route != null &&
            route != Routes.SPLASH && route != Routes.ONBOARDING
        ) {
            navController.navigate(Routes.ADDONS) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // Notification tap (episode alert): MainActivity stashed a detail route
    // via handleNotificationRoute. Navigate once past splash/onboarding,
    // then clear so it fires exactly once.
    val pendingRoute by NotificationNav.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute, currentDestination) {
        val route = pendingRoute
        val dest = currentDestination?.route
        if (route != null && dest != null &&
            dest != Routes.SPLASH && dest != Routes.ONBOARDING
        ) {
            NotificationNav.pendingRoute.value = null
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
    }

    if (isTv) {
        // TV: bottom-center nav bar (replaced the old left rail). The bar is
        // hidden on splash/onboarding/player, same as the rail was. Content
        // gets bottom padding so the bar never covers anything; the bar is
        // drawn below the content in a Column, not overlaid.
        val barHiddenRoutes = setOf(Routes.SPLASH, Routes.ONBOARDING, Routes.PLAYER)
        val destRoutes = currentDestination?.hierarchy?.mapNotNull { it.route }.orEmpty()
        val showBar = destRoutes.none { it in barHiddenRoutes }
        val barFocus = remember { FocusRequester() }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalPalette.current.background)
        ) {
            // Left-side Home button, vertically centered: jumps home and
            // parks focus on the bar.
            if (showBar) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(start = 12.dp, end = 8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = {
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            runCatching { barFocus.requestFocus() }
                        },
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.Home,
                            contentDescription = "Home",
                            tint = TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    PulseNavGraph(navController, repository, listsRepository, onThemeChanged)
                }
                if (showBar) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TvBottomBar(
                            currentDestination = currentDestination,
                            barFocus = barFocus,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    } else {
        val tabs = appTabs()
        val showBottomBar = tabs.any { it.route == currentDestination?.route }
        // Scroll-aware nav: compact (icons only) when scrolling down, like Nuvio.
        var compactNav by remember { mutableStateOf(false) }
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // available.y < 0 means scrolling down (content moving up).
                    // Hysteresis: only collapse on a clear downward scroll,
                    // only expand on a clear upward scroll — the nav stays
                    // minimized until the user scrolls up (no dancing).
                    if (available.y < -10) compactNav = true
                    else if (available.y > 10) compactNav = false
                    return Offset.Zero
                }
            }
        }
        Box(Modifier.fillMaxSize().nestedScroll(nestedScrollConnection)) {
            // Full-height content (v0.12.2; Scaffold removed v0.12.11):
            // even with no bottomBar, the Scaffold kept reserving the
            // bottom system inset for its content area — a solid strip
            // under the floating dock (owner's on-device note). A plain
            // Box now hosts the content: only the status-bar top inset
            // is applied (screens already expect exactly that — Home
            // shifts its list up by the same amount for the immersive
            // hero), and the content owns every remaining pixel down
            // to the physical bottom edge, scrolling under the dock.
            // Tab screens carry their own extra bottom list padding so
            // the last row can scroll clear of the capsule.
            val shellDensity = LocalDensity.current
            val shellStatusTop = with(shellDensity) {
                WindowInsets.statusBars.getTop(shellDensity).toDp()
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(LocalPalette.current.background)
                    .padding(top = shellStatusTop)
            ) {
                PulseNavGraph(navController, repository, listsRepository, onThemeChanged, trailerMini)
            }
        // Floating dock (v0.12.10, owner's order): no longer a
        // Scaffold bottomBar — the reserved solid strip at the
        // bottom is GONE. The screen's usable area runs the full
        // height and cards scroll underneath the capsule, which
        // floats over the content as an overlay. Tab screens
        // already carry ~104dp bottom list padding, so the last
        // row can still scroll clear of the capsule.
            if (showBottomBar) {
                // One spring drives the whole shrink (v0.12.7, owner's
                // pick: sample A "Glass Shrink"). A single Animatable
                // morphs the capsule's width + height a little — the
                // tray NEVER folds into a Home pill: scrolling down
                // shrinks it slightly from both sides and from
                // top/bottom and eats the tab names (icons stay);
                // scrolling up grows it back with the names. Softly
                // damped spring: gentle settle, no bounce jitter. The
                // hysteresis latch above (compactNav) owns the trigger.
                val foldAnim = remember { Animatable(0f) }
                LaunchedEffect(compactNav) {
                    foldAnim.animateTo(
                        targetValue = if (compactNav) 1f else 0f,
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 205f)
                    )
                }
                val fold = foldAnim.value
                // Seated at the very bottom edge (v0.12.15, owner's
                // correction): NO navigationBarsPadding — that inset
                // lifted the capsule off the bottom and left a dead
                // black band of screen below it. The capsule now sits
                // 6dp off the physical bottom edge, over the gesture
                // zone, so almost no screen is left under it — and
                // the list scrolls through what little remains.
                BoxWithConstraints(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // End states (v0.12.7): open = nearly the FULL
                    // width (only the 16dp side gaps), 62dp tall, five
                    // tabs with icon + name spread evenly; shrunk = 85%
                    // of that width, 47dp tall, names eaten, icons only.
                    val capsuleWidth = lerp(maxWidth, maxWidth * 0.85f, fold)
                    val capsuleHeight = lerp(62.dp, 47.dp, fold)
                    // Capsule-fitted black backing (v0.12.9; RESTORED
                    // v0.12.15 on the owner's correction): a black pill
                    // EXACTLY the capsule's size and shape sits directly
                    // behind it — same animated width/height, so it
                    // shrinks and grows in lockstep and never shows
                    // outside the capsule at all. The v0.12.13 removal
                    // was a misread: the owner's "solid black" was
                    // never this pill — it is the dead screen space
                    // BELOW the capsule, fixed in v0.12.15 by seating
                    // the dock at the very bottom edge.
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(capsuleWidth)
                            .height(capsuleHeight)
                            .background(Color.Black, RoundedCornerShape(50))
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Transparent,
                        shadowElevation = 0.dp,
                        modifier = Modifier
                            .width(capsuleWidth)
                            .height(capsuleHeight)
                    ) {
                        Row(
                            // Liquid-glass dock: sheen + accent glow + specular rim.
                            // v0.12.7 (owner's order): LIGHT tinted glass
                            // with blur — darken nearly gone (0.03), frost
                            // up (0.12) — over the fitted black backing,
                            // so the buttons show properly. Other glass untouched.
                            modifier = Modifier
                                .liquidGlass(RoundedCornerShape(50), frost = 0.12f, darken = 0.03f)
                                .fillMaxSize()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { tab ->
                                val selected =
                                    currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                val isHome = tab.route == Routes.HOME
                                // All five tabs stay, always (v0.12.7):
                                // the shrink only eats the names — no tab
                                // ever melts away or folds into a pill.
                                DockItem(
                                    tab = tab,
                                    selected = selected,
                                    labelFraction = 1f - fold,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                        // Home (v0.12.4): tapping Home
                                        // lands on Home scrolled to the
                                        // very top — from any other tab,
                                        // and when already on Home.
                                        if (isHome) {
                                            com.pulsestream.app.ui.screens.HomeScrollSignal
                                                .requestScrollToTop()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        // Full portrait trailer layer — floats above the current tab (which
        // stays mounted behind it). Slides/fades up on open, collapses down
        // into the mini player on swipe-down/back.
        AnimatedVisibility(
            visible = trailerMini.playerState == TrailerPlayerState.Full,
            enter = if (systemMotionEnabled) slideInVertically(
                initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.88f, stiffness = 450f)
            ) + fadeIn(tween(280)) else androidx.compose.animation.EnterTransition.None,
            exit = if (systemMotionEnabled) slideOutVertically(
                targetOffsetY = { it }, animationSpec = tween(380, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(300)) else androidx.compose.animation.ExitTransition.None
        ) {
            val req = trailerMini.request
            val player = trailerMini.player
            if (req != null && player != null) {
                TrailerPlayerScreen(
                    repository = repository,
                    request = req,
                    externalPlayer = player,
                    onBack = { trailerMini.minimize() },
                    onSwipeDown = { trailerMini.minimize() }
                )
            }
        }
        // Mini floating trailer player, above content and dock.
        AnimatedVisibility(
            visible = trailerMini.playerState == TrailerPlayerState.Mini,
            enter = if (systemMotionEnabled) fadeIn(tween(220, delayMillis = 120)) + scaleIn(
                initialScale = 0.6f, transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(300, delayMillis = 120, easing = FastOutSlowInEasing)
            ) else androidx.compose.animation.EnterTransition.None,
            exit = if (systemMotionEnabled) fadeOut(tween(200)) + scaleOut(
                targetScale = 0.6f, transformOrigin = TransformOrigin(1f, 1f),
                animationSpec = tween(200, easing = FastOutSlowInEasing)
            ) else androidx.compose.animation.ExitTransition.None
        ) {
            MiniTrailerOverlay(
                controller = trailerMini,
                onExpand = { trailerMini.expand() }
            )
        }
        } // end Box wrapping mobile Scaffold
    } // end else (mobile bottom dock)

    // Update popup, shown over whatever screen is visible (both form factors).
    UpdatePrompt(
        info = updateInfo,
        onDismiss = { updateInfo = null }
    )
}

@Composable
private fun PulseNavGraph(
    navController: NavHostController,
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onThemeChanged: () -> Unit,
    trailerMini: TrailerMiniController? = null
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity

    val navScope = rememberCoroutineScope()
    fun openResolvedDetail(addonId: String, type: String, metaId: String) {
        navScope.launch {
            val target = com.pulsestream.app.ui.screens.resolveDetailTarget(repository, addonId, type, metaId)
            navController.navigate(Routes.detail(target.addonId, target.type, target.metaId))
        }
    }

    /**
     * Single choke point for all playback. Honors Settings > Playback >
     * "Preferred player". Built-in behavior is unchanged; external/ask modes
     * only divert when the user chose them.
     */
    fun playRequest(req: PlaybackRequest) {
        when (PlayerChoice.fromName(repository.settings.preferredPlayer)) {
            PlayerChoice.BUILT_IN -> {
                repository.pendingPlayback = req
                navController.navigate(Routes.PLAYER)
            }
            PlayerChoice.EXTERNAL -> {
                // Toast shown inside on failure; fall back to built-in.
                if (!ExternalPlayer.play(activity ?: appContext, req)) {
                    repository.pendingPlayback = req
                    navController.navigate(Routes.PLAYER)
                }
            }
            PlayerChoice.ASK -> {
                val act = activity
                if (act == null || act.isFinishing) {
                    repository.pendingPlayback = req
                    navController.navigate(Routes.PLAYER)
                } else {
                    android.app.AlertDialog.Builder(act)
                        .setTitle("Play with")
                        .setItems(arrayOf("Built-in player", "External player")) { _, which ->
                            if (which == 1 && ExternalPlayer.play(act, req)) return@setItems
                            repository.pendingPlayback = req
                            navController.navigate(Routes.PLAYER)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            // Warm the home catalog cache while the splash animation
            // plays — by the time Home appears, content is ready.
            // Fire-and-forget: never blocks or extends the animation.
            LaunchedEffect(Unit) {
                launch { runCatching { repository.refreshCatalogRows() } }
                launch { runCatching { repository.pruneOversizedCache() } }
            }
            SplashScreen(
                onDone = {
                    val next = if (repository.settings.onboardingDone) {
                        Routes.HOME
                    } else {
                        Routes.ONBOARDING
                    }
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                repository = repository,
                onDone = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                repository = repository,
                listsRepository = listsRepository,
                onOpenDetail = { addonId, type, metaId ->
                    openResolvedDetail(addonId, type, metaId)
                },
                onResumePlayback = {
                    repository.pendingPlayback?.let { playRequest(it) }
                },
                onGoToAddons = { navController.navigate(Routes.ADDONS) },
                onGoToWatchlist = { navController.navigate(Routes.WATCHLIST) },
                onGoToLists = { navController.navigate(Routes.LISTS) },
                onOpenCatalog = { row ->
                    repository.pendingCatalog = row
                    navController.navigate(
                        Routes.catalog(row.addonId, row.type, row.catalogId)
                    )
                },
                onGoToDownloads = { navController.navigate(Routes.DOWNLOADS) },
            )
        }
        composable(Routes.LIVE) {
            LiveScreen(
                repository = repository,
                onPlay = { req ->
                    // #3: the Live tab builds the PlaybackRequest — it must be
                    // handed to the player, not dropped.
                    playRequest(req)
                },
                onGoToAddons = { navController.navigate(Routes.ADDONS) }
            )
        }
        composable(Routes.TRAILER_PLAYER) {
            TrailerPlayerScreen(
                repository = repository,
                externalPlayer = trailerMini?.player,
                onBack = {
                    // YouTube behavior: back minimizes to the floating player.
                    trailerMini?.minimize()
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.SEARCH) {
        SearchScreen(
            repository = repository,
            onOpenDetail = { addonId, type, metaId ->
                openResolvedDetail(addonId, type, metaId)
            }
        )
        }
        composable(Routes.WATCHLIST) {
            WatchlistScreen(
                repository = repository,
                onOpenDetail = { addonId, type, metaId ->
                    openResolvedDetail(addonId, type, metaId)
                }
            )
        }
        composable(Routes.LISTS) {
            ListsScreen(
                listsRepository = listsRepository,
                onOpenList = { listId ->
                    navController.navigate(Routes.listDetail(listId))
                }
            )
        }
        composable(
            Routes.LIST_DETAIL,
            arguments = listOf(
                navArgument("listId") { type = NavType.StringType }
            )
        ) { entry ->
            ListDetailScreen(
                listsRepository = listsRepository,
                listId = entry.arguments!!.getString("listId")!!,
                onBack = { navController.popBackStack() },
                onOpenDetail = { addonId, type, metaId ->
                    openResolvedDetail(addonId, type, metaId)
                }
            )
        }
        composable(Routes.ADDONS) {
            AddonsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onGoToTvSetup = { navController.navigate(Routes.TV_ADDON_SETUP) }
            )
        }
        composable(Routes.TV_ADDON_SETUP) {
            TvAddonSetupScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.HOME_CATALOGS) {
            HomeCatalogsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onThemeChanged = onThemeChanged,
                onGoToAddons = { navController.navigate(Routes.ADDONS) },
                onGoToHomeCatalogs = { navController.navigate(Routes.HOME_CATALOGS) },
                onGoToPlayback = { navController.navigate(Routes.PLAYBACK_SETTINGS) },
                onGoToSubtitles = { navController.navigate(Routes.SUBTITLE_SETTINGS) },
                onGoToAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                onGoToStreams = { navController.navigate(Routes.STREAMS_SETTINGS) },
                onGoToAbout = { navController.navigate(Routes.ABOUT) },
                onGoToFeedback = { navController.navigate(Routes.FEEDBACK) },
                onGoToAccount = { navController.navigate(Routes.ACCOUNT) },
                onGoToIntegrations = { navController.navigate(Routes.INTEGRATIONS) }
            )
        }
        composable(Routes.INTEGRATIONS) {
            IntegrationsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ACCOUNT) {
            AccountScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.PLAYBACK_SETTINGS) {
            PlaybackSettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.SUBTITLE_SETTINGS) {
            SubtitleSettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.APPEARANCE_SETTINGS) {
            AppearanceSettingsScreen(
                repository = repository,
                onThemeChanged = onThemeChanged,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.STREAMS_SETTINGS) {
            StreamsSettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onGoToLicenses = { navController.navigate(Routes.LICENSES) }
            )
        }
        composable(Routes.FEEDBACK) {
            FeedbackScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.DOWNLOADS) {
            DownloadsScreen(
                onBack = { navController.popBackStack() },
                onPlayFile = { filePath, title ->
                    val file = java.io.File(filePath)
                    if (!file.exists() || !file.canRead()) {
                        android.widget.Toast.makeText(
                            appContext,
                            "Downloaded file not found \u2014 it may have been deleted.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } else {
                        playRequest(
                            PlaybackRequest(
                                streamUrl = "file://$filePath",
                                title = title,
                                poster = null,
                                videoId = "download_${filePath.hashCode()}",
                                isMovie = true
                            )
                        )
                    }
                }
            )
        }
        composable(Routes.LICENSES) {
            LicensesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.DETAIL,
            arguments = listOf(
                navArgument("addonId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("metaId") { type = NavType.StringType }
            )
        ) { entry ->
            val trailerCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
            DetailScreen(
                repository = repository,
                listsRepository = listsRepository,
                addonId = Routes.dec(entry.arguments!!.getString("addonId")!!),
                type = Routes.dec(entry.arguments!!.getString("type")!!),
                metaId = Routes.dec(entry.arguments!!.getString("metaId")!!),
                onBack = { navController.popBackStack() },
                onPlay = {
                    // startPlayback() stashed the request; honor the
                    // preferred-player setting instead of always navigating.
                    val req = repository.pendingPlayback
                    if (req != null) playRequest(req)
                    else navController.navigate(Routes.PLAYER)
                },
                onPlayTrailer = { req ->
                    repository.pendingPlayback = req
                    // Mini floating player (mobile); TV falls back to the full player.
                    val mini = trailerMini
                    if (mini != null) {
                        mini.play(trailerCtx, req)
                    } else {
                        navController.navigate(Routes.TRAILER_PLAYER)
                    }
                },
                onOpenDetail = { relatedAddonId, relatedType, relatedMetaId ->
                    openResolvedDetail(relatedAddonId, relatedType, relatedMetaId)
                },
                onOpenActor = { personId ->
                    navController.navigate("actor/$personId")
                }
            )
        }
        composable(
            Routes.ACTOR,
            arguments = listOf(
                androidx.navigation.navArgument("personId") {
                    type = androidx.navigation.NavType.IntType
                }
            )
        ) { entry ->
            ActorScreen(
                repository = repository,
                personId = entry.arguments!!.getInt("personId"),
                onBack = { navController.popBackStack() },
                onOpenDetail = { type, metaId ->
                    // PlavioMeta addon id — detail resolves meta/streams from
                    // any capable addon via the base-URL hint chain.
                    openResolvedDetail("community.plaviometa", type, metaId)
                }
            )
        }
        composable(Routes.PLAYER) {
            PlayerScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            Routes.CATALOG,
            arguments = listOf(
                navArgument("addonId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("catalogId") { type = NavType.StringType }
            )
        ) {
            CatalogScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onOpenDetail = { addonId, type, metaId ->
                    openResolvedDetail(addonId, type, metaId)
                }
            )
        }
    }
}
