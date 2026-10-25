package com.lagradost.cloudstream4.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.DesktopTmdbProvider
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.ContentRail
import com.lagradost.cloudstream4.ui.components.ErrorView
import com.lagradost.cloudstream4.ui.components.MediaCard
import com.lagradost.cloudstream4.ui.components.ShiopaButton
import com.lagradost.cloudstream4.ui.components.ShiopaButtonTone
import com.lagradost.cloudstream4.ui.components.ShiopaFilterChip
import com.lagradost.cloudstream4.ui.components.ShiopaIcon
import com.lagradost.cloudstream4.ui.components.ShiopaIconButton
import com.lagradost.cloudstream4.ui.components.ShiopaStatusDot
import com.lagradost.cloudstream4.ui.components.displayMediaType
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which provider catalog request produced a home section (for Explore-all pagination). */
private data class HomeSectionSource(
    val providerName: String,
    val requestName: String,
    val requestData: String
)

/** Next carousel index with wraparound; empty/single lists stay at 0. Pure. */
fun nextCarouselIndex(size: Int, current: Int): Int =
    if (size <= 1) 0 else (current + 1) % size

/** Which real loaded titles the hero cycles through, in latest-content order. */
fun heroCarouselTitles(homeSections: List<HomePageList>): List<SearchResponse> =
    homeSections.flatMap { it.list }.distinctBy { searchResultKey(it.apiName, it.url) }

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val homeSections = remember { mutableStateListOf<HomePageList>() }
    // Which provider + catalog request produced each displayed section, so
    // "View all" opens that provider's real catalog (with pagination)
    // instead of the global TMDB recommendations. Keyed by display name.
    val sectionSources = remember { java.util.concurrent.ConcurrentHashMap<String, HomeSectionSource>() }
    var heroItem by remember { mutableStateOf<SearchResponse?>(null) }
    var heroCarouselIndex by remember { mutableIntStateOf(0) }

    // Real loaded titles in latest-content order. Recomputed when the section
    // snapshot changes; no network request is made per carousel tick. Capped so
    // the dot selector stays a compact control instead of a wall of ticks.
    val heroTitles = heroCarouselTitles(homeSections).take(6)

    // Pause auto-advance while the pointer is over the featured area.
    val heroInteraction = remember { MutableInteractionSource() }
    val heroHovered by heroInteraction.collectIsHoveredAsState()

    LaunchedEffect(heroHovered, heroCarouselIndex, heroTitles.size) {
        if (heroHovered || heroTitles.size <= 1) return@LaunchedEffect
        delay(3000L)
        val next = nextCarouselIndex(heroTitles.size, heroCarouselIndex)
        heroCarouselIndex = next
        heroItem = heroTitles[next]
    }

    var selectedCategoryChip by remember { mutableStateOf("All") }
    var selectedHomeProvider by remember { mutableStateOf(DesktopStorage.settings.homeProvider) }
    var showProviderMenu by remember { mutableStateOf(false) }

    // Category chips are a real horizontal scroller. The paired controls
    // below mirror its position so the affordance is explicit instead of a
    // fade that hides the clipped chip.
    val chipScrollState = rememberLazyListState()
    val canScrollChipsBack by remember(chipScrollState) {
        derivedStateOf { chipScrollState.canScrollBackward }
    }
    val canScrollChipsForward by remember(chipScrollState) {
        derivedStateOf { chipScrollState.canScrollForward }
    }

    fun scrollCategoryChips(direction: Int) {
        scope.launch {
            val viewport = chipScrollState.layoutInfo.viewportSize.width
            val step = if (viewport > 0) viewport * 0.8f else 320f
            chipScrollState.animateScrollBy(direction * step)
        }
    }

    // Hero watchlist state — reflects the current featured title's bookmark.
    val heroBookmarkStatus = remember(heroItem) {
        heroItem?.let { DesktopStorage.getBookmark(it.url, it.apiName)?.status }
    }
    var heroBookmarked by remember(heroItem) { mutableStateOf(heroBookmarkStatus != null) }

    fun toggleHeroBookmark() {
        val item = heroItem ?: return
        if (heroBookmarked) {
            DesktopStorage.removeBookmark(item.url, item.apiName)
            heroBookmarked = false
            NotificationService.info("Removed from Watchlist")
        } else {
            DesktopStorage.saveBookmark(
                BookmarkItem(
                    url = item.url,
                    apiName = item.apiName,
                    title = item.name,
                    posterUrl = item.posterUrl
                )
            )
            heroBookmarked = true
            NotificationService.success("Added to Watchlist")
        }
    }

    val categoryChips = listOf(
        "All", "Movies", "TV Shows", "Anime", "Popular Movies", "Popular TV Shows",
        "Top Rated Movies", "Top Rated TV Shows"
    )

    val pluginsVersion by DesktopPluginManager.pluginsState.collectAsState()
    val homeProviderOptions = remember(pluginsVersion) {
        listOf("All providers") + APIHolder.allProviders
            .filter { it !is TmdbProvider && it.hasMainPage }
            .map { it.name }
            .distinct()
            .sorted()
    }
    if (selectedHomeProvider !in homeProviderOptions && selectedHomeProvider != "TMDB") {
        selectedHomeProvider = "All providers"
    }

    var historyItems by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    LaunchedEffect(pluginsVersion) {
        withContext(Dispatchers.IO) {
            val loaded = DesktopStorage.getHistory()
            val providerUrls = APIHolder.allProviders.associate { it.name to it.mainUrl }
            // Resolve relative poster paths against the owning provider so
            // existing history entries display their posters when available.
            historyItems = loaded.map { h ->
                h.copy(posterUrl = absolutePosterUrl(h.posterUrl, providerUrls[h.apiName] ?: ""))
            }
        }
    }

    fun loadHomeContent() {
        isLoading = true
        errorMsg = null
        sectionSources.clear()
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val providersWithHome = APIHolder.allProviders.filter { it.hasMainPage }
                    val loadedLists = mutableListOf<HomePageList>()

                    // 1. Try any third-party streaming providers first
                    val thirdPartyProviders = providersWithHome.filter { provider ->
                        provider !is TmdbProvider &&
                            (selectedHomeProvider == "All providers" || provider.name == selectedHomeProvider)
                    }
                    for (customProvider in thirdPartyProviders.take(4)) {
                        try {
                            if (customProvider.mainPage.isNotEmpty()) {
                                for (req in customProvider.mainPage.take(3)) {
                                    try {
                                        val resp = customProvider.getMainPage(1, MainPageRequest(req.name, req.data, req.horizontalImages))
                                        if (resp != null && resp.items.isNotEmpty()) {
                                            for (list in resp.items) {
                                                val displayName = "${customProvider.name}: ${list.name}"
                                                loadedLists.add(list.copy(name = displayName))
                                                sectionSources[displayName] =
                                                    HomeSectionSource(customProvider.name, req.name, req.data)
                                            }
                                        }
                                    } catch (e: Throwable) {
                                        e.printStackTrace()
                                    }
                                }
                            } else {
                                val resp = customProvider.getMainPage(1, MainPageRequest(customProvider.name, "", false))
                                if (resp != null && resp.items.isNotEmpty()) {
                                    for (list in resp.items) {
                                        val displayName = "${customProvider.name}: ${list.name}"
                                        loadedLists.add(list.copy(name = displayName))
                                        sectionSources[displayName] =
                                            HomeSectionSource(customProvider.name, customProvider.name, "")
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }

                    // Keep the metadata catalog in All mode and when it is selected explicitly.
                    if (selectedHomeProvider == "All providers" || selectedHomeProvider == "TMDB") {
                        val tmdb = (APIHolder.allProviders.find { it is TmdbProvider } as? TmdbProvider)
                            ?: DesktopTmdbProvider()
                        try {
                            val resp = tmdb.getMainPage(1, MainPageRequest("TMDB", "", false))
                            if (resp.items.isNotEmpty()) {
                                loadedLists.addAll(resp.items)
                                for (list in resp.items) {
                                    sectionSources[list.name] =
                                        HomeSectionSource(tmdb.name, "TMDB", "")
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }

                    withContext(Dispatchers.Main) {
                        homeSections.clear()
                        homeSections.addAll(loadedLists)
                        heroCarouselIndex = 0
                        heroItem = loadedLists.firstOrNull()?.list?.firstOrNull()
                        isLoading = false
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    errorMsg = e.message ?: "Failed to connect to media catalog"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(pluginsVersion, selectedHomeProvider) {
        loadHomeContent()
    }

    if (isLoading) {
        HomeLoadingState(modifier = modifier)
        return
    }

    if (errorMsg != null && homeSections.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ShiopaStyle.CanvasBlack)
                .padding(ShiopaSpacing.Xxl)
        ) {
            ErrorView(
                title = "Could not load Home",
                details = errorMsg,
                onRetry = { loadHomeContent() }
            )
        }
        return
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
    ) {
        val compact = maxWidth < 1080.dp
        val pageInset = if (compact) ShiopaSpacing.PageInsetCompact else ShiopaSpacing.PageInset
        // Keep the first content rail visible on shorter windows instead of
        // letting the featured panel consume the entire viewport.
        val heroHeight = when {
            maxHeight < 620.dp -> 272.dp
            maxHeight < 700.dp -> 320.dp
            maxWidth < 1080.dp -> 320.dp
            maxWidth < 1420.dp -> 360.dp
            else -> 392.dp
        }

        // Provider selector interaction lives here so it keeps its state even
        // while the rows below recompose.
        val providerInteraction = remember { MutableInteractionSource() }
        val providerHovered by providerInteraction.collectIsHoveredAsState()
        val providerBackground by animateColorAsState(
            targetValue = if (providerHovered) ShiopaStyle.SurfaceHover else ShiopaStyle.SurfaceCard,
            animationSpec = tween(120),
            label = "provider-selector-background"
        )
        val providerBorder by animateColorAsState(
            targetValue = if (providerHovered) ShiopaStyle.GlassBorderHover else ShiopaStyle.GlassBorder,
            animationSpec = tween(120),
            label = "provider-selector-border"
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = ShiopaSpacing.Xxxl)
        ) {
            item { Spacer(modifier = Modifier.height(ShiopaSpacing.Xl)) }

            // 1. Featured area: a rounded, image-led panel with stable height,
            // real metadata, and a restrained right-to-left transition.
            if (heroItem != null) {
                item {
                    FeaturedHero(
                        item = heroItem!!,
                        bookmarked = heroBookmarked,
                        currentIndex = heroCarouselIndex,
                        slideCount = heroTitles.size,
                        onSelectIndex = { index ->
                            if (index in heroTitles.indices) {
                                heroCarouselIndex = index
                                heroItem = heroTitles[index]
                            }
                        },
                        heroInteraction = heroInteraction,
                        height = heroHeight,
                        compact = compact,
                        onPlayClick = {
                            val current = heroItem ?: return@FeaturedHero
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = current.url,
                                    apiName = current.apiName,
                                    title = current.name,
                                    posterUrl = current.posterUrl
                                )
                            )
                        },
                        onDetailsClick = {
                            val current = heroItem ?: return@FeaturedHero
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = current.url,
                                    apiName = current.apiName,
                                    title = current.name,
                                    posterUrl = current.posterUrl
                                )
                            )
                        },
                        onToggleWatchlist = { toggleHeroBookmark() },
                        modifier = Modifier.padding(horizontal = pageInset)
                    )
                }
            }

            // 2. Browse filters + provider selector.
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = pageInset,
                            end = pageInset,
                            top = ShiopaSpacing.Xl,
                            bottom = ShiopaSpacing.Sm
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Chips scroll inside their own viewport; the paired
                    // controls sit outside it, so no chip text is covered.
                    LazyRow(
                        state = chipScrollState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(categoryChips.size) { index ->
                            val chip = categoryChips[index]
                            ShiopaFilterChip(
                                label = chip,
                                selected = selectedCategoryChip == chip,
                                onClick = { selectedCategoryChip = chip }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(ShiopaSpacing.Sm))

                    CategoryScrollControls(
                        canScrollBack = canScrollChipsBack,
                        canScrollForward = canScrollChipsForward,
                        onScrollBack = { scrollCategoryChips(-1) },
                        onScrollForward = { scrollCategoryChips(1) }
                    )

                    Spacer(modifier = Modifier.width(ShiopaSpacing.Lg))

                    Box {
                        Row(
                            modifier = Modifier
                                .height(32.dp)
                                .clip(ShiopaStyle.PillShape)
                                .background(providerBackground)
                                .border(1.dp, providerBorder, ShiopaStyle.PillShape)
                                .clickable(
                                    interactionSource = providerInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClick = { showProviderMenu = true }
                                )
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShiopaStatusDot(size = 5.dp)
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Provider",
                                fontSize = 10.5.sp,
                                color = ShiopaStyle.TextMuted
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = selectedHomeProvider,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = ShiopaStyle.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 150.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            ShiopaIcon(
                                icon = ShiopaIcon.ChevronDown,
                                size = 12.dp,
                                tint = ShiopaStyle.TextMuted,
                                strokeWidth = 1.7.dp
                            )
                        }

                        DropdownMenu(
                            expanded = showProviderMenu,
                            onDismissRequest = { showProviderMenu = false }
                        ) {
                            (homeProviderOptions + "TMDB").distinct().forEach { providerName ->
                                val isSelected = providerName == selectedHomeProvider
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = providerName,
                                            fontSize = 12.5.sp,
                                            color = ShiopaStyle.TextPrimary
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            ShiopaIcon(
                                                icon = ShiopaIcon.Check,
                                                size = 14.dp,
                                                tint = ShiopaStyle.AccentMain,
                                                strokeWidth = 1.8.dp
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedHomeProvider = providerName
                                        DesktopStorage.saveSettings(
                                            DesktopStorage.settings.copy(homeProvider = providerName)
                                        )
                                        showProviderMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Continue Watching rail.
            if (historyItems.isNotEmpty()) {
                item {
                    ContentRail(
                        title = "Continue Watching",
                        subtitle = "Pick up where you left off",
                        onSeeAllClick = { NavigationManager.navigate(NavDestination.History) },
                        horizontalPadding = pageInset
                    ) {
                        for (history in historyItems.take(12)) {
                            val progress = if (history.durationMs > 0) {
                                history.positionMs.toFloat() / history.durationMs
                            } else null

                            MediaCard(
                                title = history.title,
                                posterUrl = history.posterUrl,
                                type = history.type,
                                watchProgress = progress,
                                onClick = {
                                    NavigationManager.navigate(
                                        NavDestination.MediaDetails(
                                            url = history.url,
                                            apiName = history.apiName,
                                            title = history.title,
                                            posterUrl = history.posterUrl,
                                            resumePositionMs = history.positionMs,
                                            resumeEpisodeIndex = history.episodeIndex,
                                            resumeSeasonIndex = history.seasonIndex,
                                            resumeEpisodeTitle = history.episodeTitle
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 4. Filtered content rails. Section titles drop the provider
            // prefix from the heading and surface it as a quiet subtitle.
            val displayedSections = homeSections.mapNotNull { section ->
                val matchingItems = when (selectedCategoryChip) {
                    "All" -> section.list
                    "Movies" -> section.list.filter {
                        it is MovieSearchResponse || it.type == TvType.AnimeMovie
                    }
                    "TV Shows" -> section.list.filter {
                        it is TvSeriesSearchResponse && it.type != TvType.Anime && it.type != TvType.OVA
                    }
                    "Anime" -> section.list.filter {
                        it.type == TvType.Anime || it.type == TvType.AnimeMovie || it.type == TvType.OVA
                    }
                    "Popular Movies" -> if (section.name.contains("Popular Movies", ignoreCase = true)) section.list else emptyList()
                    "Popular TV Shows" -> if (section.name.contains("Popular Series", ignoreCase = true)) section.list else emptyList()
                    "Top Rated Movies" -> if (section.name.contains("Top Movies", ignoreCase = true)) section.list else emptyList()
                    "Top Rated TV Shows" -> if (section.name.contains("Top Series", ignoreCase = true)) section.list else emptyList()
                    else -> emptyList()
                }
                matchingItems.takeIf { it.isNotEmpty() }?.let { section.copy(list = it) }
            }

            if (displayedSections.isEmpty()) {
                item {
                    HomeEmptyState(
                        selectedCategory = selectedCategoryChip,
                        onShowAll = { selectedCategoryChip = "All" },
                        onRefresh = { loadHomeContent() },
                        pageInset = pageInset
                    )
                }
            }

            items(displayedSections.size) { index ->
                val section = displayedSections[index]
                if (section.list.isNotEmpty()) {
                    val nameParts = section.name.split(": ", limit = 2)
                    val displayTitle = if (nameParts.size == 2) nameParts[1] else section.name
                    val providerLabel = if (nameParts.size == 2) nameParts[0] else null

                    ContentRail(
                        title = displayTitle,
                        subtitle = providerLabel?.let { "via $it" },
                        onSeeAllClick = {
                            val src = sectionSources[section.name]
                            NavigationManager.navigate(
                                NavDestination.Discover(
                                    initialGenre = section.name,
                                    providerName = src?.providerName,
                                    sectionName = src?.requestName,
                                    sectionData = src?.requestData
                                )
                            )
                        },
                        horizontalPadding = pageInset
                    ) {
                        for (item in section.list) {
                            val year = when (item) {
                                is MovieSearchResponse -> item.year
                                is TvSeriesSearchResponse -> item.year
                                else -> null
                            }
                            val rating = item.score?.toDouble(10)

                            MediaCard(
                                title = item.name,
                                posterUrl = item.posterUrl,
                                year = year,
                                rating = rating,
                                type = item.type?.name,
                                onClick = {
                                    NavigationManager.navigate(
                                        NavDestination.MediaDetails(
                                            url = item.url,
                                            apiName = item.apiName,
                                            title = item.name,
                                            posterUrl = item.posterUrl
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Paired category-scroll controls. They live outside the LazyRow viewport so
 * the clipped chip stays fully visible up to the edge; enabled state follows
 * the row's real scroll bounds rather than a decorative fade.
 */
@Composable
private fun CategoryScrollControls(
    canScrollBack: Boolean,
    canScrollForward: Boolean,
    onScrollBack: () -> Unit,
    onScrollForward: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(ShiopaStyle.PillShape)
            .background(ShiopaStyle.SurfaceCard)
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
            .padding(horizontal = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShiopaIconButton(
            icon = ShiopaIcon.ChevronLeft,
            contentDescription = "Show earlier category filters",
            onClick = onScrollBack,
            enabled = canScrollBack,
            buttonSize = 30.dp,
            iconSize = 14.dp
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(ShiopaStyle.Divider)
        )
        ShiopaIconButton(
            icon = ShiopaIcon.ChevronRight,
            contentDescription = "Show later category filters",
            onClick = onScrollForward,
            enabled = canScrollForward,
            buttonSize = 30.dp,
            iconSize = 14.dp
        )
    }
}

@Composable
private fun FeaturedHero(
    item: SearchResponse,
    bookmarked: Boolean,
    currentIndex: Int,
    slideCount: Int,
    onSelectIndex: (Int) -> Unit,
    heroInteraction: MutableInteractionSource,
    height: Dp,
    compact: Boolean,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onToggleWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    val year = when (item) {
        is MovieSearchResponse -> item.year
        is TvSeriesSearchResponse -> item.year
        else -> null
    }
    val rating = item.score?.toDouble(10)
    val typeName = item.type?.name?.let(::displayMediaType)
    val titleSize = if (compact) 32.sp else 38.sp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(ShiopaStyle.CornerXl)
            .background(ShiopaStyle.SurfaceCard)
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerXl)
            .hoverable(heroInteraction)
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = {
                (
                    slideInHorizontally(animationSpec = tween(400)) { full -> full } +
                        fadeIn(animationSpec = tween(400))
                    ).togetherWith(
                    slideOutHorizontally(animationSpec = tween(400)) { full -> -full } +
                        fadeOut(animationSpec = tween(400))
                )
            },
            label = "featured-slide",
            modifier = Modifier.fillMaxSize()
        ) { current ->
            Box(modifier = Modifier.fillMaxSize()) {
                // Preserve the existing full-bleed hero treatment. Search
                // responses usually provide portrait posters, so a fitted
                // full-poster panel is also shown at the right below.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(ShiopaStyle.SurfaceRaised, ShiopaStyle.CanvasBlack)
                            )
                        )
                )
                if (!current.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = current.posterUrl,
                        contentDescription = current.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShiopaStyle.HeroSideGradient)
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ShiopaStyle.HeroGradient)
                )

                // Leave a separate strip for the carousel dots so the
                // controls never sit on top of the poster artwork.
                val posterBottomInset = if (slideCount > 1) 64.dp else 14.dp
                val posterHeight = maxOf(120.dp, height - 14.dp - posterBottomInset)
                val posterWidth = posterHeight * (2f / 3f)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(
                            start = 12.dp,
                            top = 14.dp,
                            end = 20.dp,
                            bottom = posterBottomInset
                        )
                        .width(posterWidth)
                        .fillMaxHeight()
                        .clip(ShiopaStyle.CornerLg)
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg)
                        .background(ShiopaStyle.CardBackground)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(ShiopaStyle.SurfaceCard, ShiopaStyle.Sidebar)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = current.name.trim().take(2).uppercase(),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ShiopaStyle.TextPrimary.copy(alpha = 0.5f)
                        )
                    }
                    if (!current.posterUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = current.posterUrl,
                            contentDescription = "${current.name} poster",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = if (compact) ShiopaSpacing.Xl else ShiopaSpacing.Xxl,
                            end = ShiopaSpacing.Xl,
                            bottom = if (compact) ShiopaSpacing.Xl else ShiopaSpacing.Xxl
                        )
                        .widthIn(max = 680.dp)
                        .fillMaxWidth(0.68f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(2.dp)
                                .clip(CircleShape)
                                .background(ShiopaStyle.TextPrimary.copy(alpha = 0.45f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FEATURED",
                            style = ShiopaType.Overline,
                            color = ShiopaStyle.TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))

                    Text(
                        text = current.name,
                        fontSize = titleSize,
                        lineHeight = if (compact) 38.sp else 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = ShiopaStyle.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val meta = buildList {
                            if (rating != null && rating > 0) {
                                add("★ ${"%.1f".format(rating)}" to ShiopaStyle.AccentGold)
                            }
                            if (year != null && year > 0) {
                                add(year.toString() to ShiopaStyle.TextSecondary)
                            }
                            if (!typeName.isNullOrBlank()) {
                                add(typeName to ShiopaStyle.TextSecondary)
                            }
                            if (current.apiName.isNotBlank()) {
                                add("via ${current.apiName}" to ShiopaStyle.TextSecondary)
                            }
                        }
                        meta.forEachIndexed { index, entry ->
                            if (index > 0) {
                                Text(
                                    text = "·",
                                    fontSize = 11.sp,
                                    color = ShiopaStyle.TextMuted
                                )
                            }
                            Text(
                                text = entry.first,
                                fontSize = 12.5.sp,
                                fontWeight = if (entry.second == ShiopaStyle.AccentGold) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = entry.second,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)
                    ) {
                        ShiopaButton(
                            label = "Play",
                            onClick = onPlayClick,
                            tone = ShiopaButtonTone.Primary,
                            icon = ShiopaIcon.Play
                        )
                        ShiopaButton(
                            label = "Details",
                            onClick = onDetailsClick,
                            tone = ShiopaButtonTone.Secondary
                        )
                        ShiopaButton(
                            label = if (bookmarked) "In watchlist" else "Watchlist",
                            onClick = onToggleWatchlist,
                            tone = ShiopaButtonTone.Secondary,
                            icon = if (bookmarked) ShiopaIcon.Check else ShiopaIcon.Plus,
                            selected = bookmarked
                        )
                    }
                }
            }
        }

        // Carousel position: restrained dots over a legible dark capsule.
        if (slideCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(ShiopaSpacing.Xl)
                    .clip(ShiopaStyle.PillShape)
                    .background(Color(0x990A0B0F))
                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (index in 0 until slideCount) {
                    HeroDot(
                        selected = index == currentIndex,
                        index = index,
                        onClick = { onSelectIndex(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDot(
    selected: Boolean,
    index: Int,
    onClick: () -> Unit
) {
    val width by animateDpAsState(
        targetValue = if (selected) 18.dp else 6.dp,
        animationSpec = tween(160),
        label = "hero-dot-width"
    )
    val color by animateColorAsState(
        targetValue = if (selected) ShiopaStyle.AccentMain else ShiopaStyle.TextPrimary.copy(alpha = 0.35f),
        animationSpec = tween(160),
        label = "hero-dot-color"
    )

    Box(
        modifier = Modifier
            .width(18.dp)
            .height(18.dp)
            .semantics { contentDescription = "Featured title ${index + 1}" }
            .clickable(role = Role.Button, onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(4.dp)
                .clip(ShiopaStyle.PillShape)
                .background(color)
        )
    }
}

@Composable
private fun HomeEmptyState(
    selectedCategory: String,
    onShowAll: () -> Unit,
    onRefresh: () -> Unit,
    pageInset: Dp
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pageInset, vertical = ShiopaSpacing.Xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ShiopaStyle.CornerLg)
                .background(ShiopaStyle.SurfaceCard)
                .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg)
                .padding(ShiopaSpacing.Xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(ShiopaStyle.SurfaceRaised)
                    .border(1.dp, ShiopaStyle.GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ShiopaIcon(
                    icon = ShiopaIcon.Explore,
                    size = 19.dp,
                    tint = ShiopaStyle.TextMuted,
                    strokeWidth = 1.5.dp
                )
            }
            Spacer(modifier = Modifier.height(ShiopaSpacing.Lg))
            Text(
                text = if (selectedCategory == "All") {
                    "No titles loaded yet"
                } else {
                    "Nothing in $selectedCategory"
                },
                style = ShiopaType.Title,
                color = ShiopaStyle.TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (selectedCategory == "All") {
                    "Refresh the catalog or choose a different provider."
                } else {
                    "Try another filter or refresh the catalog."
                },
                style = ShiopaType.Caption,
                color = ShiopaStyle.TextSecondary,
                modifier = Modifier.widthIn(max = 480.dp)
            )
            Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))
            Row(horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)) {
                if (selectedCategory != "All") {
                    ShiopaButton(
                        label = "Show all",
                        onClick = onShowAll,
                        tone = ShiopaButtonTone.Secondary,
                        compact = true
                    )
                }
                ShiopaButton(
                    label = "Refresh",
                    onClick = onRefresh,
                    tone = ShiopaButtonTone.Primary,
                    compact = true
                )
            }
        }
    }
}

/** Content-shaped loading state instead of a bare full-screen spinner. */
@Composable
private fun HomeLoadingState(modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
    ) {
        val compact = maxWidth < 1080.dp
        val pageInset = if (compact) ShiopaSpacing.PageInsetCompact else ShiopaSpacing.PageInset
        val heroHeight = when {
            maxHeight < 620.dp -> 272.dp
            compact -> 320.dp
            else -> 360.dp
        }

        Column(modifier = Modifier.fillMaxSize()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = ShiopaStyle.AccentMain,
                trackColor = ShiopaStyle.SurfaceCard
            )

            Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pageInset)
                    .height(heroHeight)
                    .clip(ShiopaStyle.CornerXl)
                    .background(ShiopaStyle.SurfaceCard)
                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerXl)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(ShiopaSpacing.Xxl)
                        .widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)
                ) {
                    SkeletonBlock(width = 74.dp, height = 9.dp)
                    SkeletonBlock(width = 360.dp, height = 30.dp)
                    SkeletonBlock(width = 220.dp, height = 11.dp)
                    Row(
                        modifier = Modifier.padding(top = ShiopaSpacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)
                    ) {
                        SkeletonBlock(width = 92.dp, height = 38.dp, radius = 10.dp)
                        SkeletonBlock(width = 96.dp, height = 38.dp, radius = 10.dp)
                        SkeletonBlock(width = 112.dp, height = 38.dp, radius = 10.dp)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pageInset, vertical = ShiopaSpacing.Xl)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
            ) {
                repeat(6) { index ->
                    SkeletonBlock(
                        width = (62 + (index % 3) * 18).dp,
                        height = 32.dp,
                        radius = 50.dp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = pageInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkeletonBlock(width = 180.dp, height = 18.dp)
                SkeletonBlock(width = 52.dp, height = 10.dp)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = pageInset, top = ShiopaSpacing.Md, end = pageInset),
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Lg)
            ) {
                repeat(4) {
                    Column(modifier = Modifier.width(168.dp)) {
                        SkeletonBlock(width = 168.dp, height = 252.dp, radius = 14.dp)
                        Spacer(modifier = Modifier.height(9.dp))
                        SkeletonBlock(width = 132.dp, height = 12.dp)
                        Spacer(modifier = Modifier.height(6.dp))
                        SkeletonBlock(width = 84.dp, height = 9.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
    radius: Dp = 6.dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(radius))
            .background(ShiopaStyle.SurfaceRaised)
            .border(
                1.dp,
                ShiopaStyle.GlassBorder,
                androidx.compose.foundation.shape.RoundedCornerShape(radius)
            )
    )
}
