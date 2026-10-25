package com.lagradost.cloudstream4.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.downloads.DesktopDownloadManager
import com.lagradost.cloudstream4.downloads.DownloadState
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.ShiopaBrandMark
import com.lagradost.cloudstream4.ui.components.ShiopaButton
import com.lagradost.cloudstream4.ui.components.ShiopaButtonTone
import com.lagradost.cloudstream4.ui.components.ShiopaIcon
import com.lagradost.cloudstream4.ui.components.ShiopaIconButton
import com.lagradost.cloudstream4.ui.components.ShiopaKeyCap
import com.lagradost.cloudstream4.ui.components.ShiopaStatusDot
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Legacy nav-tab model retained for compatibility with earlier shell callers. */
data class ShiopaNavTab(
    val title: String,
    val icon: String,
    val destination: NavDestination,
    val badgeCount: Int = 0
)

private data class RailNavItem(
    val label: String,
    val icon: ShiopaIcon,
    val destination: NavDestination,
    val badgeCount: Int = 0
)

private fun isDestinationSelected(
    destination: NavDestination,
    current: NavDestination
): Boolean = when (destination) {
    is NavDestination.Home -> current is NavDestination.Home
    is NavDestination.Discover -> current is NavDestination.Discover
    is NavDestination.Search -> current is NavDestination.Search
    is NavDestination.Bookmarks -> current is NavDestination.Bookmarks
    is NavDestination.History -> current is NavDestination.History
    is NavDestination.Downloads -> current is NavDestination.Downloads
    is NavDestination.Extensions -> current is NavDestination.Extensions
    is NavDestination.Settings -> current is NavDestination.Settings
    else -> false
}

/**
 * Persistent left navigation. The expanded rail keeps labels visible; the
 * compact rail keeps the same destinations and uses icon-above-label items so
 * nothing is hidden at a 960px window. Navigation semantics are unchanged.
 */
@Composable
fun DesktopSideRail(
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    val currentDest = NavigationManager.currentDestination
    val pluginsVersion by DesktopPluginManager.pluginsState.collectAsState()
    val providerCount = remember(pluginsVersion) { APIHolder.allProviders.size }
    val activeDownloads = DesktopDownloadManager.tasks.count { it.state == DownloadState.DOWNLOADING }

    val browseItems = listOf(
        RailNavItem("Home", ShiopaIcon.Home, NavDestination.Home),
        RailNavItem("Explore", ShiopaIcon.Explore, NavDestination.Discover())
    )
    val libraryItems = listOf(
        RailNavItem("Watchlist", ShiopaIcon.Watchlist, NavDestination.Bookmarks),
        RailNavItem(
            "Downloads",
            ShiopaIcon.Downloads,
            NavDestination.Downloads,
            badgeCount = activeDownloads
        ),
        RailNavItem("History", ShiopaIcon.History, NavDestination.History)
    )
    val sourceItems = listOf(
        RailNavItem("Extensions", ShiopaIcon.Extensions, NavDestination.Extensions)
    )

    val railWidth = if (expanded) 228.dp else 84.dp

    Box(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .background(ShiopaStyle.Sidebar)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (expanded) 14.dp else 8.dp,
                    end = if (expanded) 14.dp else 8.dp,
                    top = 16.dp,
                    bottom = 16.dp
                )
        ) {
            RailBrand(expanded = expanded)

            Spacer(modifier = Modifier.height(if (expanded) 22.dp else 18.dp))

            if (expanded) RailGroupLabel("Browse")
            for (item in browseItems) {
                RailItem(
                    item = item,
                    expanded = expanded,
                    selected = isDestinationSelected(item.destination, currentDest)
                )
            }

            Spacer(modifier = Modifier.height(if (expanded) 12.dp else 6.dp))
            if (expanded) RailGroupLabel("Library")
            for (item in libraryItems) {
                RailItem(
                    item = item,
                    expanded = expanded,
                    selected = isDestinationSelected(item.destination, currentDest)
                )
            }

            Spacer(modifier = Modifier.height(if (expanded) 12.dp else 6.dp))
            if (expanded) RailGroupLabel("Sources")
            for (item in sourceItems) {
                RailItem(
                    item = item,
                    expanded = expanded,
                    selected = isDestinationSelected(item.destination, currentDest)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(ShiopaStyle.Divider)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (expanded) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    ShiopaStatusDot(size = 5.dp)
                    Text(
                        text = "$providerCount providers · on-device library",
                        fontSize = 10.5.sp,
                        color = ShiopaStyle.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ShiopaStatusDot(size = 5.dp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$providerCount",
                        fontSize = 9.5.sp,
                        color = ShiopaStyle.TextMuted
                    )
                    Text(
                        text = "providers",
                        fontSize = 8.sp,
                        color = ShiopaStyle.TextMuted
                    )
                }
            }
        }

        // Hairline separating navigation from the content canvas.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(ShiopaStyle.Divider)
        )
    }
}

