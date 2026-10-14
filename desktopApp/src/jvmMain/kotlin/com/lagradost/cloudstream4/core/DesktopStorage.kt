package com.lagradost.cloudstream4.core

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.TrackerType
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

val desktopJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
    encodeDefaults = true
    coerceInputValues = true
    isLenient = true
}

@Serializable
data class AppSettings(
    val themeMode: String = "Dark", // "Dark", "Light", "System", "Black"
    val accentColor: Long = 0xFF6366F1, // Default Indigo/Purple
    val hardwareAcceleration: Boolean = true,
    val autoPlayNextEpisode: Boolean = true,
    val defaultSeekSeconds: Int = 10,
    val subtitleFontSize: Int = 22,
    val subtitleColor: Long = 0xFFFFFFFF,
    val subtitleBackgroundColor: Long = 0xAA000000,
    val subtitleLanguage: String = "en",
    val preferredAudioLanguage: String = "en",
    val downloadsDirectory: String = AppPaths.defaultDownloadsDir.absolutePath,
    val maxConcurrentDownloads: Int = 3,
    val sidebarExpanded: Boolean = true,
    val checkExtensionUpdatesOnStartup: Boolean = true,
    val reducedMotion: Boolean = false,
    val ambientGlow: Boolean = true,
    val homeProvider: String = "All providers"
)

@Serializable
enum class BookmarkStatus {
    WATCHING,
    PLAN_TO_WATCH,
    COMPLETED,
    ON_HOLD,
    DROPPED
}

@Serializable
data class BookmarkItem(
    val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val year: Int? = null,
    val status: BookmarkStatus = BookmarkStatus.WATCHING,
    val addedAt: Long = System.currentTimeMillis()
)

@Serializable
data class HistoryItem(
    val url: String,
    val apiName: String,
    val title: String,
    val posterUrl: String? = null,
    val type: String? = null,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val episodeTitle: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastWatchedAt: Long = System.currentTimeMillis()
)

@Serializable
data class StoredRepository(
    val name: String,
    val url: String,
    val description: String? = null,
    val iconUrl: String? = null
)

@Serializable
data class StoredPlugin(
    val internalName: String,
    val name: String,
    val version: Int,
    val filePath: String,
    val url: String? = null,
    val enabled: Boolean = true,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val iconUrl: String? = null
)

