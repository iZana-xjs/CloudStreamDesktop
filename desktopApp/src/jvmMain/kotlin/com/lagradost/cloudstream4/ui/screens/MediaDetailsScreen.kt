package com.lagradost.cloudstream4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.getImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.getMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.getTMDbId
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.BookmarkStatus
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.downloads.DesktopDownloadManager
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.navigation.PlaybackSource
import com.lagradost.cloudstream4.navigation.handleSourceListKey
import com.lagradost.cloudstream4.navigation.sortPlaybackSources
import com.lagradost.cloudstream4.ui.components.ErrorView
import com.lagradost.cloudstream4.ui.components.LoadingView
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@Composable
fun MediaDetailsScreen(
    url: String,
    apiName: String,
    initialTitle: String? = null,
    initialPosterUrl: String? = null,
    resumePositionMs: Long = 0L,
    resumeEpisodeIndex: Int? = null,
    resumeSeasonIndex: Int? = null,
    resumeEpisodeTitle: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var mediaResponse by remember { mutableStateOf<LoadResponse?>(null) }

    var selectedSeason by remember { mutableIntStateOf(1) }
    var detailsRequestId by remember { mutableIntStateOf(0) }
    var identityWarning by remember { mutableStateOf<String?>(null) }
    var titleCandidates by remember { mutableStateOf<List<SearchResponse>?>(null) }
    var findingTitle by remember { mutableStateOf(false) }
    var bookmarkStatus by remember {
        mutableStateOf(DesktopStorage.getBookmark(url, apiName)?.status)
    }

    // Source resolution state
    var isResolvingSources by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var resolvingEpisode by remember { mutableStateOf<Episode?>(null) }
    val foundSources = remember { mutableStateListOf<ExtractorLink>() }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var pendingResumePositionMs by remember(url, apiName, resumePositionMs) {
        mutableStateOf(resumePositionMs.takeIf { it > 5_000L })
    }
    var resumeAttempted by remember(url, apiName, resumePositionMs) { mutableStateOf(false) }

    fun loadDetails() {
        isLoading = true
        errorMsg = null
        identityWarning = null
        titleCandidates = null
        findingTitle = false
        // Guard against out-of-order loads: loadDetails launches into the
        // screen scope, so LaunchedEffect cancellation on navigation does NOT
        // stop an in-flight load — without this id, the slowest load wins and
        // a previously clicked title can paint over the current page.
        val requestId = ++detailsRequestId
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val api = APIHolder.getApiFromNameNull(apiName)
                        ?: APIHolder.allProviders.firstOrNull { it.name == apiName }
                        ?: throw IllegalStateException("Provider $apiName is not loaded. Enable it in Extensions and retry.")

                    val result = api.load(url)
                    withContext(Dispatchers.Main) {
                        if (requestId != detailsRequestId) return@withContext
                        if (result != null) {
                            // Identity gate: the clicked card's provider, URL,
                            // title, and poster must match what actually loaded.
                            // A mismatch means the click would open a different
                            // title — surface a recoverable error instead of
                            // opening it silently.
                            when (val check = validateLoadedIdentity(
                                apiName, url, initialTitle, initialPosterUrl,
                                result.apiName, result.url, result.name, result.posterUrl,
                                siteBaseUrl = api.mainUrl
                            )) {
                                is DetailIdentityCheck.Match -> mediaResponse = result
                                is DetailIdentityCheck.ProviderMismatch -> {
                                    System.err.println(
                                        "Detail identity mismatch: expected provider " +
                                            "${check.expectedApi} but loaded ${check.actualApi}"
                                    )
                                    errorMsg = "Opened the wrong title: this page came from " +
                                        "“${check.actualApi}” instead of “${check.expectedApi}”. " +
                                        "Please retry or pick the result again."
                                }
                                is DetailIdentityCheck.TitleMismatch -> {
                                    // Same provider and URL, conflicting titles: the
                                    // provider's own catalog is cross-wired for this
                                    // entry (proven live: search "Dark" vs loaded
                                    // "Dark Shadows" on one URL). Blocking would
                                    // hide playable content, so open the page and
                                    // say plainly what happened instead.
                                    System.err.println(
                                        "Detail identity mismatch: expected “${check.expectedTitle}” " +
                                            "but loaded “${check.actualTitle}” ($apiName)"
                                    )
                                    mediaResponse = result
                                    identityWarning = "You searched for “${check.expectedTitle}”, " +
                                        "but this provider page is titled “${check.actualTitle}”. " +
                                        "Content below comes from that page."
                                }
                            }
                        } else {
                            errorMsg = "Provider returned no details."
                        }
                        isLoading = false
                    }
                }
            } catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    if (requestId != detailsRequestId) return@withContext
                    errorMsg = e.message ?: "Failed to load details"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(url, apiName) {
        loadDetails()
    }

    fun startResolvingLinks(ep: Episode? = null, resumeAtMs: Long? = pendingResumePositionMs) {
        pendingResumePositionMs = resumeAtMs?.takeIf { it > 5_000L }
        resolvingEpisode = ep
        isResolvingSources = true
        sourceError = null
        foundSources.clear()
        showSourcesDialog = true

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val api = APIHolder.getApiFromNameNull(apiName)
                        ?: APIHolder.allProviders.firstOrNull { it.name == apiName }
                        ?: throw IllegalStateException("Provider $apiName is not loaded. Enable it in Extensions and retry.")

                    val dataToLoad = ep?.data ?: (mediaResponse as? MovieLoadResponse)?.dataUrl ?: url

                    api.loadLinks(
                        data = dataToLoad,
                        isCasting = false,
                        subtitleCallback = { },
                        callback = { link ->
                            scope.launch(Dispatchers.Main) {
                                if (foundSources.none { it.url == link.url }) {
                                    foundSources.add(link)
                                }
                            }
                        }
                    )
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    sourceError = "${e::class.simpleName}: ${e.message ?: "The provider could not resolve links."}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isResolvingSources = false
                }
            }
        }
    }

    if (isLoading) {
        LoadingView(message = "Loading media details...")
        return
    }

    if (errorMsg != null || mediaResponse == null) {
        ErrorView(
            title = "Could not load details",
            details = errorMsg,
            onRetry = { loadDetails() }
        )
        return
    }

    val item = mediaResponse!!
    val title = item.name
    val poster = item.posterUrl ?: initialPosterUrl
    val plot = item.plot
    val year = item.year
    val rating = item.score?.toDouble(10)
    val duration = item.duration

    val episodes = (item as? TvSeriesLoadResponse)?.episodes ?: emptyList()
    val availableSeasons = episodes.mapNotNull { it.season }.distinct().sorted()

    fun playResolvedSource(source: ExtractorLink, startAtMs: Long = 0L) {
        val episode = resolvingEpisode
        NavigationManager.navigate(
            NavDestination.Player(
                url = url,
                title = title,
                episodeTitle = episode?.name,
                mediaUrl = source.url,
                mediaHeaders = source.getAllHeaders(),
                apiName = apiName,
                episodeIndex = episode?.episode,
                seasonIndex = episode?.season,
                startPositionMs = startAtMs,
                posterUrl = poster,
                tvTypeName = item.type.name,
                year = (item as? MovieLoadResponse)?.year ?: (item as? TvSeriesLoadResponse)?.year,
                malId = item.getMalId()?.toIntOrNull(),
                tmdbId = item.getTMDbId(),
                imdbId = item.getImdbId(),
                absoluteEpisodeIndex = episode?.let { ep ->
                    episodes.indexOfFirst { it.season == ep.season && it.episode == ep.episode }
                        .takeIf { it >= 0 }?.plus(1)
                },
                hasNextEpisode = episode?.let { ep ->
                    val idx = episodes.indexOfFirst { it.season == ep.season && it.episode == ep.episode }
                    idx >= 0 && idx < episodes.size - 1
                } == true,
                sources = sortPlaybackSources(foundSources.toList(), { it.name }, { it.quality }).map {
                    PlaybackSource(
                        it.name, it.url, it.quality,
                        if (it.isM3u8) "HLS" else if (it.isDash) "DASH" else "Direct",
                        it.getAllHeaders()
                    )
                }
            )
        )
    }

    LaunchedEffect(mediaResponse, resumePositionMs, resumeEpisodeIndex, resumeSeasonIndex, resumeEpisodeTitle) {
        if (resumePositionMs <= 5_000L || resumeAttempted) return@LaunchedEffect
        resumeAttempted = true
        val resumeEpisode = if (item is TvSeriesLoadResponse) {
            episodes.firstOrNull { episode ->
                if (resumeEpisodeIndex != null) {
                    episode.episode == resumeEpisodeIndex &&
                        (resumeSeasonIndex == null || episode.season == resumeSeasonIndex)
                } else {
                    !resumeEpisodeTitle.isNullOrBlank() && episode.name.equals(resumeEpisodeTitle, ignoreCase = true)
                }
            }
        } else null

        if (item is TvSeriesLoadResponse && resumeEpisode == null) {
            NotificationService.warning(
                "Could not resume saved episode",
                "The saved season and episode could not be found. Open the episode list and choose an episode to play."
            )
            return@LaunchedEffect
        }
        startResolvingLinks(resumeEpisode, resumePositionMs)
    }

    LaunchedEffect(pendingResumePositionMs) {
        val resumeAt = pendingResumePositionMs ?: return@LaunchedEffect
        // Continue as soon as the first playable mirror is available. Give the
        // provider a brief moment to return nearby mirrors so quality selection
        // can still choose the best result without waiting for every resolver.
        snapshotFlow { foundSources.isNotEmpty() }.first { it }
        delay(300)
        if (pendingResumePositionMs != resumeAt) return@LaunchedEffect
        val sources = foundSources.toList()
        val sdrSources = sources.filter { source ->
            val label = "${source.name} ${source.source}".lowercase()
            listOf("hdr", "dolby vision", "dolbyvision", " dv ", "hdr10", "hlg")
                .none(label::contains) && !Regex("\\bdv\\b").containsMatchIn(label)
        }
        val source = sdrSources.firstOrNull { it.quality == 1080 }
            ?: sdrSources.filter { it.quality in 1..1080 }.maxByOrNull { it.quality }
            ?: sdrSources.minByOrNull { it.quality }
            ?: sources.filter { it.quality in 1..1080 }.minByOrNull { it.quality }
            ?: sources.minByOrNull { it.quality }
            ?: return@LaunchedEffect
        pendingResumePositionMs = null
        showSourcesDialog = false
        playResolvedSource(source, resumeAt)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShiopaStyle.CanvasBlack)) {
        val isNarrow = maxWidth < 768.dp

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Edge-to-Edge Shiopa Cinematic Hero Header
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isNarrow) 460.dp else 420.dp)
                ) {
                    // Edge-to-Edge Backdrop
                    if (!poster.isNullOrBlank()) {
                        AsyncImage(
                            model = poster,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Multi-layer ambient gradient overlay fading into CanvasBlack
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color(0x70090A10),
                                        Color(0xB0090A10),
                                        Color(0xF5090A10),
                                        ShiopaStyle.CanvasBlack
                                    )
                                )
                            )
                    )

                    // Horizontal gradient for text contrast
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xCC090A10),
                                        Color(0x77090A10),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Header Content
                    if (isNarrow) {
                        // Narrow / Mobile stack
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ShiopaSpacing.Xl),
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            DetailsMetadataHeader(
                                title = title,
                                year = year,
                                rating = rating,
                                duration = duration,
                                apiName = apiName,
                                onPlayClick = {
                                    if (episodes.isNotEmpty()) {
                                        startResolvingLinks(episodes.firstOrNull())
                                    } else {
                                        startResolvingLinks()
                                    }
                                },
                                bookmarkStatus = bookmarkStatus,
                                onBookmarkChange = { status ->
                                    bookmarkStatus = status
                                    if (status != null) {
                                        DesktopStorage.saveBookmark(
                                            BookmarkItem(
                                                url = url,
                                                apiName = apiName,
                                                title = title,
                                                posterUrl = poster,
                                                year = year,
                                                status = status
                                            )
                                        )
                                        NotificationService.success("Added to ${status.name.replace("_", " ")}")
                                    } else {
                                        DesktopStorage.removeBookmark(url, apiName)
                                        NotificationService.info("Removed from Watchlist")
                                    }
                                }
                            )
                        }
                    } else {
                        // Desktop side-by-side poster and metadata
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = ShiopaSpacing.PageInsetWide, vertical = ShiopaSpacing.Xxl),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Rounded Poster Card
                            Surface(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(290.dp)
                                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg),
                                shape = ShiopaStyle.CornerLg,
                                color = ShiopaStyle.SurfaceCard
                            ) {
                                if (!poster.isNullOrBlank()) {
                                    AsyncImage(
                                        model = poster,
                                        contentDescription = title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(36.dp))

                            // Metadata & Action Buttons
                            DetailsMetadataHeader(
                                title = title,
                                year = year,
                                rating = rating,
                                duration = duration,
                                apiName = apiName,
                                onPlayClick = {
                                    if (episodes.isNotEmpty()) {
                                        startResolvingLinks(episodes.firstOrNull())
                                    } else {
                                        startResolvingLinks()
                                    }
                                },
                                bookmarkStatus = bookmarkStatus,
                                onBookmarkChange = { status ->
                                    bookmarkStatus = status
                                    if (status != null) {
                                        DesktopStorage.saveBookmark(
                                            BookmarkItem(
                                                url = url,
                                                apiName = apiName,
                                                title = title,
                                                posterUrl = poster,
                                                year = year,
                                                status = status
                                            )
                                        )
                                        NotificationService.success("Added to ${status.name.replace("_", " ")}")
                                    } else {
                                        DesktopStorage.removeBookmark(url, apiName)
                                        NotificationService.info("Removed from Watchlist")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Synopsis Card
            if (!plot.isNullOrBlank()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isNarrow) 20.dp else 48.dp, vertical = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(ShiopaStyle.GlassSurface)
                            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(16.dp))
                            .padding(22.dp)
                    ) {
                        Column {
                            Text(
                                text = "Synopsis",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = plot,
                                fontSize = 13.5.sp,
                                color = Color(0xFFD4D4E2),
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }

            // TV Series Episodes Section
            if (episodes.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (isNarrow) 20.dp else 48.dp,
                            vertical = 16.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Episodes (${episodes.size})",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                                color = Color.White
                            )

                            // Season selector chips
                            if (availableSeasons.size > 1) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)) {
                                    items(availableSeasons) { seasonNum ->
                                        val isSelected = seasonNum == selectedSeason
                                        Box(
                                            modifier = Modifier
                                                .clip(ShiopaStyle.PillShape)
                                                .background(
                                                    if (isSelected) ShiopaStyle.AccentMain
                                                    else ShiopaStyle.PillChipBackground
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.PillChipBorder,
                                                    ShiopaStyle.PillShape
                                                )
                                                .clickable { selectedSeason = seasonNum }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .padding(horizontal = 16.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "Season $seasonNum",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 12.5.sp,
                                                color = if (isSelected) Color.White else ShiopaStyle.TextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val filteredEpisodes = episodes.filter { it.season == null || it.season == selectedSeason }
                        for (ep in filteredEpisodes) {
                            EpisodeRowItem(
                                episode = ep,
                                onPlayClick = { startResolvingLinks(ep) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(56.dp))
            }
        }

        // Cross-wired provider entry: same URL, conflicting titles. The page
        // opens (its episodes and sources are usable — season/episode choice
        // stays entirely with the viewer) with this plain warning. The
        // optional lookup below re-searches the SAME provider for the exact
        // clicked title so the viewer can pick the right entry themselves.
        if (identityWarning != null) {
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    color = Color(0xCC1A1A22),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, Color(0x66F59E0B), RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = identityWarning!!,
                                color = Color(0xFFFBBF24),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { identityWarning = null }) {
                                Text("Dismiss", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (!initialTitle.isNullOrBlank() && titleCandidates == null && !findingTitle) {
                            TextButton(onClick = {
                                findingTitle = true
                                scope.launch {
                                    val found = withContext(Dispatchers.IO) {
                                        try {
                                            val searchApi = APIHolder.getApiFromNameNull(apiName)
                                                ?: return@withContext null
                                            val res = withTimeoutOrNull(15_000L) {
                                                searchApi.search(initialTitle, 1)?.items.orEmpty()
                                            }
                                            pickExactTitleMatches(res.orEmpty(), initialTitle, url)
                                        } catch (_: Throwable) {
                                            null
                                        }
                                    }
                                    findingTitle = false
                                    titleCandidates = found
                                }
                            }) {
                                Text("Find exact title", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (findingTitle) {
                            Text("Searching this provider for the exact title…", color = ShiopaStyle.TextMuted, fontSize = 12.sp)
                        }
                        titleCandidates?.let { candidates ->
                            if (candidates.isEmpty()) {
                                Text(
                                    "No exact title match on this provider.",
                                    color = ShiopaStyle.TextMuted,
                                    fontSize = 12.sp
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Pick the right entry:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    for (candidate in candidates) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth().clickable {
                                                NavigationManager.navigate(mediaDetailsDestination(candidate))
                                            }
                                        ) {
                                            Text(
                                                text = candidate.name,
                                                color = Color.White,
                                                fontSize = 12.5.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text("Open →", color = ShiopaStyle.AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // High-end Streaming Sources Dialog
    if (showSourcesDialog) {
        val sortedSources = sortPlaybackSources(foundSources.toList(), { it.name }, { it.quality })
        AlertDialog(
            onDismissRequest = { showSourcesDialog = false },
            modifier = Modifier.widthIn(max = 640.dp),
            containerColor = Color(0xFF10121D),
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE50914)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Available Streams",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (isResolvingSources && foundSources.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth().padding(28.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ShiopaStyle.AccentGreen)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Extracting video links from provider...", color = Color.White, fontSize = 13.sp)
                        }
                    } else if (foundSources.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AE11D48))
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sourceError ?: "No streaming links found. Try another extension provider.",
                                color = Color(0xFFFB7185),
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Text(
                            text = "${sortedSources.size} high-definition mirrors resolved:",
                            fontSize = 12.sp,
                            color = ShiopaStyle.TextSecondary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        Text(
                            text = "Recommended for most PCs: SDR 1080p when available. 4K/HDR sources remain selectable.",
                            fontSize = 11.sp,
                            color = ShiopaStyle.TextMuted,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        // Scroll state is keyed by title/episode so switching content
                        // starts at the top; incremental link arrivals keep position.
                        val sourceListFocus = remember { FocusRequester() }
                        val sourceListState = remember(url, apiName, resolvingEpisode) { LazyListState() }
                        LaunchedEffect(showSourcesDialog) {
                            if (showSourcesDialog) {
                                sourceListState.scrollToItem(0)
                                runCatching { sourceListFocus.requestFocus() }
                            }
                        }
                        // List and scrollbar share one measured viewport capped to the
                        // actual dialog space: no dead area, no desync in normal /
                        // maximized / fullscreen / resized windows.
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val sourceViewportMax = (maxHeight * 0.62f).coerceIn(240.dp, 560.dp)
                            val sourceLayout = sourceListState.layoutInfo
                            val totalStreams = sourceLayout.totalItemsCount
                            val firstStreamIdx = sourceLayout.visibleItemsInfo.firstOrNull()?.index ?: 0
                            val lastStreamIdx = sourceLayout.visibleItemsInfo.lastOrNull()?.index ?: -1
                            if (firstStreamIdx > 0) {
                                Text(
                                    text = "↑ Scroll up — $firstStreamIdx above",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ShiopaStyle.AccentMain,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            // Overlay pattern: the list determines the height (wraps
                            // when short, capped when long); the scrollbar is sized
                            // to the list's MEASURED height, never the dialog's, so
                            // no dead area and the thumb tracks the viewport.
                            // Shown only when the content actually scrolls.
                            var sourceListHeightPx by remember { mutableIntStateOf(0) }
                            val sourceScrollable = sourceListState.canScrollForward || sourceListState.canScrollBackward
                            Box(modifier = Modifier.fillMaxWidth()) {
                            LazyColumn(
                                state = sourceListState,
                                modifier = Modifier.fillMaxWidth().heightIn(max = sourceViewportMax)
                                    .onSizeChanged { sourceListHeightPx = it.height }
                                    .focusRequester(sourceListFocus).focusable()
                                    .onPreviewKeyEvent { handleSourceListKey(it, sourceListState, scope) },
                                contentPadding = PaddingValues(end = 16.dp, top = 2.dp, bottom = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                            items(sortedSources, key = { it.url }) { source ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ShiopaStyle.GlassSurface)
                                    .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                        Text(
                                            text = source.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0x2210B981))
                                                    .border(1.dp, ShiopaStyle.AccentGreen, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "${source.quality}p",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ShiopaStyle.AccentGreen
                                                )
                                            }
                                            Text(
                                                text = if (source.isM3u8) "HLS Stream" else "Direct Video",
                                                fontSize = 11.5.sp,
                                                color = ShiopaStyle.TextMuted
                                            )
                                            if (source.url == sortedSources.firstOrNull()?.url) {
                                                Text(
                                                    text = "Recommended",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ShiopaStyle.AccentMain
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(ShiopaStyle.ControlShape)
                                                .background(ShiopaStyle.AccentMain)
                                                .clickable {
                                                    showSourcesDialog = false
                                                    val savedPosition = pendingResumePositionMs ?: 0L
                                                    pendingResumePositionMs = null
                                                    playResolvedSource(source, savedPosition)
                                                }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            Text("Play", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(ShiopaStyle.ControlShape)
                                                .background(Color(0x1AFFFFFF))
                                                .border(1.dp, ShiopaStyle.GlassBorderHover, ShiopaStyle.ControlShape)
                                                .clickable {
                                                    DesktopDownloadManager.startDownload(
                                                        title = title,
                                                        episodeTitle = resolvingEpisode?.name,
                                                        sourceUrl = source.url,
                                                        posterUrl = poster,
                                                        headers = source.getAllHeaders()
                                                    )
                                                    showSourcesDialog = false
                                                }
                                                .pointerHoverIcon(PointerIcon.Hand)
                                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text("Download", fontSize = 12.5.sp, color = Color.White)
                                        }
                                    }
                                }
                                }
                            }
                            }
                            if (sourceScrollable && sourceListHeightPx > 0) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(sourceListState),
                                    style = ScrollbarStyle(
                                        minimalHeight = 48.dp,
                                        thickness = 10.dp,
                                        shape = RoundedCornerShape(5.dp),
                                        hoverDurationMillis = 300,
                                        unhoverColor = ShiopaStyle.AccentMain.copy(alpha = 0.45f),
                                        hoverColor = ShiopaStyle.AccentMain
                                    ),
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .height(with(LocalDensity.current) { sourceListHeightPx.toDp() })
                                        .pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                            }
                            if (lastStreamIdx < totalStreams - 1) {
                                val remainingStreams = totalStreams - 1 - lastStreamIdx
                                Text(
                                    text = "↓ Scroll for more — $remainingStreams of $totalStreams remaining",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ShiopaStyle.AccentMain,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcesDialog = false }) {
                    Text("Close", color = ShiopaStyle.TextMuted, fontSize = 13.sp)
                }
            }
        )
    }
}

@Composable
private fun DetailsMetadataHeader(
    title: String,
    year: Int?,
    rating: Double?,
    duration: Int?,
    apiName: String,
    onPlayClick: () -> Unit,
    bookmarkStatus: BookmarkStatus?,
    onBookmarkChange: (BookmarkStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            ),
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Real metadata badges only — rating (gold), year, duration, provider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (rating != null && rating > 0) {
                Text(
                    text = "★ ${"%.1f".format(rating)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = ShiopaStyle.AccentGold
                )
            }

            if (year != null && year > 0) {
                Text(text = "$year", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD4D4E2))
            }

            if (duration != null && duration > 0) {
                Text(text = "$duration min", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFD4D4E2))
            }

            // Provider pill (violet = provider identity)
            Box(
                modifier = Modifier
                    .clip(ShiopaStyle.CornerSm)
                    .background(ShiopaStyle.AccentPurple.copy(alpha = 0.14f))
                    .border(1.dp, ShiopaStyle.AccentPurple.copy(alpha = 0.35f), ShiopaStyle.CornerSm)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "● $apiName",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFA78BFA)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // High-Contrast Solid White Action Pill
            // Primary Play — main accent
            Box(
                modifier = Modifier
                    .clip(ShiopaStyle.ControlShape)
                    .background(ShiopaStyle.AccentMain)
                    .clickable { onPlayClick() }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = ShiopaSpacing.Xl, vertical = 13.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("▶", fontSize = 14.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Watchlist Pill with Dropdown
            var showBookmarkMenu by remember { mutableStateOf(false) }
            Box {
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, ShiopaStyle.GlassBorderHover, ShiopaStyle.ControlShape)
                        .clickable { showBookmarkMenu = true }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 22.dp, vertical = 13.dp)
                ) {
                    Text(
                        text = if (bookmarkStatus != null) "✓ ${bookmarkStatus.name.replace("_", " ")}" else "+ Watchlist",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }

                DropdownMenu(
                    expanded = showBookmarkMenu,
                    onDismissRequest = { showBookmarkMenu = false }
                ) {
                    for (status in BookmarkStatus.values()) {
                        DropdownMenuItem(
                            text = { Text(status.name.replace("_", " ")) },
                            onClick = {
                                onBookmarkChange(status)
                                showBookmarkMenu = false
                            }
                        )
                    }
                    if (bookmarkStatus != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from List", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onBookmarkChange(null)
                                showBookmarkMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowItem(
    episode: Episode,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShiopaStyle.CornerLg)
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg)
            .clickable { onPlayClick() }
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(ShiopaSpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 70.dp)
                    .clip(ShiopaStyle.CornerMd)
                    .background(ShiopaStyle.SurfaceCard),
                contentAlignment = Alignment.Center
            ) {
                if (!episode.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.posterUrl,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.dp, Color(0x40FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("▶", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.width(ShiopaSpacing.Lg))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${episode.episode?.let { "Episode $it: " } ?: ""}${episode.name ?: "Untitled Episode"}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!episode.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = episode.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = ShiopaStyle.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