@Composable
private fun RailBrand(expanded: Boolean) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()

    val background by animateColorAsState(
        targetValue = if (hovered) ShiopaStyle.SurfaceCard else Color.Transparent,
        animationSpec = tween(120),
        label = "brand-hover"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShiopaStyle.CornerMd)
            .background(background)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = { NavigationManager.navigate(NavDestination.Home) }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = if (expanded) 8.dp else 0.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center
    ) {
        ShiopaBrandMark(size = 30.dp)
        if (expanded) {
            Spacer(modifier = Modifier.width(11.dp))
            Column {
                Row {
                    Text(
                        text = "CLOUD",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        letterSpacing = 1.sp,
                        color = ShiopaStyle.TextPrimary
                    )
                    Text(
                        text = "STREAM",
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.5.sp,
                        letterSpacing = 1.sp,
                        color = ShiopaStyle.TextSecondary
                    )
                }
                Text(
                    text = "DESKTOP",
                    style = ShiopaType.Overline,
                    fontSize = 8.5.sp,
                    letterSpacing = 1.7.sp,
                    color = ShiopaStyle.TextMuted
                )
            }
        }
    }
}

@Composable
private fun RailGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = ShiopaType.Overline,
        fontSize = 9.5.sp,
        letterSpacing = 1.3.sp,
        color = ShiopaStyle.TextMuted,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun RailItem(
    item: RailNavItem,
    expanded: Boolean,
    selected: Boolean
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()

    val background by animateColorAsState(
        targetValue = when {
            selected -> ShiopaStyle.AccentSoft
            hovered || focused -> ShiopaStyle.SurfaceCard
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "rail-item-background"
    )
    val borderColor by animateColorAsState(
        targetValue = if (focused) ShiopaStyle.AccentMain else Color.Transparent,
        animationSpec = tween(120),
        label = "rail-item-border"
    )
    val iconColor = when {
        selected -> ShiopaStyle.AccentMain
        hovered || focused -> ShiopaStyle.TextPrimary
        else -> ShiopaStyle.TextSecondary
    }
    val textColor = when {
        selected || hovered || focused -> ShiopaStyle.TextPrimary
        else -> ShiopaStyle.TextSecondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (expanded) 38.dp else 46.dp)
            .padding(vertical = 1.dp)
            .clip(ShiopaStyle.CornerMd)
            .background(background)
            .border(1.dp, borderColor, ShiopaStyle.CornerMd)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = { NavigationManager.navigate(item.destination) }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = if (expanded) 9.dp else 4.dp)
    ) {
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(ShiopaStyle.PillShape)
                        .background(if (selected) ShiopaStyle.AccentMain else Color.Transparent)
                )
                Spacer(modifier = Modifier.width(9.dp))
                ShiopaIcon(
                    icon = item.icon,
                    size = 17.dp,
                    tint = iconColor,
                    strokeWidth = 1.6.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor,
                    maxLines = 1
                )
                if (item.badgeCount > 0) {
                    Spacer(modifier = Modifier.weight(1f))
                    RailBadge(item.badgeCount)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ShiopaIcon(
                    icon = item.icon,
                    size = 18.dp,
                    tint = iconColor,
                    strokeWidth = 1.6.dp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = item.label,
                    fontSize = 9.5.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = textColor,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RailBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(ShiopaStyle.AccentMain)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = ShiopaStyle.TextOnAccent
        )
    }
}