@Serializable
data class StoredDownload(
    val id: String,
    val mediaTitle: String,
    val episodeTitle: String? = null,
    val sourceUrl: String,
    val destinationPath: String,
    val posterUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "COMPLETED", // "DOWNLOADING", "PAUSED", "COMPLETED", "FAILED"
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class SkipSegment(
    val apiName: String,
    val seriesUrl: String,
    val episodeIndex: Int? = null,
    val seasonIndex: Int? = null,
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val recapStartMs: Long = -1L,
    val recapEndMs: Long = -1L,
    val endingStartMs: Long = -1L,
    val endingEndMs: Long = -1L,
    /** "manual" for user marks, "provider:<name>" for fetched markers. Old files decode as manual. */
    val source: String = SKIP_SOURCE_MANUAL
)

/** Source tag for explicit user-marked ranges. Always wins over fetched data. */
const val SKIP_SOURCE_MANUAL = "manual"

/** A segment is known only when both ends are explicitly set and ordered. Never guessed. */
fun isKnownSegment(startMs: Long, endMs: Long): Boolean = startMs >= 0 && endMs > startMs

/** Visible only while playback is inside [startMs, endMs). End-exclusive: hides exactly at the end. */
fun isInSegment(positionMs: Long, startMs: Long, endMs: Long): Boolean =
    isKnownSegment(startMs, endMs) && positionMs in startMs until endMs

/** Episode-exact range wins; otherwise a series-level range (null episode index). */
fun findSkipSegment(
    segments: List<SkipSegment>,
    apiName: String,
    seriesUrl: String,
    episodeIndex: Int?,
    seasonIndex: Int?
): SkipSegment? {
    val mine = segments.filter { it.apiName == apiName && it.seriesUrl == seriesUrl }
    // Explicit user marks always win over fetched provider data, at each level.
    return mine.find { it.episodeIndex == episodeIndex && it.seasonIndex == seasonIndex && it.source == SKIP_SOURCE_MANUAL }
        ?: mine.find { it.episodeIndex == episodeIndex && it.seasonIndex == seasonIndex }
        ?: mine.find { it.episodeIndex == null && it.source == SKIP_SOURCE_MANUAL }
        ?: mine.find { it.episodeIndex == null }
}

/** One episode's markers from a community service. Times in ms; -1 = unknown kind. */
data class EpisodeMarkers(
    val introStartMs: Long = -1L,
    val introEndMs: Long = -1L,
    val recapStartMs: Long = -1L,
    val recapEndMs: Long = -1L,
    val endingStartMs: Long = -1L,
    val endingEndMs: Long = -1L,
    val source: String = "unknown"
) {
    fun hasAnyKnown(): Boolean =
        isKnownSegment(introStartMs, introEndMs) || isKnownSegment(recapStartMs, recapEndMs) ||
            isKnownSegment(endingStartMs, endingEndMs)

    /**
     * Drops kinds that fail usability (too short, out of the episode, or
     * unordered). Returns null when nothing usable remains. Source tag kept.
     */
    fun toUsable(durationMs: Long): EpisodeMarkers? {
        val introOk = isUsableMarker(introStartMs, introEndMs, durationMs)
        val recapOk = isUsableMarker(recapStartMs, recapEndMs, durationMs)
        val endingOk = isUsableMarker(endingStartMs, endingEndMs, durationMs)
        if (!introOk && !recapOk && !endingOk) return null
        return copy(
            introStartMs = if (introOk) introStartMs else -1L,
            introEndMs = if (introOk) introEndMs else -1L,
            recapStartMs = if (recapOk) recapStartMs else -1L,
            recapEndMs = if (recapOk) recapEndMs else -1L,
            endingStartMs = if (endingOk) endingStartMs else -1L,
            endingEndMs = if (endingOk) endingEndMs else -1L
        )
    }
}

/**
 * A marker range is usable only when explicitly provided, ordered, long enough
 * to matter, starting at/after zero, and (when the episode duration is known)
 * fully inside the episode. Nothing is inferred, extended, or defaulted.
 */
const val MIN_SKIP_SEGMENT_MS = 5000L

fun isUsableMarker(startMs: Long, endMs: Long, durationMs: Long, minLengthMs: Long = MIN_SKIP_SEGMENT_MS): Boolean {
    if (!isKnownSegment(startMs, endMs)) return false
    if (startMs < 0) return false
    if (endMs - startMs < minLengthMs) return false
    if (durationMs > 0 && endMs > durationMs) return false
    return true
}

enum class SkipButtonKind(val key: String) { INTRO("intro"), RECAP("recap"), ENDING("ending") }

/**
 * Deterministic single-button rule: at most one button is ever shown. Intro
 * wins ties and overlaps, then Recap, then Ending; a used-up kind never
 * returns even when rewound back into its range; leaving the range hides
 * everything.
 */
fun resolveVisibleButton(
    inIntro: Boolean, introUsed: Boolean,
    inRecap: Boolean, recapUsed: Boolean,
    inEnding: Boolean = false, endingUsed: Boolean = true
): SkipButtonKind? = when {
    inIntro && !introUsed -> SkipButtonKind.INTRO
    inRecap && !recapUsed -> SkipButtonKind.RECAP
    inEnding && !endingUsed -> SkipButtonKind.ENDING
    else -> null
}

/**
 * Full per-frame button decision for a stored range: validity + position +
 * single-use + overlap rule. Pure and unit-tested.
 */
fun selectSkipButton(
    range: SkipSegment?,
    positionMs: Long,
    durationMs: Long,
    usedKinds: Set<String>
): SkipButtonKind? {
    if (range == null) return null
    val inIntro = isInSegment(positionMs, range.introStartMs, range.introEndMs) &&
        isUsableMarker(range.introStartMs, range.introEndMs, durationMs)
    val inRecap = isInSegment(positionMs, range.recapStartMs, range.recapEndMs) &&
        isUsableMarker(range.recapStartMs, range.recapEndMs, durationMs)
    val inEnding = isInSegment(positionMs, range.endingStartMs, range.endingEndMs) &&
        isUsableMarker(range.endingStartMs, range.endingEndMs, durationMs)
    return resolveVisibleButton(
        inIntro, usedKinds.contains(SkipButtonKind.INTRO.key),
        inRecap, usedKinds.contains(SkipButtonKind.RECAP.key),
        inEnding, usedKinds.contains(SkipButtonKind.ENDING.key)
    )
}

/** Lowercase alphanumeric folding for strict title comparison. */
fun normalizeMediaTitle(title: String): String =
    title.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

/** Strict equality on normalized titles; blank sides never match. */
fun isStrictTitleMatch(queryTitle: String, candidateTitle: String?): Boolean {
    if (queryTitle.isBlank() || candidateTitle.isNullOrBlank()) return false
    return normalizeMediaTitle(queryTitle) == normalizeMediaTitle(candidateTitle)
}

/** Everything a marker provider needs. Stream URLs/headers/tokens are never included. */
data class MarkerLookup(
    val apiName: String,
    val seriesUrl: String,
    val title: String,
    val tvTypeName: String?,
    val year: Int?,
    val malId: Int?,
    val tmdbId: String?,
    val imdbId: String?,
    val seasonIndex: Int?,
    val episodeIndex: Int?,
    val durationMs: Long,
    /** Flat position in series order (+1); fallback when seasonal numbering misses. Null when unknown. */
    val absoluteEpisodeIndex: Int? = null
) {
    /** Movies carry no episode number: only explicitly-identified movie data may apply. */
    val isMovie: Boolean get() = episodeIndex == null
    fun tvType(): TvType? = tvTypeName?.let { runCatching { TvType.valueOf(it) }.getOrNull() }
}

/**
 * A community marker service. Implementations must use only the lookup's
 * non-identifying metadata — never stream URLs, request headers, or tokens.
 */
interface DesktopSkipProvider {
    val name: String
    fun supports(ctx: MarkerLookup): Boolean
    suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers?
}

// --- AniSkip (anime OP/recap; keyless public API, mirrored from the Android counterpart) ---
@kotlinx.serialization.Serializable
data class AniSkipTimesResponse(
    @SerialName("found") val found: Boolean = false,
    @SerialName("results") val results: List<AniSkipStamp>? = null
)

@kotlinx.serialization.Serializable
data class AniSkipStamp(
    @SerialName("skipType") val skipType: String = "",
    @SerialName("interval") val interval: AniSkipInterval? = null
)

@kotlinx.serialization.Serializable
data class AniSkipInterval(
    @SerialName("startTime") val startTime: Double = -1.0,
    @SerialName("endTime") val endTime: Double = -1.0
)

fun aniskipToMarkers(response: AniSkipTimesResponse): EpisodeMarkers {
    var intro: Pair<Long, Long>? = null
    var recap: Pair<Long, Long>? = null
    var ending: Pair<Long, Long>? = null
    for (stamp in response.results.orEmpty()) {
        val iv = stamp.interval ?: continue
        val start = (iv.startTime * 1000.0).toLong()
        val end = (iv.endTime * 1000.0).toLong()
        if (end <= start || start < 0) continue
        when (stamp.skipType) {
            "op", "mixed-op" -> if (intro == null) intro = start to end
            "recap" -> if (recap == null) recap = start to end
            // Endings drive the desktop "Skip Ending" button (first one wins).
            "ed", "mixed-ed" -> if (ending == null) ending = start to end
            else -> Unit
        }
    }
    return EpisodeMarkers(
        introStartMs = intro?.first ?: -1L, introEndMs = intro?.second ?: -1L,
        recapStartMs = recap?.first ?: -1L, recapEndMs = recap?.second ?: -1L,
        endingStartMs = ending?.first ?: -1L, endingEndMs = ending?.second ?: -1L,
        source = "aniskip"
    )
}

/**
 * AniSkip episode-number candidates in try order: seasonal first, then the
 * flat absolute number when it differs. Long-running anime are often numbered
 * absolutely in MAL-indexed databases while providers report per-season
 * numbers; a miss on the first candidate reports found=false (hidden, safe),
 * so the fallback only costs one extra cached call. Pure.
 */
fun aniskipEpisodeCandidates(episodeIndex: Int, absoluteEpisodeIndex: Int?): List<Int> =
    if (absoluteEpisodeIndex != null && absoluteEpisodeIndex != episodeIndex && absoluteEpisodeIndex > 0) {
        listOf(episodeIndex, absoluteEpisodeIndex)
    } else {
        listOf(episodeIndex)
    }

class AniSkipDesktopProvider : DesktopSkipProvider {
    override val name = "aniskip"
    override fun supports(ctx: MarkerLookup): Boolean {
        val type = ctx.tvType()
        if (type != TvType.Anime && type != TvType.OVA) return false
        if (ctx.episodeIndex == null || ctx.durationMs <= 0) return false
        return true
    }

    override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? {
        val malId = ctx.malId ?: return null
        val episodeSeconds = ctx.durationMs / 1000L
        for (episodeNumber in aniskipEpisodeCandidates(
            ctx.episodeIndex ?: return null,
            ctx.absoluteEpisodeIndex
        )) {
            val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber" +
                "?types[]=mixed-op&types[]=op&types[]=recap" +
                "&episodeLength=$episodeSeconds"
            val text = app.get(url).text
            val response = runCatching { desktopJson.decodeFromString<AniSkipTimesResponse>(text) }.getOrNull()
                ?: return null
            if (!response.found) continue
            return aniskipToMarkers(response)
        }
        return null
    }
}

// --- TheIntroDB (series + explicitly-identified movies; keyless, mirrored from Android) ---
@kotlinx.serialization.Serializable
data class TheIntroDbRoot(
    @SerialName("intro") val intro: List<TheIntroDbStamp> = emptyList(),
    @SerialName("recap") val recap: List<TheIntroDbStamp> = emptyList(),
    @SerialName("credits") val credits: List<TheIntroDbStamp> = emptyList()
    // preview intentionally not mapped (Intro + Recap + Ending buttons only)
)

@kotlinx.serialization.Serializable
data class TheIntroDbStamp(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null
)

fun theIntroDbToMarkers(root: TheIntroDbRoot): EpisodeMarkers {
    fun firstUsable(list: List<TheIntroDbStamp>): Pair<Long, Long>? {
        val s = list.firstOrNull() ?: return null
        val start = s.startMs ?: return null
        val end = s.endMs ?: return null
        // Null-ended stamps are dropped, never defaulted to the duration.
        if (end <= start || start < 0) return null
        return start to end
    }
    val intro = firstUsable(root.intro)
    val recap = firstUsable(root.recap)
    // End credits drive the desktop "Skip Ending" button.
    val ending = firstUsable(root.credits)
    return EpisodeMarkers(
        introStartMs = intro?.first ?: -1L, introEndMs = intro?.second ?: -1L,
        recapStartMs = recap?.first ?: -1L, recapEndMs = recap?.second ?: -1L,
        endingStartMs = ending?.first ?: -1L, endingEndMs = ending?.second ?: -1L,
        source = "theintrodb"
    )
}

class TheIntroDbDesktopProvider : DesktopSkipProvider {
    override val name = "theintrodb"
    override fun supports(ctx: MarkerLookup): Boolean {
        if (ctx.tmdbId.isNullOrBlank() && ctx.imdbId.isNullOrBlank()) return false
        // Episodes need season+episode; movies use the ID-only path.
        if (!ctx.isMovie && (ctx.seasonIndex == null || ctx.episodeIndex == null)) return false
        return true
    }

    override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? {
        val idParam = ctx.tmdbId?.takeIf { it.isNotBlank() }?.let { "tmdb_id=$it" }
            ?: ctx.imdbId?.takeIf { it.isNotBlank() }?.let { "imdb_id=$it" }
            ?: return null
        val url = if (ctx.isMovie) {
            "https://api.theintrodb.org/v2/media?$idParam"
        } else {
            "https://api.theintrodb.org/v2/media?$idParam&season=${ctx.seasonIndex}&episode=${ctx.episodeIndex}"
        }
        val text = app.get(url).text
        val root = runCatching { desktopJson.decodeFromString<TheIntroDbRoot>(text) }.getOrNull()
            ?: return null
        return theIntroDbToMarkers(root)
    }
}

// --- IntroDb (series recap/intro by IMDb ID; keyless, mirrored from Android) ---
@kotlinx.serialization.Serializable
data class IntroDbResponse(
    @SerialName("intro") val intro: IntroDbSegment? = null,
    @SerialName("recap") val recap: IntroDbSegment? = null,
    @SerialName("outro") val outro: IntroDbSegment? = null
)

@kotlinx.serialization.Serializable
data class IntroDbSegment(
    @SerialName("start_ms") val startMs: Long? = null,
    @SerialName("end_ms") val endMs: Long? = null,
    @SerialName("start_sec") val startSec: Double? = null,
    @SerialName("end_sec") val endSec: Double? = null
)

/** Millisecond fields win; second fields convert; anything missing/invalid drops the range. Pure. */
fun introDbSegmentToRange(seg: IntroDbSegment?): Pair<Long, Long>? {
    if (seg == null) return null
    val start = seg.startMs ?: seg.startSec?.let { (it * 1000.0).toLong() } ?: return null
    val end = seg.endMs ?: seg.endSec?.let { (it * 1000.0).toLong() } ?: return null
    if (end <= start || start < 0) return null
    return start to end
}

fun introDbToMarkers(response: IntroDbResponse): EpisodeMarkers {
    val intro = introDbSegmentToRange(response.intro)
    val recap = introDbSegmentToRange(response.recap)
    // Outro drives the desktop "Skip Ending" button.
    val ending = introDbSegmentToRange(response.outro)
    return EpisodeMarkers(
        introStartMs = intro?.first ?: -1L, introEndMs = intro?.second ?: -1L,
        recapStartMs = recap?.first ?: -1L, recapEndMs = recap?.second ?: -1L,
        endingStartMs = ending?.first ?: -1L, endingEndMs = ending?.second ?: -1L,
        source = "introdb"
    )
}

class IntroDbDesktopProvider : DesktopSkipProvider {
    override val name = "introdb"
    override fun supports(ctx: MarkerLookup): Boolean {
        if (ctx.imdbId.isNullOrBlank()) return false
        if (ctx.isMovie || ctx.seasonIndex == null || ctx.episodeIndex == null) return false
        return true
    }

    override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? {
        val imdbId = ctx.imdbId?.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.introdb.app/segments?imdb_id=$imdbId" +
            "&season=${ctx.seasonIndex}&episode=${ctx.episodeIndex}"
        val text = app.get(url).text
        val response = runCatching { desktopJson.decodeFromString<IntroDbResponse>(text) }.getOrNull()
            ?: return null
        return introDbToMarkers(response)
    }
}

// --- External ID resolution (syncData IDs first; resolved ones cached in memory) ---
data class ResolvedIds(val malId: Int? = null, val tmdbId: String? = null, val imdbId: String? = null)

object IdResolutionCache {
    private val guard = ReentrantReadWriteLock()
    private val entries = mutableMapOf<String, ResolvedIds>()

    fun key(apiName: String, seriesUrl: String) = "$apiName|$seriesUrl"

    fun get(cacheKey: String): ResolvedIds? = guard.read { entries[cacheKey] }

    fun put(cacheKey: String, value: ResolvedIds) = guard.write { entries[cacheKey] = value }

    fun clear() = guard.write { entries.clear() }
}

/** MAL ID via the shared AniList matcher (same getTracker the Android app uses). Null when unresolvable. */
suspend fun resolveMalId(
    apiName: String,
    seriesUrl: String,
    title: String,
    tvTypeName: String?,
    year: Int?,
    knownMalId: Int?
): Int? {
    knownMalId?.let { return it }
    val tvType = tvTypeName?.let { runCatching { TvType.valueOf(it) }.getOrNull() }
    if (tvType != TvType.Anime && tvType != TvType.OVA) return null
    if (title.length <= 2) return null
    val cacheKey = IdResolutionCache.key(apiName, seriesUrl)
    IdResolutionCache.get(cacheKey)?.malId?.let { return it }
    return try {
        // Shared AniList matcher (same getTracker the Android app uses).
        APIHolder.getTracker(listOf(title), TrackerType.getTypes(tvType), year)?.malId?.also {
            val prev = IdResolutionCache.get(cacheKey)
            IdResolutionCache.put(cacheKey, ResolvedIds(malId = it, tmdbId = prev?.tmdbId, imdbId = prev?.imdbId))
        }
    } catch (_: Throwable) {
        null
    }
}

// Same public key the bundled TmdbProvider ships with (repo-visible, keyless community use).
private const val TMDB_PUBLIC_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"

@kotlinx.serialization.Serializable
data class TmdbSearchResponse(
    @SerialName("results") val results: List<TmdbSearchItem> = emptyList()
)

@kotlinx.serialization.Serializable
data class TmdbDetailsResponse(
    @SerialName("imdb_id") val imdbId: String? = null
)

/** Extracts imdb_id from a TMDB movie/tv details payload. Blank/missing/malformed → null. Pure. */
fun parseImdbIdFromDetails(jsonText: String): String? = runCatching {
    desktopJson.decodeFromString<TmdbDetailsResponse>(jsonText).imdbId?.takeIf { it.isNotBlank() }
}.getOrNull()

/**
 * TMDB → IMDb enrichment: one details call per TMDB ID, merged into the ID
 * cache. Unlocks IntroDb for TMDB-only titles (IntroDb is IMDb-keyed).
 * Null when already known, unresolvable, or on any failure.
 */
suspend fun resolveImdbId(
    apiName: String,
    seriesUrl: String,
    tmdbId: String?,
    imdbId: String?,
    isMovie: Boolean
): String? {
    imdbId?.takeIf { it.isNotBlank() }?.let { return it }
    if (tmdbId.isNullOrBlank()) return null
    val cacheKey = IdResolutionCache.key(apiName, seriesUrl)
    IdResolutionCache.get(cacheKey)?.imdbId?.let { return it }
    return try {
        val kind = if (isMovie) "movie" else "tv"
        val text = app.get(
            url = "https://api.themoviedb.org/3/$kind/$tmdbId",
            params = mapOf("api_key" to TMDB_PUBLIC_API_KEY)
        ).text
        parseImdbIdFromDetails(text)?.also {
            val prev = IdResolutionCache.get(cacheKey)
            IdResolutionCache.put(
                cacheKey,
                ResolvedIds(malId = prev?.malId, tmdbId = prev?.tmdbId ?: tmdbId, imdbId = it)
            )
        }
    } catch (_: Throwable) {
        null
    }
}

@kotlinx.serialization.Serializable
data class TmdbSearchItem(
    @SerialName("id") val id: Long = -1L,
    @SerialName("title") val title: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null
)

/** Strict TMDB match: normalized title equality plus year when known. Pure. */
fun pickTmdbId(results: List<TmdbSearchItem>, title: String, year: Int?, isMovie: Boolean): Long? {
    for (item in results) {
        if (item.id < 0) continue
        val candidate = (if (isMovie) item.title else item.name ?: item.title) ?: continue
        if (!isStrictTitleMatch(title, candidate)) continue
        val date = (if (isMovie) item.releaseDate else item.firstAirDate)?.take(4)?.toIntOrNull()
        if (year != null && date != null && date != year) continue
        return item.id
    }
    return null
}

/** TMDB title/year search for themoviedb IDs (feeds TheIntroDB). Null when unresolvable. */
suspend fun resolveTmdbId(
    apiName: String,
    seriesUrl: String,
    title: String,
    year: Int?,
    knownTmdbId: String?,
    isMovie: Boolean
): String? {
    knownTmdbId?.takeIf { it.isNotBlank() }?.let { return it }
    if (title.length <= 2) return null
    val cacheKey = IdResolutionCache.key(apiName, seriesUrl)
    IdResolutionCache.get(cacheKey)?.tmdbId?.let { return it }
    return try {
        val kind = if (isMovie) "movie" else "tv"
        val params = buildMap {
            put("api_key", TMDB_PUBLIC_API_KEY)
            put("query", title)
            put("include_adult", "false")
            if (year != null) put(if (isMovie) "year" else "first_air_date_year", year.toString())
        }
        val text = app.get(url = "https://api.themoviedb.org/3/search/$kind", params = params).text
        val response = desktopJson.decodeFromString<TmdbSearchResponse>(text)
        pickTmdbId(response.results, title, year, isMovie)?.toString()?.also {
            val prev = IdResolutionCache.get(cacheKey)
            IdResolutionCache.put(cacheKey, ResolvedIds(malId = prev?.malId, tmdbId = it, imdbId = prev?.imdbId))
        }
    } catch (_: Throwable) {
        null
    }
}

fun defaultDesktopSkipProviders(): List<DesktopSkipProvider> = listOf(
    // Same order as the Android counterpart (minus the login-walled AnimeSkip,
    // which needs personal anime-skip.com credentials the desktop has nowhere
    // to store): first provider with usable Intro/Recap data wins.
    AniSkipDesktopProvider(),
    TheIntroDbDesktopProvider(),
    IntroDbDesktopProvider()
)

private const val MARKER_CACHE_TTL_MS = 24L * 60 * 60 * 1000
private const val MARKER_NEGATIVE_TTL_MS = 60L * 60 * 1000

data class CachedMarkers(val markers: EpisodeMarkers?, val fetchedAtMs: Long)

/** Freshness check for positive (24h) and negative/missing (1h) cache entries. Pure. */
fun isMarkerCacheFresh(fetchedAtMs: Long, nowMs: Long, negative: Boolean): Boolean {
    if (fetchedAtMs > nowMs) return false
    val ttl = if (negative) MARKER_NEGATIVE_TTL_MS else MARKER_CACHE_TTL_MS
    return nowMs - fetchedAtMs < ttl
}

/** In-memory marker cache (surviving restarts via persisted provider entries). */
object SkipMarkerCache {
    private val guard = ReentrantReadWriteLock()
    private val entries = mutableMapOf<String, CachedMarkers>()

    fun key(apiName: String, seriesUrl: String, seasonIndex: Int?, episodeIndex: Int?): String =
        "$apiName|$seriesUrl|S$seasonIndex|E$episodeIndex"

    fun get(cacheKey: String): CachedMarkers? = guard.read { entries[cacheKey] }

    fun put(cacheKey: String, value: CachedMarkers) = guard.write { entries[cacheKey] = value }

    fun clear() = guard.write { entries.clear() }

    fun size(): Int = guard.read { entries.size }
}

object DesktopStorage {
    val repositoryRevision = kotlinx.coroutines.flow.MutableStateFlow(0)
    fun notifyRepositoriesChanged() { repositoryRevision.value += 1 }

    private val lock = ReentrantReadWriteLock()
    private val settingsFile = File(AppPaths.dataDir, "settings.json")
    private val bookmarksFile = File(AppPaths.dataDir, "bookmarks.json")
    private val historyFile = File(AppPaths.dataDir, "history.json")
    private val reposFile = File(AppPaths.dataDir, "repositories.json")
    private val pluginsFile = File(AppPaths.dataDir, "plugins.json")
    private val downloadsFile = File(AppPaths.dataDir, "downloads.json")
    private val skipSegmentsFile = File(AppPaths.dataDir, "skipSegments.json")

    var settings: AppSettings = loadSettings()
        private set

    private fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val loaded = desktopJson.decodeFromString<AppSettings>(settingsFile.readText())
                // Rebase download folders rooted at the legacy fixed D: path to the
                // current data home; user-chosen folders elsewhere are preserved.
                val fixedDir = if (System.getProperty("os.name").lowercase().contains("win")) {
                    rebaseDownloadsDirectory(
                        loaded.downloadsDirectory,
                        AppPaths.LEGACY_WINDOWS_DATA_PATH,
                        AppPaths.defaultDownloadsDir.absolutePath
                    )
                } else loaded.downloadsDirectory
                val rebased = if (fixedDir != loaded.downloadsDirectory) {
                    loaded.copy(downloadsDirectory = fixedDir)
                } else loaded
                if (rebased != loaded) saveSettings(rebased)
                rebased
            } else {
                AppSettings().also { saveSettings(it) }
            }
        } catch (e: Exception) {
            AppSettings()
        }
    }

    fun saveSettings(newSettings: AppSettings) {
        lock.write {
            settings = newSettings
            try {
                settingsFile.writeText(desktopJson.encodeToString(newSettings))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Bookmarks
    fun getBookmarks(): List<BookmarkItem> = lock.read {
        try {
            if (bookmarksFile.exists()) {
                desktopJson.decodeFromString<List<BookmarkItem>>(bookmarksFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveBookmark(item: BookmarkItem) = lock.write {
        val current = getBookmarks().filterNot { it.url == item.url && it.apiName == item.apiName }
        val updated = listOf(item) + current
        bookmarksFile.writeText(desktopJson.encodeToString(updated))
    }

    fun removeBookmark(url: String, apiName: String) = lock.write {
        val current = getBookmarks().filterNot { it.url == url && it.apiName == apiName }
        bookmarksFile.writeText(desktopJson.encodeToString(current))
    }

    fun getBookmark(url: String, apiName: String): BookmarkItem? = lock.read {
        getBookmarks().find { it.url == url && it.apiName == apiName }
    }

    // History
    fun getHistory(): List<HistoryItem> = lock.read {
        try {
            if (historyFile.exists()) {
                desktopJson.decodeFromString<List<HistoryItem>>(historyFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveHistory(item: HistoryItem) = lock.write {
        val current = getHistory().filterNot { it.url == item.url && it.apiName == item.apiName }
        val updated = listOf(item) + current
        historyFile.writeText(desktopJson.encodeToString(updated))
    }

    fun removeHistory(url: String, apiName: String) = lock.write {
        val current = getHistory().filterNot { it.url == url && it.apiName == apiName }
        historyFile.writeText(desktopJson.encodeToString(current))
    }

    fun clearHistory() = lock.write {
        historyFile.writeText(desktopJson.encodeToString(emptyList<HistoryItem>()))
    }

    val DEFAULT_REPOSITORIES = listOf(
        StoredRepository(
            name = "Phisher Repo (84 Streamers)",
            url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
            description = "84+ English & multi-source movie and series providers"
        ),
        StoredRepository(
            name = "CloudStream Official",
            url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            description = "Official CloudStream providers repository"
        ),
        StoredRepository(
            name = "Mega Repository",
            url = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
            description = "Mega provider aggregator"
        ),
        StoredRepository(
            name = "CNC Repo (All Language)",
            url = "https://raw.githubusercontent.com/NivinCNC/CNCVerse-Cloud-Stream-Extension/refs/heads/builds/CNC.json",
            description = "Community providers in multiple languages, including MovieBox"
        )
    )

    // Repositories
    fun getRepositories(): List<StoredRepository> = lock.read {
        try {
            if (reposFile.exists()) {
                val loaded = desktopJson.decodeFromString<List<StoredRepository>>(reposFile.readText())
                // Upgrade any old dead URLs
                val upgraded = loaded.map { repo ->
                    when {
                        repo.url.contains("recloudstream/cloudstream-extensions/master") ->
                            repo.copy(url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json")
                        repo.url.contains("recloudstream/english-providers") ->
                            DEFAULT_REPOSITORIES[0] // Upgrade to Phisher 84-provider repo
                        else -> repo
                    }
                }.distinctBy { it.url }
                // Seed CNC once for existing installs so its MovieBox build is discoverable.
                val migrationMarker = File(AppPaths.dataDir, "cnc-repository-seeded")
                val withCnc = if (!migrationMarker.exists()) {
                    (upgraded + DEFAULT_REPOSITORIES.last()).distinctBy { it.url }
                } else upgraded
                if (withCnc != loaded) {
                    lock.write { reposFile.writeText(desktopJson.encodeToString(withCnc)) }
                }
                if (!migrationMarker.exists()) migrationMarker.writeText("1")
                withCnc
            } else {
                DEFAULT_REPOSITORIES.also { saveRepositories(it) }
            }
        } catch (e: Exception) {
            DEFAULT_REPOSITORIES
        }
    }

    fun saveRepositories(repos: List<StoredRepository>) = lock.write {
        reposFile.writeText(desktopJson.encodeToString(repos))
    }

    fun addRepository(repo: StoredRepository) = lock.write {
        val current = getRepositories().filterNot { it.url == repo.url }
        saveRepositories(current + repo)
    }

    /** Merge repositories discovered by CloudStream plugins (for example MegaProvider). */
    fun mergePluginRepositories(discovered: List<StoredRepository>) = lock.write {
        if (discovered.isEmpty()) return@write
        val current = getRepositories()
        val byUrl = current.associateBy { it.url }.toMutableMap()
        discovered.forEach { repo ->
            if (repo.url.isNotBlank()) {
                val existing = byUrl[repo.url]
                byUrl[repo.url] = if (existing == null) repo else existing.copy(
                    name = repo.name.ifBlank { existing.name },
                    iconUrl = repo.iconUrl ?: existing.iconUrl
                )
            }
        }
        saveRepositories(byUrl.values.toList())
    }

    fun saveRepository(repo: StoredRepository) = addRepository(repo)

    fun removeRepository(url: String) = lock.write {
        val current = getRepositories().filterNot { it.url == url }
        saveRepositories(current)
    }

    // Installed Plugins
    fun getInstalledPlugins(): List<StoredPlugin> = lock.read {
        try {
            if (pluginsFile.exists()) {
                desktopJson.decodeFromString<List<StoredPlugin>>(pluginsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveInstalledPlugins(plugins: List<StoredPlugin>) = lock.write {
        pluginsFile.writeText(desktopJson.encodeToString(plugins))
    }

    fun savePlugin(plugin: StoredPlugin) = lock.write {
        val current = getInstalledPlugins().filterNot { it.internalName == plugin.internalName }
        saveInstalledPlugins(current + plugin)
    }

    fun removePlugin(internalName: String) = lock.write {
        val current = getInstalledPlugins().filterNot { it.internalName == internalName }
        saveInstalledPlugins(current)
    }

    // Downloads
    fun getDownloads(): List<StoredDownload> = lock.read {
        try {
            if (downloadsFile.exists()) {
                desktopJson.decodeFromString<List<StoredDownload>>(downloadsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDownloads(downloads: List<StoredDownload>) = lock.write {
        downloadsFile.writeText(desktopJson.encodeToString(downloads))
    }

    fun saveDownload(download: StoredDownload) = lock.write {
        val current = getDownloads().filterNot { it.id == download.id }
        saveDownloads(listOf(download) + current)
    }

    fun removeDownload(id: String) = lock.write {
        val current = getDownloads().filterNot { it.id == id }
        saveDownloads(current)
    }

    // Skip intro/recap segments (user-marked, per episode or per series)
    fun getSkipSegments(): List<SkipSegment> = lock.read {
        try {
            if (skipSegmentsFile.exists()) {
                desktopJson.decodeFromString<List<SkipSegment>>(skipSegmentsFile.readText())
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSkipSegment(apiName: String, seriesUrl: String, episodeIndex: Int?, seasonIndex: Int?): SkipSegment? =
        findSkipSegment(getSkipSegments(), apiName, seriesUrl, episodeIndex, seasonIndex)

    fun saveSkipSegment(segment: SkipSegment) = lock.write {
        val current = getSkipSegments().filterNot {
            it.apiName == segment.apiName && it.seriesUrl == segment.seriesUrl &&
                it.episodeIndex == segment.episodeIndex && it.seasonIndex == segment.seasonIndex
        }
        skipSegmentsFile.writeText(desktopJson.encodeToString(current + segment))
    }

    fun removeSkipSegment(apiName: String, seriesUrl: String, episodeIndex: Int?, seasonIndex: Int?) = lock.write {
        val current = getSkipSegments().filterNot {
            it.apiName == apiName && it.seriesUrl == seriesUrl &&
                it.episodeIndex == episodeIndex && it.seasonIndex == seasonIndex
        }
        skipSegmentsFile.writeText(desktopJson.encodeToString(current))
    }

    // --- Automatic skip markers (community services, mirrored from Android) -----
    // Sources: AniSkip (anime OP/recap, MAL ID), TheIntroDB (series + movies
    // with TMDB/IMDb IDs; intro/recap/credits/preview), IntroDb (series with
    // IMDb ID; intro/recap/outro). The login-walled AnimeSkip needs personal
    // credentials and is deliberately excluded. Ending/credits/preview/outro
    // stamps are ignored: desktop offers Intro + Recap only. Only
    // non-identifying metadata ever leaves the device (titles, IDs, season/
    // episode numbers, duration) — never stream URLs, headers, or tokens.

    /**
     * Resolves automatic markers for one episode (or explicitly-identified
     * movie). Manual entries short-circuit with zero network. Otherwise each
     * supporting provider is tried in order with a 6s timeout; the first one
     * yielding usable Intro/Recap data wins, is cached (24h positive / 1h
     * negative) and persisted without overwriting manual entries. Any
     * failure — timeouts, offline, unknown IDs, malformed replies, rate
     * limits — resolves to null (hidden buttons), never a crash or popup.
     * Distinct cuts/runtimes are contained: AniSkip validates episode length
     * server-side, and results are keyed per provider/season/episode.
     */
    suspend fun resolveEpisodeMarkers(
        apiName: String,
        seriesUrl: String,
        title: String,
        tvTypeName: String?,
        year: Int?,
        malId: Int?,
        tmdbId: String?,
        imdbId: String?,
        seasonIndex: Int?,
        episodeIndex: Int?,
        durationMs: Long,
        absoluteEpisodeIndex: Int? = null,
        providers: List<DesktopSkipProvider> = defaultDesktopSkipProviders(),
        malResolver: suspend () -> Int? = { resolveMalId(apiName, seriesUrl, title, tvTypeName, year, malId) },
        tmdbResolver: suspend () -> String? = {
            resolveTmdbId(apiName, seriesUrl, title, year, tmdbId, episodeIndex == null)
        },
        imdbResolver: suspend (tmdbId: String?) -> String? = { tid ->
            resolveImdbId(apiName, seriesUrl, tid, imdbId, episodeIndex == null)
        }
    ): SkipSegment? = withContext(Dispatchers.IO) {
        val manual = getSkipSegment(apiName, seriesUrl, episodeIndex, seasonIndex)
        if (manual?.source == SKIP_SOURCE_MANUAL) return@withContext manual
        val cacheKey = SkipMarkerCache.key(apiName, seriesUrl, seasonIndex, episodeIndex)
        // Cache first: a fresh hit avoids all ID resolution and network.
        // Cached negatives are skipped while the duration is still unknown:
        // duration-gated providers (AniSkip) deserve a retry once it arrives.
        SkipMarkerCache.get(cacheKey)?.let { cached ->
            val negative = cached.markers == null
            if (!negative || durationMs > 0) {
                if (isMarkerCacheFresh(cached.fetchedAtMs, System.currentTimeMillis(), negative)) {
                    val hit = cached.markers?.takeIf { it.hasAnyKnown() }?.toUsable(durationMs)
                    if (hit == null) return@withContext manual
                    return@withContext persistProviderMarkers(
                        apiName, seriesUrl, seasonIndex, episodeIndex, hit, hit.source
                    )
                }
            }
        }
        val resolvedMal = try { malResolver() } catch (_: Throwable) { null }
        val resolvedTmdb = try { tmdbResolver() } catch (_: Throwable) { null }
        // TMDB → IMDb enrichment unlocks IntroDb for TMDB-only titles.
        val resolvedImdb = try { imdbResolver(resolvedTmdb) } catch (_: Throwable) { null }
        val ctx = MarkerLookup(
            apiName = apiName, seriesUrl = seriesUrl, title = title,
            tvTypeName = tvTypeName, year = year,
            malId = resolvedMal, tmdbId = resolvedTmdb, imdbId = resolvedImdb,
            seasonIndex = seasonIndex, episodeIndex = episodeIndex,
            durationMs = durationMs, absoluteEpisodeIndex = absoluteEpisodeIndex
        )
        for (provider in providers) {
            if (!provider.supports(ctx)) continue
            val fetched = try {
                withTimeout(6000L) { provider.fetchMarkers(ctx) }
            } catch (_: Throwable) {
                null
            }
            val usable = fetched?.takeIf { it.hasAnyKnown() }?.toUsable(durationMs)
            if (usable == null) continue
            SkipMarkerCache.put(cacheKey, CachedMarkers(usable, System.currentTimeMillis()))
            return@withContext persistProviderMarkers(
                apiName, seriesUrl, seasonIndex, episodeIndex, usable, provider.name
            )
        }
        if (durationMs > 0) {
            SkipMarkerCache.put(cacheKey, CachedMarkers(null, System.currentTimeMillis()))
        }
        return@withContext manual
    }

    private fun persistProviderMarkers(
        apiName: String,
        seriesUrl: String,
        seasonIndex: Int?,
        episodeIndex: Int?,
        markers: EpisodeMarkers,
        providerName: String
    ): SkipSegment {
        val seg = SkipSegment(
            apiName = apiName,
            seriesUrl = seriesUrl,
            episodeIndex = episodeIndex,
            seasonIndex = seasonIndex,
            introStartMs = markers.introStartMs,
            introEndMs = markers.introEndMs,
            recapStartMs = markers.recapStartMs,
            recapEndMs = markers.recapEndMs,
            endingStartMs = markers.endingStartMs,
            endingEndMs = markers.endingEndMs,
            source = "provider:$providerName"
        )
        // Never overwrite an entry the user made at the same key.
        val existing = getSkipSegments().find {
            it.apiName == apiName && it.seriesUrl == seriesUrl &&
                it.episodeIndex == episodeIndex && it.seasonIndex == seasonIndex
        }
        if (existing?.source == SKIP_SOURCE_MANUAL) return existing
        saveSkipSegment(seg)
        return seg
    }
}
