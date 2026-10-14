package com.lagradost.cloudstream4.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

sealed class NavDestination {
    object Home : NavDestination()
    data class Discover(
        val initialGenre: String? = null,
        // Provider catalog mode: when set, Discover paginates THIS provider's
        // section instead of the global TMDB recommendations.
        val providerName: String? = null,
        val sectionName: String? = null,
        val sectionData: String? = null,
        val horizontalImages: Boolean = false
    ) : NavDestination()
    data class Search(val initialQuery: String? = null, val initialApiName: String? = null) : NavDestination()
    data class MediaDetails(
        val url: String,
        val apiName: String,
        val title: String? = null,
        val posterUrl: String? = null,
        val resumePositionMs: Long = 0L,
        val resumeEpisodeIndex: Int? = null,
        val resumeSeasonIndex: Int? = null,
        val resumeEpisodeTitle: String? = null
    ) : NavDestination()
    object Bookmarks : NavDestination()
    object History : NavDestination()
    object Downloads : NavDestination()
    object Extensions : NavDestination()
    object Settings : NavDestination()
    data class Player(
        val url: String,
        val title: String,
        val episodeTitle: String? = null,
        val mediaUrl: String? = null,
        val apiName: String? = null,
        val episodeIndex: Int? = null,
        val seasonIndex: Int? = null,
        val startPositionMs: Long = 0L,
        val posterUrl: String? = null,
        val sources: List<PlaybackSource> = emptyList(),
        val mediaHeaders: Map<String, String> = emptyMap(),
        // Stable episode identity for automatic skip-marker lookup. All
        // optional: nulls simply narrow (or disable) provider matching.
        val tvTypeName: String? = null,
        val year: Int? = null,
        val malId: Int? = null,
        val tmdbId: String? = null,
        val imdbId: String? = null,
        // Flat position in series order (+1) for absolute-numbered anime,
        // and whether a later episode exists in the loaded list.
        val absoluteEpisodeIndex: Int? = null,
        val hasNextEpisode: Boolean = false
    ) : NavDestination()
}

data class PlaybackSource(
    val name: String,
    val url: String,
    val quality: Int,
    val type: String,
    val headers: Map<String, String> = emptyMap()
)

/**
 * List the easy-to-play SDR 1080p option first, then other lower-bandwidth SDR
 * options. Keep every mirror available so people can still choose 4K/HDR.
 */
fun <T> sortPlaybackSources(
    sources: List<T>,
    name: (T) -> String,
    quality: (T) -> Int
): List<T> {
    fun hdrTagged(item: T): Boolean {
        val label = name(item).lowercase()
        return listOf("hdr", "dolby vision", "dolbyvision", "hdr10", "hlg")
            .any(label::contains) || Regex("\\bdv\\b").containsMatchIn(label)
    }

    fun tier(item: T): Int {
        val q = quality(item)
        val hdr = hdrTagged(item)
        return when {
            !hdr && q == 1080 -> 0
            !hdr && q in 1..1080 -> 1
            !hdr && q > 1080 -> 2
            !hdr -> 3
            hdr && q in 1..1080 -> 4
            hdr && q > 1080 -> 5
            else -> 6
        }
    }

    return sources.sortedWith(
        compareBy<T> { tier(it) }.thenBy { item ->
            when (tier(item)) {
                1, 4 -> -quality(item) // highest quality within 1080p SDR/HDR fallback
                2, 5 -> quality(item) // avoid 4K when a lower SDR/HDR source exists
                else -> 0
            }
        }
    )
}

/**
 * Shared keyboard scrolling for the stream/source pickers (Available Streams
 * dialog + in-player Playback source dialog). Returns true when the key was
 * consumed. Wheel and scrollbar-drag scrolling are handled by LazyColumn and
 * VerticalScrollbar themselves; this covers arrows / PageUp / PageDown /
 * Home / End for keyboard users.
 */
fun handleSourceListKey(
    event: KeyEvent,
    listState: LazyListState,
    scope: kotlinx.coroutines.CoroutineScope
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val layout = listState.layoutInfo
    val total = layout.totalItemsCount
    if (total == 0) return false
    val first = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
    val last = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
    val visible = (last - first + 1).coerceAtLeast(1)
    val target = when (event.key) {
        Key.DirectionDown -> first + 1
        Key.DirectionUp -> first - 1
        Key.PageDown -> last + 1
        Key.PageUp -> first - visible
        Key.MoveHome -> 0
        Key.MoveEnd -> total - 1
        else -> return false
    }.coerceIn(0, total - 1)
    scope.launch { listState.scrollToItem(target) }
    return true
}

object NavigationManager {
    private val backStack = mutableStateListOf<NavDestination>(NavDestination.Home)

    var currentDestination: NavDestination by mutableStateOf(NavDestination.Home)
        private set

    val canGoBack: Boolean
        get() = backStack.size > 1

    fun navigate(destination: NavDestination) {
        if (currentDestination == destination) return
        backStack.add(destination)
        currentDestination = destination
    }

    fun replace(destination: NavDestination) {
        if (backStack.isNotEmpty()) {
            backStack.removeAt(backStack.size - 1)
        }
        backStack.add(destination)
        currentDestination = destination
    }

    fun goBack(): Boolean {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.size - 1)
            currentDestination = backStack.last()
            return true
        }
        return false
    }

    fun clearToHome() {
        backStack.clear()
        backStack.add(NavDestination.Home)
        currentDestination = NavDestination.Home
    }
}
