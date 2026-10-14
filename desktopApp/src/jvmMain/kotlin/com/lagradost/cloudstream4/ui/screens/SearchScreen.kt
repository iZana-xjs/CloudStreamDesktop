package com.lagradost.cloudstream4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream4.core.DesktopTmdbProvider
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.EmptyView
import com.lagradost.cloudstream4.ui.components.LoadingView
import com.lagradost.cloudstream4.ui.components.MediaCard
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Live-suggestion selection decision. Returns the exact (fieldText,
 * submittedQuery) pair so the visible field and the search that runs can
 * never diverge — always the clicked suggestion's title, never the old
 * text. Pure, for unit tests.
 */
fun suggestionSelectionPair(suggestionTitle: String): Pair<String, String> =
    suggestionTitle to suggestionTitle

/**
 * Stable grid identity: the same URL string from two different providers can
 * point at different catalog entries (e.g. relative paths), so identity must
 * include the owning provider. Fixes cards showing one title while opening
 * another after list updates.
 */
fun searchResultKey(apiName: String, url: String): String = "$apiName::$url"

/**
 * Exact-title match for a selected suggestion: returns the single provider
 * result whose normalized title equals the clicked title (year/type used to
 * disambiguate when provided). Never returns the first loose match — null
 * means "no reliable match", which the UI surfaces instead of opening a
 * different title. Pure, for unit tests.
 */
fun findExactProviderMatch(
    results: List<SearchResponse>,
    title: String,
    year: Int? = null,
    type: String? = null
): SearchResponse? {
    val normTitle = normalizeIdentityText(title)
    if (normTitle.isBlank()) return null
    val exact = results.filter { normalizeIdentityText(it.name) == normTitle }
    if (exact.isEmpty()) return null
    if (exact.size == 1) return exact.single()
    // Disambiguate by year / media type when the data offers it.
    if (year != null) {
        val byYear = exact.filter { (it as? com.lagradost.cloudstream3.MovieSearchResponse)?.year == year || (it as? com.lagradost.cloudstream3.TvSeriesSearchResponse)?.year == year }
        if (byYear.size == 1) return byYear.single()
    }
    if (!type.isNullOrBlank()) {
        val byType = exact.filter { it.type?.name?.equals(type, ignoreCase = true) == true }
        if (byType.size == 1) return byType.single()
    }
    // Still ambiguous → no reliable single match.
    return null
}

/**
 * Providers sometimes return root-relative poster paths. Resolve them against
 * the provider's own site so cards don't render broken or mismatched images.
 * Absolute, protocol-relative, and data: URLs pass through untouched.
 */
fun absolutePosterUrl(posterUrl: String?, mainUrl: String): String? {
    if (posterUrl.isNullOrBlank()) return null
    val trimmed = posterUrl.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://") ||
        trimmed.startsWith("data:")
    ) {
        return trimmed
    }
    if (trimmed.startsWith("//")) return "https:$trimmed"
    if (mainUrl.isBlank() || mainUrl.equals("NONE", ignoreCase = true)) return trimmed
    val base = mainUrl.trimEnd('/')
    return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
}

/** One provider request never blocks the others for longer than this. */
const val SEARCH_PROVIDER_TIMEOUT_MS = 15_000L

/**
 * Classified single-provider search outcome. Empty (but successful) results
 * are NOT failures — only timeouts and errors are reported. Cancellation
 * (stale query) never produces an outcome at all.
 */
sealed interface ProviderSearchOutcome {
    val providerName: String
    data class Success(override val providerName: String, val results: List<SearchResponse>) : ProviderSearchOutcome
    data class NoResults(override val providerName: String) : ProviderSearchOutcome
    data class TimedOut(override val providerName: String) : ProviderSearchOutcome
    data class Errored(override val providerName: String) : ProviderSearchOutcome
    /**
     * The provider never implemented search() (throws NotImplementedError).
     * Not a failure of this query — reported once as info, never warned.
     */
    data class Unsupported(override val providerName: String) : ProviderSearchOutcome
    val label: String
        get() = when (this) {
            is Success -> providerName
            is NoResults -> "$providerName (no results)"
            is TimedOut -> "$providerName (timed out)"
            is Errored -> "$providerName (error)"
            is Unsupported -> "$providerName (search not supported)"
        }
    val isFailure: Boolean get() = this is TimedOut || this is Errored
}