/**
 * Focused top bar: a fixed back slot (no layout shift), one prominent global
 * search entry, and a grouped provider/profile/settings cluster. Section
 * navigation lives in the rail so this row never becomes a crowded toolbar.
 */
@Composable
fun DesktopTopBar(
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentDest = NavigationManager.currentDestination
    val canGoBack = NavigationManager.canGoBack
    var showAccountDialog by remember { mutableStateOf(false) }

    val pluginsVersion by DesktopPluginManager.pluginsState.collectAsState()
    val providerCount = remember(pluginsVersion) { APIHolder.allProviders.size }

    Column(modifier = modifier.fillMaxWidth().background(ShiopaStyle.CanvasBlack)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = if (isCompact) 20.dp else 28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fixed slot: search never shifts when Back appears/disappears.
            if (canGoBack) {
                ShiopaIconButton(
                    icon = ShiopaIcon.ChevronLeft,
                    contentDescription = "Back",
                    onClick = { NavigationManager.goBack() },
                    buttonSize = 34.dp,
                    iconSize = 16.dp
                )
            } else {
                Spacer(modifier = Modifier.size(34.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // SearchScreen already owns the prominent query field. Hide
                // this global launcher there to avoid rendering two search
                // controls on the same page.
                if (currentDest !is NavDestination.Search) {
                    GlobalSearchEntry(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(ShiopaStyle.CornerMd)
                    .background(ShiopaStyle.SurfaceRaised)
                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
                    .padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProviderStatusButton(
                    providerCount = providerCount,
                    compact = isCompact
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(ShiopaStyle.Divider)
                )

                ShiopaIconButton(
                    icon = ShiopaIcon.User,
                    contentDescription = "Profile",
                    onClick = { showAccountDialog = true },
                    buttonSize = 30.dp,
                    iconSize = 15.dp
                )
                ShiopaIconButton(
                    icon = ShiopaIcon.Settings,
                    contentDescription = "Settings",
                    onClick = { NavigationManager.navigate(NavDestination.Settings) },
                    selected = currentDest is NavDestination.Settings,
                    buttonSize = 30.dp,
                    iconSize = 15.dp
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ShiopaStyle.Divider)
        )
    }

    if (showAccountDialog) {
        ShiopaAccountDialog(
            activeProviders = providerCount,
            onDismiss = { showAccountDialog = false }
        )
    }
}

@Composable
private fun GlobalSearchEntry(modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> ShiopaStyle.AccentMain
            hovered -> ShiopaStyle.GlassBorderHover
            else -> ShiopaStyle.GlassBorder
        },
        animationSpec = tween(120),
        label = "search-entry-border"
    )
    val background by animateColorAsState(
        targetValue = if (hovered || focused) ShiopaStyle.SurfaceHover else ShiopaStyle.SurfaceCard,
        animationSpec = tween(120),
        label = "search-entry-background"
    )

    Row(
        modifier = modifier
            .height(38.dp)
            .clip(ShiopaStyle.CornerMd)
            .background(background)
            .border(1.dp, borderColor, ShiopaStyle.CornerMd)
            .hoverable(source)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = { NavigationManager.navigate(NavDestination.Search()) }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(start = 12.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShiopaIcon(
            icon = ShiopaIcon.Search,
            size = 15.dp,
            tint = if (hovered) ShiopaStyle.TextSecondary else ShiopaStyle.TextMuted,
            strokeWidth = 1.6.dp
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = "Search movies, series, people…",
            fontSize = 12.5.sp,
            color = ShiopaStyle.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        ShiopaKeyCap("Ctrl K")
    }
}

@Composable
private fun ProviderStatusButton(
    providerCount: Int,
    compact: Boolean
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) ShiopaStyle.SurfaceHover else Color.Transparent,
        animationSpec = tween(120),
        label = "provider-status-background"
    )

    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(ShiopaStyle.CornerSm)
            .background(background)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = { NavigationManager.navigate(NavDestination.Extensions) }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShiopaStatusDot(size = 6.dp)
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            // The bare number was ambiguous at compact width; the unit keeps
            // the status meaningful without adding another icon.
            text = "$providerCount providers",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = if (hovered) ShiopaStyle.TextPrimary else ShiopaStyle.TextSecondary,
            maxLines = 1
        )
    }
}

