package com.lagradost.cloudstream4.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.lagradost.cloudstream4.core.BookmarkStatus
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.DesktopTmdbProvider
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.ContentRail
import com.lagradost.cloudstream4.ui.components.ErrorView
import com.lagradost.cloudstream4.ui.components.LoadingView
import com.lagradost.cloudstream4.ui.components.MediaCard
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlinx.coroutines.Dispatchers
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
fun heroCarouselTitles(homeSections: List<com.lagradost.cloudstream3.HomePageList>): List<SearchResponse> =
    homeSections.flatMap { it.list }.distinctBy { searchResultKey(it.apiName, it.url) }

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val homeSections = remember { mutableStateListOf<HomePageList>() }
    // Which provider + catalog request produced each displayed section, so
    // "Explore all" opens that provider's real catalog (with pagination)
    // instead of the global TMDB recommendations. Keyed by display name.
    // Concurrent map: filled on IO threads, read on user clicks.
    val sectionSources = remember { java.util.concurrent.ConcurrentHashMap<String, HomeSectionSource>() }
    var heroItem by remember { mutableStateOf<SearchResponse?>(null) }

    // Hero carousel: real loaded titles in latest-content order, auto-advancing
    // every 3s with a short right-to-left slide. Paused on user interaction,
    // single/empty lists, or an unfocused window. No refetch per tick.
    val heroTitles = remember(homeSections) {
        homeSections.flatMap { it.list }.distinctBy { searchResultKey(it.apiName, it.url) }
    }
    var heroCarouselIndex by remember { mutableIntStateOf(0) }
    // Pause auto-advance while the pointer is over the hero (user interacting).
    val heroInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
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
        LoadingView(message = "Curating featured cinema and series...")
        return
    }

    if (errorMsg != null && homeSections.isEmpty()) {
        ErrorView(
            title = "Could not load Home",
            details = errorMsg,
            onRetry = { loadHomeContent() }
        )
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
    ) {
        // 1. Cinematic Featured Hero — auto-advancing carousel with a short
        // right-to-left slide. Interaction pauses the timer (see heroPaused).
        if (heroItem != null) {
            item {
                androidx.compose.animation.AnimatedContent(
                    targetState = heroItem,
                    transitionSpec = {
                        (slideInHorizontally { full -> full } + fadeIn(animationSpec = androidx.compose.animation.core.tween(420)))
                            .togetherWith(
                                slideOutHorizontally { full -> -full } + fadeOut(animationSpec = androidx.compose.animation.core.tween(420))
                            )
                    },
                    label = "hero-slide",
                    modifier = Modifier.hoverable(heroInteraction)
                ) { currentHero ->
                    ShiopaEdgeToEdgeHero(
                        item = currentHero!!,
                        bookmarked = heroBookmarked,
                        onPlayClick = {
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = currentHero!!.url,
                                    apiName = currentHero!!.apiName,
                                    title = currentHero!!.name,
                                    posterUrl = currentHero!!.posterUrl
                                )
                            )
                        },
                        onDetailsClick = {
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = currentHero!!.url,
                                    apiName = currentHero!!.apiName,
                                    title = currentHero!!.name,
                                    posterUrl = currentHero!!.posterUrl
                                )
                            )
                        },
                        onToggleWatchlist = { toggleHeroBookmark() }
                    )
                }
            }
        }

        // 2. Compact genre shortcut rail
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ShiopaSpacing.PageInset, vertical = ShiopaSpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(categoryChips.size) { index ->
                        val chip = categoryChips[index]
                        val isSelected = selectedCategoryChip == chip
                        val bg by animateColorAsState(
                            if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.SurfaceCard
                        )
                        val textCol by animateColorAsState(
                            if (isSelected) Color.White else ShiopaStyle.TextSecondary
                        )

                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(bg)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorder,
                                    shape = ShiopaStyle.PillShape
                                )
                                .clickable { selectedCategoryChip = chip }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textCol
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(ShiopaSpacing.Md))
                Box {
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(ShiopaStyle.SurfaceCard)
                            .border(1.dp, ShiopaStyle.GlassBorderHover, ShiopaStyle.PillShape)
                            .clickable { showProviderMenu = true }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = ShiopaSpacing.Md, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(ShiopaStyle.AccentGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Provider: $selectedHomeProvider",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text("  ▾", fontSize = 10.sp, color = ShiopaStyle.TextMuted)
                        }
                    }
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        (homeProviderOptions + "TMDB").distinct().forEach { providerName ->
                            DropdownMenuItem(
                                text = { Text(providerName) },
                                onClick = {
                                    selectedHomeProvider = providerName
                                    DesktopStorage.saveSettings(DesktopStorage.settings.copy(homeProvider = providerName))
                                    showProviderMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // 3. Continue Watching Rail (if history present and "All" or "Continue" selected)
        if (historyItems.isNotEmpty()) {
            item {
                ContentRail(
                    title = "Continue Watching",
                    subtitle = "Jump back into your recent titles",
                    onSeeAllClick = { NavigationManager.navigate(NavDestination.History) }
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

        // 4. Content Rows / Rails
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No titles available in $selectedCategoryChip",
                        color = ShiopaStyle.TextMuted,
                        fontSize = 14.sp
                    )
                }
            }
        }

        items(displayedSections.size) { index ->
            val section = displayedSections[index]
            if (section.list.isNotEmpty()) {
                ContentRail(
                    title = section.name,
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
                    }
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

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun ShiopaEdgeToEdgeHero(
    item: SearchResponse,
    bookmarked: Boolean,
    onPlayClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onToggleWatchlist: () -> Unit
) {
    val backdropUrl = item.posterUrl?.replace("/w500/", "/w1280/") ?: item.posterUrl
    val year = when (item) {
        is MovieSearchResponse -> item.year
        is TvSeriesSearchResponse -> item.year
        else -> null
    }
    val rating = item.score?.toDouble(10)
    val typeName = item.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(440.dp)
            .background(ShiopaStyle.CanvasBlack)
    ) {
        if (!backdropUrl.isNullOrBlank()) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF161528), Color(0xFF090A10))
                        )
                    )
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = ShiopaSpacing.Xxl, bottom = ShiopaSpacing.Xxl)
                .fillMaxWidth(0.62f)
        ) {
            // Real metadata only — no fabricated badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (rating != null && rating > 0) {
                    Text(
                        text = "★ ${"%.1f".format(rating)}",
                        color = ShiopaStyle.AccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (year != null && year > 0) {
                    Text(
                        text = year.toString(),
                        color = ShiopaStyle.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!typeName.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.CornerSm)
                            .background(ShiopaStyle.PillChipBackground)
                            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerSm)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = typeName,
                            color = ShiopaStyle.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerSm)
                        .background(ShiopaStyle.AccentPurple.copy(alpha = 0.14f))
                        .border(1.dp, ShiopaStyle.AccentPurple.copy(alpha = 0.35f), ShiopaStyle.CornerSm)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "● ${item.apiName}",
                        color = Color(0xFFA78BFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(ShiopaSpacing.Md))

            Text(
                text = item.name,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = listOfNotNull(
                    typeName?.let { "$it · " },
                    year?.let { "$it · " },
                    if (rating != null && rating > 0) "Rated ${"%.1f".format(rating)}" else null
                ).joinToString(""),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                color = ShiopaStyle.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md)
            ) {
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(ShiopaStyle.AccentMain)
                        .clickable(onClick = onPlayClick)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = ShiopaSpacing.Xl, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "▶", fontSize = 13.sp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Now",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, ShiopaStyle.GlassBorderHover, ShiopaStyle.ControlShape)
                        .clickable(onClick = onDetailsClick)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Details",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(if (bookmarked) ShiopaStyle.AccentMain.copy(alpha = 0.16f) else Color(0x1AFFFFFF))
                        .border(
                            1.dp,
                            if (bookmarked) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorderHover,
                            ShiopaStyle.ControlShape
                        )
                        .clickable(onClick = onToggleWatchlist)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (bookmarked) "✓ Watchlist" else "+ Watchlist",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }
    }
}