/**
 * Bounded single-provider search with poster normalization. Always call the
 * paginated API: many current CloudStream extensions override
 * search(query, page) while leaving the legacy search(query) method
 * unimplemented. Page 1 also works for legacy providers because MainAPI's
 * default paginated implementation delegates to the old method.
 * Unusable entries
 * (blank URL — nothing navigable) are dropped, never shown as broken cards.
 * Cancellation propagates untouched (caller treats it as stale, never as
 * failure). Timeout is injectable for fast unit tests.
 */
suspend fun searchOneProvider(
    provider: MainAPI,
    query: String,
    timeoutMs: Long = SEARCH_PROVIDER_TIMEOUT_MS
): ProviderSearchOutcome {
    return try {
        val results = withTimeoutOrNull(timeoutMs) {
            provider.search(query, 1)?.items?.map { item ->
                item.apply { posterUrl = absolutePosterUrl(posterUrl, provider.mainUrl) }
            }.orEmpty()
        } ?: return ProviderSearchOutcome.TimedOut(provider.name)
        val usable = results.filter { it.url.isNotBlank() }
        if (usable.isEmpty()) ProviderSearchOutcome.NoResults(provider.name)
        else ProviderSearchOutcome.Success(provider.name, usable)
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotImplementedError) {
        // search() not overridden by this provider: a permanent capability
        // gap, not a failure of this query. Silent here; surfaced once as
        // muted info so it never reads as "did not respond".
        ProviderSearchOutcome.Unsupported(provider.name)
    } catch (e: Throwable) {
        System.err.println("Search failed for provider ${provider.name}: ${e.message}")
        ProviderSearchOutcome.Errored(provider.name)
    }
}

/**
 * Adds only entries whose identity key is unseen, preserving arrival order.
 * Pure and unit-tested (incl. out-of-order provider completion).
 */
fun mergeSearchResults(
    seenKeys: MutableSet<String>,
    newResults: List<SearchResponse>
): List<SearchResponse> = newResults.filter { seenKeys.add(searchResultKey(it.apiName, it.url)) }

/**
 * The click action for a result card, built from the CLICKED item only —
 * never from query text, list position, or a re-resolved title.
 */
fun mediaDetailsDestination(item: SearchResponse): NavDestination.MediaDetails =
    NavDestination.MediaDetails(
        url = item.url,
        apiName = item.apiName,
        title = item.name,
        posterUrl = item.posterUrl
    )

/** Detail-page identity verdict. Anything but Match must surface, never silently open. */
sealed interface DetailIdentityCheck {
    data object Match : DetailIdentityCheck
    data class ProviderMismatch(val expectedApi: String, val actualApi: String) : DetailIdentityCheck
    data class TitleMismatch(val expectedTitle: String, val actualTitle: String) : DetailIdentityCheck
}

fun normalizeIdentityText(value: String): String =
    value.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

fun normalizePageUrl(value: String): String = value.trim().trimEnd('/')

/**
 * Exact-title disambiguation: when a provider page's title disagrees with the
 * clicked card (stale search index vs details page), re-search the SAME
 * provider for the clicked title and offer only strict normalized-equality
 * matches at OTHER urls. The user picks from the candidates — nothing
 * auto-opens, and season/episode choice stays entirely manual. Pure.
 */
fun pickExactTitleMatches(
    results: List<SearchResponse>,
    clickedTitle: String,
    currentUrl: String,
    maxResults: Int = 3
): List<SearchResponse> {
    val normClicked = normalizeIdentityText(clickedTitle)
    if (normClicked.isBlank()) return emptyList()
    val normCurrent = normalizePageUrl(currentUrl)
    return results.filter { item ->
        val itemUrl = normalizePageUrl(item.url)
        itemUrl.isNotBlank() && !itemUrl.equals(normCurrent, ignoreCase = true) &&
            normalizeIdentityText(item.name) == normClicked
    }.take(maxResults)
}

/**
 * Resolves provider-relative page URLs ("/title/tt123") against the owning
 * provider's site so search-result URLs and detail URLs compare correctly
 * even when one side is relative. Absolute, protocol-relative, and blank
 * inputs pass through the normalizer untouched.
 */
