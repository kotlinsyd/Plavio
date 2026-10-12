package com.pulsestream.app.ui.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextSecondary

/** Tabs shared by the TV bottom bar and the mobile dock. */
data class TvTab(val route: String, val label: String, val icon: ImageVector)

val tvTabs = listOf(
    TvTab("home", "Home", Icons.Filled.Home),
    TvTab("live", "Live", Icons.Filled.LiveTv),
    TvTab("search", "Search", Icons.Filled.Search),
    TvTab("settings", "Settings", Icons.Filled.Settings)
)

/**
 * Bottom-center navigation bar (replaces the old left rail).
 *
 * TV: D-pad Left/Right moves between items in row order (default 2D focus
 * search — first/last items wrap nowhere, focus simply stops, never lost).
 * Up from the bar reaches the content above; Down from content lands here.
 * The focused item gets the unmissable green tvFocusRing; the active page
 * gets the accent pill. On touch devices it behaves as a normal tab bar.
 *
 * [barFocus] lets the top-left Home button jump focus straight here.
 */
@Composable
fun TvBottomBar(
    currentDestination: NavDestination?,
    onNavigate: (String) -> Unit,
    barFocus: FocusRequester,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Box(
        modifier = modifier
            .padding(bottom = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = palette.panel.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, GlassBorder),
            shadowElevation = 10.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tvTabs.forEachIndexed { index, tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { onNavigate(tab.route) },
                        modifier = if (index == 0) Modifier.focusRequester(barFocus) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: TvTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(50)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) palette.accent.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .tvFocusRing(shape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Focused-but-not-selected tint is handled by tvFocusRing's green
        // ring + tint; the icon/label just needs the selected accent.
        Icon(
            tab.icon,
            contentDescription = tab.label,
            tint = if (selected) palette.accent else TextSecondary,
            modifier = Modifier.size(26.dp)
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
