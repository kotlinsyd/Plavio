package com.pulsestream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.withContext
import com.pulsestream.app.data.update.UpdateChecker
import com.pulsestream.app.data.update.UpdateInfo
import com.pulsestream.app.ui.components.CrashReportDialog
import com.pulsestream.app.ui.components.UpdatePrompt
import com.pulsestream.app.ui.nav.Routes
import com.pulsestream.app.ui.screens.LiveScreen
import com.pulsestream.app.ui.screens.SplashScreen
import com.pulsestream.app.ui.screens.SettingsScreen
import com.pulsestream.app.ui.screens.AccountScreen
import com.pulsestream.app.ui.screens.AddonsScreen
import com.pulsestream.app.ui.screens.CatalogScreen
import com.pulsestream.app.ui.screens.DetailScreen
import com.pulsestream.app.ui.screens.HomeCatalogsScreen
import com.pulsestream.app.ui.screens.PlaybackSettingsScreen
import com.pulsestream.app.ui.screens.AppearanceSettingsScreen
import com.pulsestream.app.ui.screens.AboutScreen
import com.pulsestream.app.ui.screens.LicensesScreen
import com.pulsestream.app.ui.screens.HomeScreen
import com.pulsestream.app.ui.screens.PlayerScreen
import com.pulsestream.app.ui.screens.SearchScreen
import com.pulsestream.app.ui.screens.WatchlistScreen
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.PulseStreamTheme
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.theme.paletteFor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            PulseStreamTheme(palette = palette) {
                PulseNav(onThemeChanged = { themeTick++ })
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

/** One item in the floating dock: icon + tiny label, accent-glowed when selected. */
@Composable
private fun DockItem(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) palette.accent.copy(alpha = 0.18f) else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (selected) palette.accent else TextSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.accent else TextSecondary.copy(alpha = 0.85f),
            maxLines = 1
        )
    }
}

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.LIVE, "Live", Icons.Filled.LiveTv),
    Tab(Routes.SEARCH, "Search", Icons.Filled.Search),
    Tab(Routes.SETTINGS, "Settings", Icons.Filled.Settings)
)

@Composable
private fun PulseNav(onThemeChanged: () -> Unit) {
    val navController = rememberNavController()
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val app = appContext as PulseApp
    val repository = remember { app.repository }

    // In-app update check (GitLab releases) on every launch; silent when nothing new.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        updateInfo = UpdateChecker.check(appContext)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = tabs.any { it.route == currentDestination?.route }

    Scaffold(
        containerColor = LocalPalette.current.background,
        bottomBar = {
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = LocalPalette.current.panel.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, GlassBorder),
                        shadowElevation = 10.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            tabs.forEach { tab ->
                                val selected =
                                    currentDestination?.hierarchy?.any { it.route == tab.route } == true
                                DockItem(
                                    tab = tab,
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
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
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
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
                            navController.navigate(Routes.HOME) {
                                popUpTo(Routes.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.HOME) {
                    HomeScreen(
                        repository = repository,
                        onOpenDetail = { addonId, type, metaId ->
                            navController.navigate(Routes.detail(addonId, type, metaId))
                        },
                        onResumePlayback = {
                            navController.navigate(Routes.PLAYER)
                        },
                        onGoToAddons = { navController.navigate(Routes.ADDONS) },
                        onGoToWatchlist = { navController.navigate(Routes.WATCHLIST) },
                        onOpenCatalog = { row ->
                            repository.pendingCatalog = row
                            navController.navigate(
                                Routes.catalog(row.addonId, row.type, row.catalogId)
                            )
                        }
                    )
                }
                composable(Routes.LIVE) {
                    LiveScreen(
                        repository = repository,
                        onPlay = { navController.navigate(Routes.PLAYER) },
                        onGoToAddons = { navController.navigate(Routes.ADDONS) }
                    )
                }
                composable(Routes.SEARCH) {
                    SearchScreen(
                        repository = repository,
                        onOpenDetail = { addonId, type, metaId ->
                            navController.navigate(Routes.detail(addonId, type, metaId))
                        }
                    )
                }
                composable(Routes.WATCHLIST) {
                    WatchlistScreen(
                        repository = repository,
                        onOpenDetail = { addonId, type, metaId ->
                            navController.navigate(Routes.detail(addonId, type, metaId))
                        }
                    )
                }
                composable(Routes.ADDONS) {
                    AddonsScreen(
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
                        onGoToAppearance = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                        onGoToAbout = { navController.navigate(Routes.ABOUT) },
                        onGoToAccount = { navController.navigate(Routes.ACCOUNT) }
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
                composable(Routes.APPEARANCE_SETTINGS) {
                    AppearanceSettingsScreen(
                        repository = repository,
                        onThemeChanged = onThemeChanged,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(Routes.ABOUT) {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onGoToLicenses = { navController.navigate(Routes.LICENSES) }
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
                    DetailScreen(
                        repository = repository,
                        addonId = Routes.dec(entry.arguments!!.getString("addonId")!!),
                        type = Routes.dec(entry.arguments!!.getString("type")!!),
                        metaId = Routes.dec(entry.arguments!!.getString("metaId")!!),
                        onBack = { navController.popBackStack() },
                        onPlay = { navController.navigate(Routes.PLAYER) }
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
                            navController.navigate(Routes.detail(addonId, type, metaId))
                        }
                    )
                }
            }
        }

        // Update popup, shown over whatever screen is visible.
        UpdatePrompt(
            info = updateInfo,
            onDismiss = { updateInfo = null }
        )
    }
}