fun resolvePageUrl(url: String?, siteBaseUrl: String): String {
    if (url.isNullOrBlank()) return ""
    val trimmed = url.trim()
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
    if (trimmed.startsWith("//")) return "https:$trimmed"
    val base = siteBaseUrl.trim().trimEnd('/')
    if (base.isBlank() || base.equals("NONE", ignoreCase = true)) return trimmed
    if (!base.startsWith("http://") && !base.startsWith("https://")) return trimmed
    return if (trimmed.startsWith("/")) "$base$trimmed" else "$base/$trimmed"
}

/**
 * Short titles ("Dark") are substrings of unrelated longer titles ("Dark
 * Shadows"), so bare containment wrongly corroborates. Require a minimum
 * overlap length and ratio on top of containment.
 */
const val TITLE_CORROBORATE_MIN_LEN = 6
const val TITLE_CORROBORATE_MIN_RATIO = 0.6

fun titlesCorroborate(expectedTitle: String?, actualTitle: String?): Boolean {
    val e = expectedTitle?.let(::normalizeIdentityText).orEmpty()
    val a = (actualTitle ?: "").let(::normalizeIdentityText)
    if (e.isBlank() || a.isBlank()) return false
    if (e == a) return true
    val shorter = minOf(e.length, a.length)
    if (shorter < TITLE_CORROBORATE_MIN_LEN) return false
    if (shorter.toDouble() / maxOf(e.length, a.length) < TITLE_CORROBORATE_MIN_RATIO) return false
    return a.contains(e) || e.contains(a)
}

/**
 * Same image file (last path segment, query stripped) on both sides strongly
 * suggests the same catalog entry even when titles differ cosmetically.
 * Different filenames for the same claimed title suggest a cross-wired entry.
 */
fun sameImageFile(a: String?, b: String?): Boolean {
    if (a.isNullOrBlank() || b.isNullOrBlank()) return false
    fun filePart(u: String) = u.trim().substringAfterLast('/').substringBefore('?').trim()
    val fa = filePart(a)
    val fb = filePart(b)
    return fa.isNotBlank() && fa.equals(fb, ignoreCase = true)
}

/**
 * Validates that a loaded detail page is the clicked result. Provider echo
 * is authoritative (a different loader means different content). A matching
 * URL still needs one corroboration (title or poster) so cross-wired entries
 * ("Dark" card opening a same-URL "Dark Shadows" page) cannot pass on a
 * substring alone. Blank sides skip their check rather than failing.
 */
fun validateLoadedIdentity(
    requestedApiName: String,
    requestedUrl: String,
    requestedTitle: String?,
    requestedPosterUrl: String?,
    loadedApiName: String?,
    loadedUrl: String?,
    loadedTitle: String?,
    loadedPosterUrl: String?,
    siteBaseUrl: String = ""
): DetailIdentityCheck {
    val expectedApi = requestedApiName.trim()
    val actualApi = (loadedApiName ?: "").trim()
    if (expectedApi.isNotBlank() && actualApi.isNotBlank() &&
        !expectedApi.equals(actualApi, ignoreCase = true)
    ) {
        return DetailIdentityCheck.ProviderMismatch(expectedApi, actualApi)
    }
    val normRequestedUrl = normalizePageUrl(resolvePageUrl(requestedUrl, siteBaseUrl))
    val normLoadedUrl = normalizePageUrl(resolvePageUrl(loadedUrl.orEmpty(), siteBaseUrl))
    val urlOk = normLoadedUrl.isNotBlank() && normRequestedUrl.equals(normLoadedUrl, ignoreCase = true)
    val titleOk = titlesCorroborate(requestedTitle, loadedTitle)
    val posterOk = sameImageFile(requestedPosterUrl, loadedPosterUrl)
    if (urlOk && (titleOk || posterOk)) {
        return DetailIdentityCheck.Match
    }
    if (!urlOk && titleOk) {
        return DetailIdentityCheck.Match
    }
    if (normLoadedUrl.isBlank() && (loadedTitle ?: "").let(::normalizeIdentityText).isBlank() &&
        (loadedPosterUrl ?: "").isBlank()
    ) {
        return DetailIdentityCheck.Match
    }
    return DetailIdentityCheck.TitleMismatch(requestedTitle.orEmpty(), loadedTitle.orEmpty())
}

