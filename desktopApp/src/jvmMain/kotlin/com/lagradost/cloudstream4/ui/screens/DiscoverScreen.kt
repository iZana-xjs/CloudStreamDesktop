package com.lagradost.cloudstream4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.EmptyView
import com.lagradost.cloudstream4.ui.components.ErrorView
import com.lagradost.cloudstream4.ui.components.LoadingView
import com.lagradost.cloudstream4.ui.components.MediaCard
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiscoverScreen(
    initialGenre: String? = null,
    providerName: String? = null,
    sectionName: String? = null,
    sectionData: String? = null,
    horizontalImages: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Provider catalog mode: "Explore all" from a home section paginates
    // THAT provider's catalog. Without a provider this stays the legacy
    // global TMDB recommendations browser.
    if (providerName != null) {
        ProviderCatalogScreen(
            providerName = providerName,
            sectionName = sectionName,
            sectionData = sectionData,
            horizontalImages = horizontalImages,
            modifier = modifier
        )
        return
    }
    val scope = rememberCoroutineScope()
    val categories = listOf("Popular Movies", "Popular TV Shows", "Top Rated Movies", "Top Rated TV Shows")
    var selectedCategory by remember(initialGenre) {
        mutableStateOf(initialGenre?.takeIf { it in categories } ?: categories.first())
    }
    var categoryRequestId by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val mediaItems = remember { mutableStateListOf<SearchResponse>() }

    fun loadCategory(category: String) {
        selectedCategory = category
        isLoading = true
        errorMsg = null
        val requestId = categoryRequestId + 1
        categoryRequestId = requestId

        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val tmdb = APIHolder.allProviders.firstOrNull { it is TmdbProvider } ?: TmdbProvider()
                    val resp = tmdb.getMainPage(1, MainPageRequest("", "", false))
                    val allLists = resp?.items ?: emptyList()
                    val targetName = when (category) {
                        "Popular Movies" -> "Popular Movies"
                        "Popular TV Shows" -> "Popular Series"
                        "Top Rated Movies" -> "Top Movies"
                        "Top Rated TV Shows" -> "Top Series"
                        else -> null
                    }
                    val items = allLists.firstOrNull { it.name.equals(targetName, ignoreCase = true) }
                        ?.list.orEmpty()

                    withContext(Dispatchers.Main) {
                        if (requestId != categoryRequestId) return@withContext
                        mediaItems.clear()
                        mediaItems.addAll(items)
                        isLoading = false
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (requestId != categoryRequestId) return@withContext
                    errorMsg = e.message ?: "Failed to load category"
                    isLoading = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        loadCategory(selectedCategory)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Shiopa Category Pills
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories.size) { index ->
                val cat = categories[index]
                val isSelected = cat == selectedCategory
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.SurfaceCard)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorder,
                            shape = ShiopaStyle.PillShape
                        )
                        .clickable { loadCategory(cat) }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = cat,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else ShiopaStyle.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            isLoading -> {
                LoadingView(message = "Discovering $selectedCategory...")
            }
            errorMsg != null -> {
                ErrorView(
                    title = "Error loading category",
                    details = errorMsg,
                    onRetry = { loadCategory(selectedCategory) }
                )
            }
            mediaItems.isEmpty() -> {
                EmptyView(
                    title = "No media found in this category",
                    description = "Try selecting another category or check your provider settings."
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 175.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(mediaItems) { item ->
                        val year = when (item) {
                            is com.lagradost.cloudstream3.MovieSearchResponse -> item.year
                            is com.lagradost.cloudstream3.TvSeriesSearchResponse -> item.year
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

/**
 * UI-agnostic paginated catalog loader: follows a provider's pages until it
 * reports no more, an empty page arrives, or pages stop yielding new items
 * (broken hasNext guard). Failed pages keep prior items and stay retryable
 * from the same page. Dedupe uses stable provider identity, posters resolve
 * against the provider site. Fully unit-tested with mocked pages.
 */
class CatalogPageLoader(
    private val fetchPage: suspend (page: Int) -> HomePageResponse?,
    private val posterBaseUrl: String = ""
) {
    private val seenKeys = mutableSetOf<String>()
    val items = mutableListOf<SearchResponse>()
    var currentPage = 0
        private set
    var hasNext = true
        private set
    var lastError: String? = null
        private set
    var loading = false
        private set
    private var emptyStreak = 0

    suspend fun loadNext(): Boolean {
        if (!hasNext || loading) return hasNext
        loading = true
        try {
            val next = currentPage + 1
            val resp = fetchPage(next)
            if (resp == null) {
                hasNext = false
                return false
            }
            currentPage = next
            val fresh = resp.items.flatMap { it.list }.map { item ->
                item.apply { posterUrl = absolutePosterUrl(posterUrl, posterBaseUrl) }
            }.filter { seenKeys.add(searchResultKey(it.apiName, it.url)) }
            if (fresh.isNotEmpty()) {
                items += fresh
                emptyStreak = 0
            } else {
                emptyStreak++
            }
            hasNext = resp.hasNext && resp.items.flatMap { it.list }.isNotEmpty() && emptyStreak < 2
            lastError = null
            return hasNext
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            lastError = e.message ?: "Failed to load page"
            return hasNext
        } finally {
            loading = false
        }
    }

    fun reset() {
        seenKeys.clear()
        items.clear()
        currentPage = 0
        hasNext = true
        lastError = null
        loading = false
        emptyStreak = 0
    }
}

/**
 * Provider-specific catalog browser ("Explore all" destination): category
 * pills from the provider's own mainPage, incremental page loading while
 * scrolling plus an explicit control, and loading/end/error states. Never
 * the global TMDB recommendations.
 */
@Composable
private fun ProviderCatalogScreen(
    providerName: String,
    sectionName: String?,
    sectionData: String?,
    horizontalImages: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val provider = remember(providerName) {
        APIHolder.getApiFromNameNull(providerName)
            ?: APIHolder.allProviders.firstOrNull { it.name == providerName }
    }
    val categories = remember(provider) {
        provider?.mainPage?.takeIf { it.isNotEmpty() }
            ?: listOf(MainPageData(sectionName ?: providerName, sectionData ?: "", horizontalImages))
    }
    var selectedCategory by remember(provider) {
        mutableStateOf(categories.firstOrNull { it.name == sectionName } ?: categories.firstOrNull())
    }
    val catalogItems = remember { mutableStateListOf<SearchResponse>() }
    var pagingError by remember { mutableStateOf<String?>(null) }
    var endReached by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    val loader = remember(provider, selectedCategory) {
        CatalogPageLoader(
            fetchPage = { page ->
                val api = provider
                    ?: throw IllegalStateException("Provider $providerName is not installed.")
                val section = selectedCategory
                    ?: throw IllegalStateException("No catalog section.")
                api.getMainPage(page, MainPageRequest(section.name, section.data, section.horizontalImages))
            },
            posterBaseUrl = provider?.mainUrl.orEmpty()
        )
    }
    val gridState = rememberLazyGridState()

    fun loadMore() {
        scope.launch {
            loadingMore = true
            try {
                loader.loadNext()
            } finally {
                catalogItems.clear()
                catalogItems.addAll(loader.items)
                pagingError = loader.lastError
                endReached = !loader.hasNext
                initialLoading = false
                loadingMore = false
            }
        }
    }

    LaunchedEffect(provider, selectedCategory) {
        catalogItems.clear()
        pagingError = null
        endReached = false
        initialLoading = true
        loadMore()
    }

    // Infinite scroll: fetch ahead before the user reaches the end.
    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, catalogItems.size) {
        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@LaunchedEffect
        if (catalogItems.isNotEmpty() && last >= catalogItems.size - 6 &&
            loader.hasNext && !loader.loading
        ) {
            loadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = providerName,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = selectedCategory?.name ?: "Catalog",
            fontSize = 13.sp,
            color = ShiopaStyle.TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (categories.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(categories.size) { index ->
                    val cat = categories[index]
                    val isSelected = cat.name == selectedCategory?.name &&
                        cat.data == selectedCategory?.data
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.SurfaceCard)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorder,
                                shape = ShiopaStyle.PillShape
                            )
                            .clickable { selectedCategory = cat }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = cat.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else ShiopaStyle.TextSecondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        when {
            initialLoading -> {
                LoadingView(message = "Loading ${selectedCategory?.name ?: "catalog"}...")
            }
            catalogItems.isEmpty() && pagingError != null -> {
                ErrorView(
                    title = "Error loading catalog",
                    details = pagingError,
                    onRetry = { loadMore() }
                )
            }
            catalogItems.isEmpty() -> {
                EmptyView(
                    title = "No titles in this catalog",
                    description = "This provider did not expose any titles for this section."
                )
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 175.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(catalogItems, key = { searchResultKey(it.apiName, it.url) }) { item ->
                        val year = when (item) {
                            is com.lagradost.cloudstream3.MovieSearchResponse -> item.year
                            is com.lagradost.cloudstream3.TvSeriesSearchResponse -> item.year
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
                                NavigationManager.navigate(mediaDetailsDestination(item))
                            }
                        )
                    }

                    if (loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = ShiopaStyle.AccentCyan
                                )
                            }
                        }
                    }

                    if (pagingError != null && !loadingMore) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            ) {
                                Text(
                                    "Couldn't load the next page: $pagingError",
                                    fontSize = 12.sp,
                                    color = ShiopaStyle.AccentGold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { loadMore() }) {
                                    Text("Retry", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (endReached && pagingError == null) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                "End of catalog · ${catalogItems.size} titles",
                                fontSize = 12.sp,
                                color = ShiopaStyle.TextMuted,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
