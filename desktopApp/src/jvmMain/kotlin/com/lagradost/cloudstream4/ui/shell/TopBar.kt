package com.lagradost.cloudstream4.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.downloads.DesktopDownloadManager
import com.lagradost.cloudstream4.downloads.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType

data class ShiopaNavTab(
    val title: String,
    val icon: String,
    val destination: NavDestination,
    val badgeCount: Int = 0
)

@Composable
fun DesktopTopBar(
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentDest = NavigationManager.currentDestination
    val canGoBack = NavigationManager.canGoBack
    var showAccountDialog by remember { mutableStateOf(false) }

    // Observe reactive plugin manager state
    val pluginsVersion by DesktopPluginManager.pluginsState.collectAsState()
    val activeProvidersCount = APIHolder.allProviders.size
    val activeDownloadsCount = DesktopDownloadManager.tasks.count { it.state == DownloadState.DOWNLOADING }

    val navTabs = listOf(
        ShiopaNavTab("Home", "⌂", NavDestination.Home),
        ShiopaNavTab("Explore", "✦", NavDestination.Discover()),
        ShiopaNavTab("Watchlist", "▤", NavDestination.Bookmarks),
        ShiopaNavTab("Extensions", "❖", NavDestination.Extensions, badgeCount = activeProvidersCount),
        ShiopaNavTab("Downloads", "⤓", NavDestination.Downloads, badgeCount = activeDownloadsCount),
        ShiopaNavTab("History", "◔", NavDestination.History)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color(0xF2090A10),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShiopaStyle.GlassBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isCompact) ShiopaSpacing.Lg else ShiopaSpacing.Xl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isCompact) Arrangement.spacedBy(ShiopaSpacing.Lg) else Arrangement.SpaceBetween
        ) {
            // ----------------------------------------------------------
            // Left: Brand (keep CloudStream recognizable) + Back
            // ----------------------------------------------------------
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerMd)
                        .clickable { NavigationManager.navigate(NavDestination.Home) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = ShiopaSpacing.Sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(ShiopaStyle.CornerSm)
                            .background(
                                Brush.linearGradient(
                                    listOf(ShiopaStyle.AccentMain, ShiopaStyle.AccentMainBright)
                                )
                            )
                            .shadow(6.dp, ShiopaStyle.CornerSm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CLOUD",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            letterSpacing = 1.2.sp,
                            color = Color.White
                        )
                        Text(
                            text = "STREAM",
                            fontWeight = FontWeight.Normal,
                            fontSize = 15.sp,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFFA5A5BA)
                        )
                    }
                }

                if (canGoBack) {
                    Spacer(modifier = Modifier.width(ShiopaSpacing.Md))
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.CornerMd)
                            .background(ShiopaStyle.SurfaceCard)
                            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
                            .clickable { NavigationManager.goBack() }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "‹",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "Back",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // ----------------------------------------------------------
            // Center: Navigation — current section obvious via accent
            // ----------------------------------------------------------
            if (!isCompact) {
                Row(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .clip(ShiopaStyle.CornerLg)
                        .background(ShiopaStyle.SurfaceCard)
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (tab in navTabs) {
                        val isSelected = when (tab.destination) {
                            is NavDestination.Home -> currentDest is NavDestination.Home
                            is NavDestination.Discover -> currentDest is NavDestination.Discover
                            is NavDestination.Bookmarks -> currentDest is NavDestination.Bookmarks
                            is NavDestination.Extensions -> currentDest is NavDestination.Extensions
                            is NavDestination.Downloads -> currentDest is NavDestination.Downloads
                            is NavDestination.History -> currentDest is NavDestination.History
                            else -> false
                        }

                        val bgColor by animateColorAsState(
                            if (isSelected) ShiopaStyle.AccentMain.copy(alpha = 0.16f) else Color.Transparent
                        )
                        val textColor by animateColorAsState(
                            if (isSelected) Color.White else ShiopaStyle.TextMuted
                        )

                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.CornerMd)
                                .background(bgColor)
                                .clickable { NavigationManager.navigate(tab.destination) }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Accent underline dot marks the active section
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(ShiopaStyle.AccentMain)
                                    )
                                }
                                Text(
                                    text = tab.title,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = textColor
                                )

                                if (tab.badgeCount > 0 && tab.title == "Downloads") {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(ShiopaStyle.AccentMain)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = tab.badgeCount.toString(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ----------------------------------------------------------
            // Right: Search (roomy) · Providers · Profile · Settings
            // ----------------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
            ) {
                // Search field — fixed roomy width, accent focus ring
                if (!isCompact) {
                    Row(
                        modifier = Modifier
                            .width(230.dp)
                            .clip(ShiopaStyle.CornerMd)
                            .background(ShiopaStyle.SurfaceCard)
                            .border(
                                1.dp,
                                ShiopaStyle.GlassBorder,
                                ShiopaStyle.CornerMd
                            )
                            .clickable { NavigationManager.navigate(NavDestination.Search()) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = ShiopaSpacing.Md, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⌕",
                            fontSize = 14.sp,
                            color = ShiopaStyle.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Search movies, series…",
                            fontSize = 12.sp,
                            color = ShiopaStyle.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.CornerSm)
                                .background(Color(0xFF222336))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Ctrl+K",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = ShiopaStyle.TextSecondary
                            )
                        }
                    }

                    // Active provider count — compact chip, navigates to Extensions
                    Row(
                        modifier = Modifier
                            .clip(ShiopaStyle.CornerMd)
                            .background(ShiopaStyle.SurfaceCard)
                            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
                            .clickable { NavigationManager.navigate(NavDestination.Extensions) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(ShiopaStyle.AccentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$activeProvidersCount",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ShiopaStyle.TextSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Providers",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = ShiopaStyle.TextMuted
                        )
                    }
                } else {
                    // Compact Search Icon
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ShiopaStyle.SurfaceCard)
                            .border(1.dp, ShiopaStyle.GlassBorder, CircleShape)
                            .clickable { NavigationManager.navigate(NavDestination.Search()) }
                            .pointerHoverIcon(PointerIcon.Hand),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌕", fontSize = 15.sp, color = Color.White)
                    }
                }

                // Vertical divider to keep actions from crowding
                if (!isCompact) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(ShiopaStyle.GlassBorder)
                    )
                }

                // Account / profile action
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF281E48), Color(0xFF17172B))
                            )
                        )
                        .border(1.dp, ShiopaStyle.AccentPurple.copy(alpha = 0.5f), CircleShape)
                        .clickable { showAccountDialog = true }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👤",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }

                // Settings action
                val isSettings = currentDest is NavDestination.Settings
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSettings) ShiopaStyle.AccentMain.copy(alpha = 0.18f) else ShiopaStyle.SurfaceCard
                        )
                        .border(
                            1.dp,
                            if (isSettings) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorder,
                            CircleShape
                        )
                        .clickable { NavigationManager.navigate(NavDestination.Settings) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚙",
                        fontSize = 15.sp,
                        color = if (isSettings) Color.White else ShiopaStyle.TextSecondary
                    )
                }
            }
        }
    }

    if (showAccountDialog) {
        ShiopaAccountDialog(
            activeProviders = activeProvidersCount,
            onDismiss = { showAccountDialog = false }
        )
    }
}