/** Compact bottom navigation kept for callers that still use it. */
@Composable
fun DesktopBottomBar(modifier: Modifier = Modifier) {
    val currentDest = NavigationManager.currentDestination

    val mobileTabs = listOf(
        ShiopaNavTab("Home", "", NavDestination.Home),
        ShiopaNavTab("Explore", "", NavDestination.Discover()),
        ShiopaNavTab("Search", "", NavDestination.Search()),
        ShiopaNavTab("Watchlist", "", NavDestination.Bookmarks),
        ShiopaNavTab("Extensions", "", NavDestination.Extensions)
    )
    val icons = listOf(
        ShiopaIcon.Home,
        ShiopaIcon.Explore,
        ShiopaIcon.Search,
        ShiopaIcon.Watchlist,
        ShiopaIcon.Extensions
    )

    Surface(
        modifier = modifier.fillMaxWidth().height(60.dp),
        color = ShiopaStyle.Sidebar,
        border = BorderStroke(1.dp, ShiopaStyle.Divider)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mobileTabs.forEachIndexed { index, tab ->
                val isSelected = isDestinationSelected(tab.destination, currentDest)
                Column(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerMd)
                        .clickable { NavigationManager.navigate(tab.destination) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    ShiopaIcon(
                        icon = icons[index],
                        size = 16.dp,
                        tint = if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.TextMuted,
                        strokeWidth = 1.6.dp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.title,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) ShiopaStyle.TextPrimary else ShiopaStyle.TextMuted
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
                ShiopaBrandMark(size = 40.dp)
                Spacer(modifier = Modifier.width(ShiopaSpacing.Md))
                Column {
                    Text(
                        text = "Desktop profile",
                        style = ShiopaType.Title,
                        color = ShiopaStyle.TextPrimary
                    )
                    Text(
                        text = "Your library stays on this device",
                        style = ShiopaType.Caption,
                        color = ShiopaStyle.TextSecondary
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
                ) {
                    AccountStat(
                        label = "Watchlist",
                        value = bookmarks.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    AccountStat(
                        label = "History",
                        value = historyItems.size.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    AccountStat(
                        label = "Providers",
                        value = activeProviders.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShiopaStyle.CornerMd)
                        .background(ShiopaStyle.CanvasBlack)
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
                        .padding(ShiopaSpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Storage & state",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = ShiopaStyle.TextPrimary
                        )
                        Text(
                            text = "Playback history, resume points, watchlist and extension configuration are stored locally at ${AppPaths.appDataDir.absolutePath}.",
                            style = ShiopaType.Caption,
                            color = ShiopaStyle.TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            ShiopaButton(
                label = "Done",
                onClick = onDismiss,
                tone = ShiopaButtonTone.Primary,
                compact = true
            )
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    NavigationManager.navigate(NavDestination.Settings)
                }
            ) {
                Text(
                    text = "Open settings",
                    color = ShiopaStyle.TextSecondary,
                    fontSize = 12.5.sp
                )
            }
        },
        containerColor = ShiopaStyle.SurfaceRaised,
        shape = ShiopaStyle.CornerXl
    )
}

@Composable
private fun AccountStat(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(ShiopaStyle.CornerMd)
            .background(ShiopaStyle.CanvasBlack)
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerMd)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = ShiopaStyle.TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 10.5.sp,
                color = ShiopaStyle.TextMuted
            )
        }
    }
}