@Composable
fun SearchScreen(
    initialQuery: String? = null,
    initialApiName: String? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(initialQuery ?: "") }
    var selectedSuggestionTitle by remember { mutableStateOf<String?>(null) }
    var submittedQuery by remember { mutableStateOf("") }
    var searchRequestId by remember { mutableIntStateOf(0) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var selectedProviderName by remember { mutableStateOf(initialApiName ?: "All Providers") }
    var isSearching by remember { mutableStateOf(false) }
    var providerFailures by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerUnsupported by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showSuggestions by remember { mutableStateOf(false) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateListOf<SearchResponse>() }
    val suggestions = remember { mutableStateListOf<SearchResponse>() }

    val recentSearches = remember { mutableStateListOf("Batman", "Inception", "Breaking Bad", "Spider-Man", "Anime", "Attack on Titan") }

    val pluginsVersion by com.lagradost.cloudstream4.extensions.DesktopPluginManager.pluginsState.collectAsState()
    val allProviders = remember(pluginsVersion) { APIHolder.allProviders.toList() }
    val providerOptions = remember(allProviders) { listOf("All Providers") + allProviders.map { it.name }.distinct() }

    fun performSearch(searchQuery: String) {
        if (searchQuery.isBlank()) return
        query = searchQuery
        selectedSuggestionTitle = null
        submittedQuery = searchQuery
        val requestId = ++searchRequestId
        isSearching = true
        providerFailures = emptyList()
        providerUnsupported = emptyList()
        searchResults.clear()

        // Cancel the previous round so its late responses can never replace
        // this query's results or failures. Cancellation is silent by design.
        searchJob?.cancel()
        searchJob = scope.launch {
            val targetProviders = if (selectedProviderName == "All Providers") {
                // Metadata-only providers (TMDB catalog) return entries
                // with no playable streams; mixing them into aggregate
                // results produces dead-end cards, so they are searched
                // only when explicitly selected (same rule as Home).
                allProviders.filter { it !is TmdbProvider }
            } else {
                allProviders.filter { it.name == selectedProviderName }
            }
            if (targetProviders.isEmpty()) {
                if (requestId == searchRequestId) isSearching = false
                return@launch
            }
            // Results stream in per provider: one slow provider can no longer
            // hold successful results hostage behind a single awaitAll.
            var pending = targetProviders.size
            val seenKeys = mutableSetOf<String>()
            targetProviders.forEach { provider ->
                scope.launch(Dispatchers.IO) {
                    val outcome = searchOneProvider(provider, searchQuery)
                    withContext(Dispatchers.Main) {
                        if (requestId != searchRequestId) return@withContext
                        when (outcome) {
                            is ProviderSearchOutcome.Success -> {
                                val fresh = mergeSearchResults(seenKeys, outcome.results)
                                if (fresh.isNotEmpty()) searchResults.addAll(fresh)
                            }
                            // Empty-but-successful is not a failure: stay silent.
                            is ProviderSearchOutcome.NoResults -> Unit
                            // Unsupported is a permanent capability gap: muted
                            // info, never a failure warning.
                            is ProviderSearchOutcome.Unsupported -> {
                                providerUnsupported = providerUnsupported + outcome.label
                            }
                            is ProviderSearchOutcome.TimedOut,
                            is ProviderSearchOutcome.Errored -> {
                                providerFailures = providerFailures + outcome.label
                            }
                        }
                        if (!recentSearches.contains(searchQuery)) {
                            recentSearches.add(0, searchQuery)
                        }
                        pending -= 1
                        if (pending == 0) isSearching = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!initialQuery.isNullOrBlank()) {
            performSearch(initialQuery)
        }
    }

    LaunchedEffect(query, showSuggestions) {
        val normalizedQuery = query.trim()
        if (!showSuggestions || normalizedQuery.length < 2) {
            suggestions.clear()
            isLoadingSuggestions = false
            return@LaunchedEffect
        }

        delay(320)
        isLoadingSuggestions = true
        val found = withContext(Dispatchers.IO) {
            try {
                val metadataProvider = APIHolder.allProviders.filterIsInstance<TmdbProvider>().firstOrNull()
                    ?: DesktopTmdbProvider()
                metadataProvider.search(normalizedQuery, 1)?.items.orEmpty()
            } catch (e: Throwable) {
                e.printStackTrace()
                emptyList()
            }
        }
        if (query.trim() == normalizedQuery && showSuggestions) {
            suggestions.clear()
            suggestions.addAll(found.take(8))
        }
        isLoadingSuggestions = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Search field — clear focus state with accent ring
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                placeholder = {
                    Text(
                        "Search movies, shows, anime, creators...",
                        color = ShiopaStyle.TextMuted,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier.weight(1f).onFocusChanged { isSearchFocused = it.isFocused },
                shape = ShiopaStyle.ControlShape,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(query) }),
                leadingIcon = {
                    Text(
                        "⌕",
                        fontSize = 17.sp,
                        color = ShiopaStyle.TextSecondary,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Text(
                            text = "✕",
                            fontSize = 14.sp,
                            color = ShiopaStyle.TextMuted,
                            modifier = Modifier
                                .clickable {
                                    query = ""
                                    submittedQuery = ""
                                    selectedSuggestionTitle = null
                                    searchRequestId++
                                    isSearching = false
                                    showSuggestions = false
                                    searchResults.clear()
                                }
                                .padding(end = 14.dp)
                                .pointerHoverIcon(PointerIcon.Hand)
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ShiopaStyle.SurfaceCard,
                    unfocusedContainerColor = ShiopaStyle.SurfaceCard,
                    focusedBorderColor = ShiopaStyle.AccentMain,
                    unfocusedBorderColor = ShiopaStyle.GlassBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = ShiopaStyle.AccentMain
                ),
                onValueChange = { value ->
                    query = value
                    selectedSuggestionTitle = null
                    showSuggestions = value.isNotBlank()
                    submittedQuery = ""
                    searchRequestId++
                    isSearching = false
                    providerFailures = emptyList()
                    searchResults.clear()
                }
            )

            Spacer(modifier = Modifier.width(ShiopaSpacing.Md))

            // Search Action Button
            Box(
                modifier = Modifier
                    .clip(ShiopaStyle.ControlShape)
                    .background(ShiopaStyle.AccentMain)
                    .clickable { performSearch(query) }
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "Search",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        if (isSearchFocused && query.trim().length < 2) {
            Text(
                "Search is active · type at least 2 characters for live title suggestions",
                fontSize = 11.sp,
                color = ShiopaStyle.AccentCyan,
                modifier = Modifier.padding(start = 14.dp, top = 5.dp)
            )
        }

        if (showSuggestions && query.trim().length >= 2) {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 330.dp)
                    .clip(ShiopaStyle.CardShape)
                    .background(Color(0xFF15151D))
                    .border(1.dp, ShiopaStyle.GlassBorderActive, ShiopaStyle.CardShape)
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("LIVE TITLE SUGGESTIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ShiopaStyle.AccentCyan, letterSpacing = 0.8.sp)
                    Text("TMDB catalog", fontSize = 10.sp, color = ShiopaStyle.TextMuted)
                }
                when {
                    isLoadingSuggestions -> Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ShiopaStyle.AccentCyan)
                        Text("Finding matching titles…", fontSize = 12.sp, color = ShiopaStyle.TextSecondary)
                    }
                    suggestions.isEmpty() -> Text(
                        "No catalog matches yet. Press Search to check your providers.",
                        fontSize = 12.sp,
                        color = ShiopaStyle.TextSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                    )
                    else -> suggestions.forEach { suggestion ->
                        val year = when (suggestion) {
                            is com.lagradost.cloudstream3.MovieSearchResponse -> suggestion.year
                            is com.lagradost.cloudstream3.TvSeriesSearchResponse -> suggestion.year
                            else -> null
                        }
                        val typeLabel = listOfNotNull(
                            year?.toString(),
                            suggestion.type?.name?.lowercase()?.replaceFirstChar { it.uppercase() }
                        ).joinToString("  •  ").ifBlank { "Movie or series" }
                        // Search area and IMDb pill are SIBLINGS, not nested
                        // clickables — so clicking the title always searches and
                        // clicking the pill always opens IMDb, never both.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Primary action: put the selected title in the
                            // field immediately and search for it.
                            Row(
                                modifier = Modifier.weight(1f)
                                    .clip(ShiopaStyle.CornerSm)
                                    .clickable {
                                    val fieldText = suggestionSelectionPair(suggestion.name.trim()).first
                                    if (fieldText.isBlank()) return@clickable
                                    // First commit the title visibly, then submit it after
                                    // Compose has rendered the field update. The popup is
                                    // independent of text-field focus so its click target
                                    // cannot disappear during the pointer interaction.
                                    query = fieldText
                                    selectedSuggestionTitle = fieldText
                                    submittedQuery = ""
                                    showSuggestions = false
                                    scope.launch {
                                        yield()
                                        performSearch(fieldText)
                                    }
                                }
                                    .pointerHoverIcon(PointerIcon.Hand),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Poster thumbnail
                                Box(
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 56.dp)
                                        .clip(ShiopaStyle.CornerSm)
                                        .background(ShiopaStyle.SurfaceCard)
                                ) {
                                    if (!suggestion.posterUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = suggestion.posterUrl,
                                            contentDescription = suggestion.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column {
                                    Text(suggestion.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(typeLabel, fontSize = 11.sp, color = ShiopaStyle.TextMuted)
                                }
                                Text(
                                    "Search title →",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ShiopaStyle.AccentCyan
                                )
                            }
                            // Secondary action: IMDb lookup for this title.
                            Text(
                                "IMDb ↗",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ShiopaStyle.AccentCyan,
                                modifier = Modifier
                                    .clip(ShiopaStyle.PillShape)
                                    .clickable {
                                        runCatching {
                                            java.awt.Desktop.getDesktop().browse(
                                                java.net.URI("https://www.imdb.com/find/?q=${java.net.URLEncoder.encode(suggestion.name, "UTF-8")}")
                                            )
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        if (selectedSuggestionTitle == query && !selectedSuggestionTitle.isNullOrBlank()) {
            Text(
                text = "Searching for “$selectedSuggestionTitle”…",
                fontSize = 11.sp,
                color = ShiopaStyle.AccentCyan,
                modifier = Modifier.padding(start = 14.dp, top = 5.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Provider Filter Capsules
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
        ) {
            items(providerOptions.size) { index ->
                val pName = providerOptions[index]
                val isSelected = pName == selectedProviderName
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.SurfaceCard)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ShiopaStyle.AccentMain else ShiopaStyle.GlassBorder,
                            shape = ShiopaStyle.PillShape
                        )
                        .clickable {
                            selectedProviderName = pName
                            if (query.isNotBlank()) performSearch(query)
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = pName,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else ShiopaStyle.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Recent Searches Tags
        if (query.isBlank() && searchResults.isEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Trending Searches:",
                    fontSize = 12.sp,
                    color = ShiopaStyle.TextMuted,
                    fontWeight = FontWeight.Medium
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recentSearches.size) { index ->
                        val item = recentSearches[index]
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(Color(0xFF14141A))
                                .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                            .clickable {
                                query = item
                                performSearch(item)
                            }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = item,
                                fontSize = 12.sp,
                                color = ShiopaStyle.TextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results View. Results render the moment any provider responds —
        // a slow provider never holds successful results hostage.
        when {
            searchResults.isNotEmpty() -> {
                if (providerFailures.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            "Some providers did not respond: ${providerFailures.joinToString()}",
                            fontSize = 11.sp,
                            color = ShiopaStyle.AccentGold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { performSearch(submittedQuery) }) {
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (providerUnsupported.isNotEmpty()) {
                    Text(
                        "No search in: ${providerUnsupported.joinToString()}",
                        fontSize = 11.sp,
                        color = ShiopaStyle.TextMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = "${searchResults.size} results for \"$submittedQuery\"" +
                        if (isSearching) " · still searching…" else "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ShiopaStyle.TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 170.dp),
                    contentPadding = PaddingValues(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(ShiopaSpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(searchResults, key = { searchResultKey(it.apiName, it.url) }) { item ->
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
                                // The destination is built from the CLICKED item
                                // only — never from query text, position, or a
                                // re-resolved title (see mediaDetailsDestination).
                                NavigationManager.navigate(mediaDetailsDestination(item))
                            }
                        )
                    }
                }
            }
            isSearching -> {
                LoadingView(message = "Searching across extensions for \"$submittedQuery\"...")
            }
            submittedQuery.isNotBlank() -> {
                EmptyView(
                    title = "No results found for \"$submittedQuery\"",
                    description = if (providerFailures.isNotEmpty()) {
                        "Could not search: ${providerFailures.joinToString()}. Try another provider or check its extension."
                    } else {
                        "Try searching for a different title or select another provider."
                    },
                    actionLabel = if (providerFailures.isNotEmpty()) "Retry search" else null,
                    onAction = if (providerFailures.isNotEmpty()) {
                        { performSearch(submittedQuery) }
                    } else null
                )
            }
        }
    }
}