@Composable
fun DesktopBottomBar(modifier: Modifier = Modifier) {
    val currentDest = NavigationManager.currentDestination

    val mobileTabs = listOf(
        ShiopaNavTab("Home", "⌂", NavDestination.Home),
        ShiopaNavTab("Explore", "✦", NavDestination.Discover()),
        ShiopaNavTab("Search", "⌕", NavDestination.Search()),
        ShiopaNavTab("Watchlist", "▤", NavDestination.Bookmarks),
        ShiopaNavTab("Extensions", "❖", NavDestination.Extensions)
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        color = Color(0xF5090A10),
        border = androidx.compose.foundation.BorderStroke(1.dp, ShiopaStyle.GlassBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (tab in mobileTabs) {
                val isSelected = when (tab.destination) {
                    is NavDestination.Home -> currentDest is NavDestination.Home
                    is NavDestination.Discover -> currentDest is NavDestination.Discover
                    is NavDestination.Search -> currentDest is NavDestination.Search
                    is NavDestination.Bookmarks -> currentDest is NavDestination.Bookmarks
                    is NavDestination.Extensions -> currentDest is NavDestination.Extensions
                    else -> false
                }

                Column(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerMd)
                        .clickable { NavigationManager.navigate(tab.destination) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(ShiopaStyle.AccentMain)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = tab.icon,
                        fontSize = 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else ShiopaStyle.TextMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else ShiopaStyle.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiopaAccountDialog(
    activeProviders: Int,
    onDismiss: () -> Unit
) {
    val historyItems = remember { mutableStateListOf<HistoryItem>() }
    val bookmarks = remember { mutableStateListOf<BookmarkItem>() }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            historyItems.clear()
            historyItems.addAll(DesktopStorage.getHistory())
            bookmarks.clear()
            bookmarks.addAll(DesktopStorage.getBookmarks())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(ShiopaStyle.CornerLg)
                        .background(
                            Brush.linearGradient(
                                listOf(ShiopaStyle.AccentPurple, Color(0xFF3B82F6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 18.sp, color = Color.White)
                }
                Spacer(modifier = Modifier.width(ShiopaSpacing.Md))
                Column {
                    Text(
                        text = "Desktop Profile",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Local Offline Media Center",
                        fontSize = 12.sp,
                        color = ShiopaStyle.TextSecondary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)) {
                // Statistics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
                ) {
                    AccountStatBadge(label = "Watchlist", value = bookmarks.size.toString(), modifier = Modifier.weight(1f))
                    AccountStatBadge(label = "History", value = historyItems.size.toString(), modifier = Modifier.weight(1f))
                    AccountStatBadge(label = "Providers", value = activeProviders.toString(), modifier = Modifier.weight(1f))
                }

                // Profile Info Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShiopaStyle.CornerMd)
                        .background(ShiopaStyle.SurfaceCard)
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
                        .padding(ShiopaSpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Storage & State",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "All playback history, resume points, and extension configurations are stored locally at ${AppPaths.appDataDir.absolutePath}.",
                            fontSize = 11.5.sp,
                            color = ShiopaStyle.TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(ShiopaStyle.CornerMd)
                    .background(ShiopaStyle.AccentMain)
                    .clickable(onClick = onDismiss)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    NavigationManager.navigate(NavDestination.Settings)
                }
            ) {
                Text("Open Settings", color = ShiopaStyle.TextSecondary, fontSize = 13.sp)
            }
        },
        containerColor = ShiopaStyle.GlassSurfaceHigh,
        shape = ShiopaStyle.CornerXl
    )
}

@Composable
private fun AccountStatBadge(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShiopaStyle.CornerMd)
            .background(ShiopaStyle.SurfaceCard)
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = label, fontSize = 11.sp, color = ShiopaStyle.TextMuted)
        }
    }
}
