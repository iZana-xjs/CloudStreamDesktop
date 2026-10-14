package com.lagradost.cloudstream4.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.core.AppSettings
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.ToastHost
import com.lagradost.cloudstream4.ui.screens.BookmarksScreen
import com.lagradost.cloudstream4.ui.screens.DiscoverScreen
import com.lagradost.cloudstream4.ui.screens.DownloadsScreen
import com.lagradost.cloudstream4.ui.screens.ExtensionsScreen
import com.lagradost.cloudstream4.ui.screens.HistoryScreen
import com.lagradost.cloudstream4.ui.screens.HomeScreen
import com.lagradost.cloudstream4.ui.screens.MediaDetailsScreen
import com.lagradost.cloudstream4.ui.screens.PlayerScreen
import com.lagradost.cloudstream4.ui.screens.SearchScreen
import com.lagradost.cloudstream4.ui.screens.SettingsScreen
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle

@Composable
fun DesktopShell(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onForceExitFullscreen: () -> Unit = {},
    onPlayerBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentDest = NavigationManager.currentDestination

    // Leaving the player (back/close) while fullscreen must restore the
    // window — otherwise the app sits borderless-fullscreen over other
    // screens with no way back except Esc.
    androidx.compose.runtime.LaunchedEffect(currentDest) {
        if (currentDest !is NavDestination.Player && isFullscreen) {
            onForceExitFullscreen()
        }
    }

    // If currently in Player destination, render full-window player without topbar
    if (currentDest is NavDestination.Player) {
        Box(modifier = modifier.fillMaxSize()) {
            PlayerScreen(
                url = currentDest.url,
                title = currentDest.title,
                episodeTitle = currentDest.episodeTitle,
                mediaUrl = currentDest.mediaUrl,
                apiName = currentDest.apiName,
                episodeIndex = currentDest.episodeIndex,
                seasonIndex = currentDest.seasonIndex,
                startPositionMs = currentDest.startPositionMs,
                posterUrl = currentDest.posterUrl,
                sources = currentDest.sources,
                mediaHeaders = currentDest.mediaHeaders,
                tvTypeName = currentDest.tvTypeName,
                year = currentDest.year,
                malId = currentDest.malId,
                tmdbId = currentDest.tmdbId,
                imdbId = currentDest.imdbId,
                absoluteEpisodeIndex = currentDest.absoluteEpisodeIndex,
                hasNextEpisode = currentDest.hasNextEpisode,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onForceExitFullscreen = onForceExitFullscreen,
                onPlayerBack = onPlayerBack
            )
            ToastHost()
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The full desktop header needs room for its brand, navigation, search,
        // provider count, and account controls. Use the compact navigation before
        // those groups collide on a narrow or maximized laptop window.
        val isCompact = maxWidth < 1180.dp

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = ShiopaStyle.CanvasBlack,
            contentColor = Color.White
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Floating Sleek Shiopa Glass Header
                DesktopTopBar(isCompact = isCompact)

                // Full-Width Cinematic Content Pane
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentDest) {
                        is NavDestination.Home -> HomeScreen()
                        is NavDestination.Discover -> DiscoverScreen(initialGenre = currentDest.initialGenre)
                        is NavDestination.Search -> SearchScreen(
                            initialQuery = currentDest.initialQuery,
                            initialApiName = currentDest.initialApiName
                        )
                        is NavDestination.MediaDetails -> MediaDetailsScreen(
                            url = currentDest.url,
                            apiName = currentDest.apiName,
                            initialTitle = currentDest.title,
                            initialPosterUrl = currentDest.posterUrl,
                            resumePositionMs = currentDest.resumePositionMs,
                            resumeEpisodeIndex = currentDest.resumeEpisodeIndex,
                            resumeSeasonIndex = currentDest.resumeSeasonIndex,
                            resumeEpisodeTitle = currentDest.resumeEpisodeTitle
                        )
                        is NavDestination.Bookmarks -> BookmarksScreen()
                        is NavDestination.History -> HistoryScreen()
                        is NavDestination.Downloads -> DownloadsScreen(onSettingsChanged = onSettingsChanged)
                        is NavDestination.Extensions -> ExtensionsScreen()
                        is NavDestination.Settings -> SettingsScreen(
                            currentSettings = settings,
                            onSettingsChanged = onSettingsChanged
                        )
                        is NavDestination.Player -> { /* handled above */ }
                    }

                    ToastHost()
                }

                // Fixed Frosted Bottom Bar on Mobile/Compact Viewports (< 768dp)
                if (isCompact) {
                    DesktopBottomBar()
                }
            }
        }
    }
}
