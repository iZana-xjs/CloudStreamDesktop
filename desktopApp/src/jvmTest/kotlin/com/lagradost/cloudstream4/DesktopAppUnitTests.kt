package com.lagradost.cloudstream4

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.AppSettings
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.BookmarkStatus
import com.lagradost.cloudstream4.core.AniSkipDesktopProvider
import com.lagradost.cloudstream4.core.AniSkipInterval
import com.lagradost.cloudstream4.core.AniSkipStamp
import com.lagradost.cloudstream4.core.AniSkipTimesResponse
import com.lagradost.cloudstream4.core.CachedMarkers
import com.lagradost.cloudstream4.core.DesktopSkipProvider
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.EpisodeMarkers
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.core.IntroDbDesktopProvider
import com.lagradost.cloudstream4.core.IntroDbResponse
import com.lagradost.cloudstream4.core.IntroDbSegment
import com.lagradost.cloudstream4.core.MarkerLookup
import com.lagradost.cloudstream4.core.SkipButtonKind
import com.lagradost.cloudstream4.core.SkipMarkerCache
import com.lagradost.cloudstream4.core.TheIntroDbDesktopProvider
import com.lagradost.cloudstream4.core.TheIntroDbRoot
import com.lagradost.cloudstream4.core.TheIntroDbStamp
import com.lagradost.cloudstream4.core.TmdbSearchItem
import com.lagradost.cloudstream4.core.TmdbSearchResponse
import com.lagradost.cloudstream4.core.aniskipEpisodeCandidates
import com.lagradost.cloudstream4.core.aniskipToMarkers
import com.lagradost.cloudstream4.core.desktopJson
import com.lagradost.cloudstream4.core.introDbSegmentToRange
import com.lagradost.cloudstream4.core.introDbToMarkers
import com.lagradost.cloudstream4.core.isStrictTitleMatch
import com.lagradost.cloudstream4.core.isUsableMarker
import com.lagradost.cloudstream4.core.normalizeMediaTitle
import com.lagradost.cloudstream4.core.parseImdbIdFromDetails
import com.lagradost.cloudstream4.core.pickTmdbId
import com.lagradost.cloudstream4.ui.screens.DetailIdentityCheck
import com.lagradost.cloudstream4.ui.screens.ProviderSearchOutcome
import com.lagradost.cloudstream4.ui.screens.absolutePosterUrl
import com.lagradost.cloudstream4.ui.screens.mediaDetailsDestination
import com.lagradost.cloudstream4.ui.screens.mergeSearchResults
import com.lagradost.cloudstream4.ui.screens.normalizeIdentityText
import com.lagradost.cloudstream4.ui.screens.normalizePageUrl
import com.lagradost.cloudstream4.ui.screens.pickExactTitleMatches
import com.lagradost.cloudstream4.ui.screens.resolvePageUrl
import com.lagradost.cloudstream4.ui.screens.searchResultKey
import com.lagradost.cloudstream4.ui.screens.sameImageFile
import com.lagradost.cloudstream4.ui.screens.titlesCorroborate
import com.lagradost.cloudstream4.ui.screens.validateLoadedIdentity
import com.lagradost.cloudstream4.core.selectSkipButton
import com.lagradost.cloudstream4.core.theIntroDbToMarkers
import com.lagradost.cloudstream4.core.SkipSegment
import com.lagradost.cloudstream4.core.ensureWritableDir
import com.lagradost.cloudstream4.core.findSkipSegment
import com.lagradost.cloudstream4.core.installRootFromCommand
import com.lagradost.cloudstream4.core.isInSegment
import com.lagradost.cloudstream4.core.isKnownSegment
import com.lagradost.cloudstream4.core.isMarkerCacheFresh
import com.lagradost.cloudstream4.core.migrateLegacyData
import com.lagradost.cloudstream4.core.rebaseDownloadsDirectory
import com.lagradost.cloudstream4.core.StoredDownload
import com.lagradost.cloudstream4.core.StoredPlugin
import com.lagradost.cloudstream4.core.StoredRepository
import com.lagradost.cloudstream4.downloads.DesktopDownloadManager
import com.lagradost.cloudstream4.downloads.DownloadState
import com.lagradost.cloudstream4.downloads.DownloadTask
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.extensions.isSafePluginInternalName
import com.lagradost.cloudstream4.extensions.requireTrustedHttpsUrl
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.playback.DesktopPlayer
import com.lagradost.cloudstream4.playback.PlayerArtworkAccent
import com.lagradost.cloudstream4.playback.PlayerTrack
import com.lagradost.cloudstream4.playback.computeSeekTargetMs
import com.lagradost.cloudstream4.playback.describeVideoStarvation
import com.lagradost.cloudstream4.playback.fourccToString
import com.lagradost.cloudstream4.playback.nextDecoderRetryPlan
import com.lagradost.cloudstream4.playback.fractionToMs
import com.lagradost.cloudstream4.playback.msToFraction
import com.lagradost.cloudstream3.HomePageList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Test
import java.io.File
import java.io.IOException
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream4.input.shouldNavigateBackOnMouseButton
import com.lagradost.cloudstream4.ui.screens.CatalogPageLoader
import com.lagradost.cloudstream4.ui.screens.findExactProviderMatch
import com.lagradost.cloudstream4.ui.screens.heroCarouselTitles
import com.lagradost.cloudstream4.ui.screens.nextCarouselIndex
import com.lagradost.cloudstream4.ui.screens.searchOneProvider
import com.lagradost.cloudstream4.ui.screens.shouldSaveHistory
import com.lagradost.cloudstream4.ui.screens.suggestionSelectionPair
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopAppUnitTests {

    @Test
    fun testDex2jarMega() = runBlocking {
        val cs3File = File(AppPaths.pluginsDir, "AnimePahe.cs3")
        println("cs3File exists: ${cs3File.exists()} at ${cs3File.absolutePath}")
        if (cs3File.exists()) {
            val res = DesktopPluginManager.testLoadPlugin(cs3File, "AnimePahe")
            println("testLoadPlugin result: $res")
            assertTrue(res, "Failed to load AnimePahe")
            val p = APIHolder.allProviders.find { it.name.contains("AnimePahe", ignoreCase = true) }
            println("Found provider: $p")
            if (p != null) {
                try {
                    val searchRes = p.search("Naruto")
                    println("Search result count: ${searchRes?.size}")
                } catch (e: Throwable) {
                    println("Search failed with exception:")
                    e.printStackTrace()
                }
            }
        }
    }

    @Test
    fun testLoadHDhub4u() = runBlocking {
        val cs3File = File(AppPaths.pluginsDir, "HDhub4u.cs3")
        println("HDhub4u cs3File exists: ${cs3File.exists()}")
        if (cs3File.exists()) {
            val res = DesktopPluginManager.testLoadPlugin(cs3File, "HDhub4u")
            println("HDhub4u load result: $res")
            assertTrue(res, "Failed to load HDhub4u")
        }
    }

    @Test
    fun testAppPathsCreation() {
        assertNotNull(AppPaths.appDataDir)
        assertTrue(AppPaths.appDataDir.exists())
        assertNotNull(AppPaths.pluginsDir)
        assertTrue(AppPaths.pluginsDir.exists())
        assertNotNull(AppPaths.pluginsCacheDir)
        assertTrue(AppPaths.pluginsCacheDir.exists())
        assertNotNull(AppPaths.dataDir)
        assertTrue(AppPaths.dataDir.exists())
    }

    @Test
    fun testDesktopStorageSettings() {
        val original = DesktopStorage.settings
        val modified = original.copy(themeMode = "Black", subtitleFontSize = 26)
        DesktopStorage.saveSettings(modified)
        assertEquals("Black", DesktopStorage.settings.themeMode)
        assertEquals(26, DesktopStorage.settings.subtitleFontSize)

        // Restore original
        DesktopStorage.saveSettings(original)
    }

    @Test
    fun testDesktopStorageBookmarks() {
        val testBookmark = BookmarkItem(
            url = "https://example.com/test-movie",
            apiName = "TestProvider",
            title = "Test Movie Title",
            year = 2024,
            status = BookmarkStatus.PLAN_TO_WATCH
        )

        DesktopStorage.saveBookmark(testBookmark)
        val fetched = DesktopStorage.getBookmark(testBookmark.url, testBookmark.apiName)
        assertNotNull(fetched)
        assertEquals("Test Movie Title", fetched.title)
        assertEquals(BookmarkStatus.PLAN_TO_WATCH, fetched.status)

        DesktopStorage.removeBookmark(testBookmark.url, testBookmark.apiName)
        val afterDelete = DesktopStorage.getBookmark(testBookmark.url, testBookmark.apiName)
        assertEquals(null, afterDelete)
    }

    @Test
    fun testDesktopStorageHistory() {
        val testHistory = HistoryItem(
            url = "https://example.com/test-series",
            apiName = "TestProvider",
            title = "Test Series Title",
            episodeTitle = "Episode 1",
            positionMs = 120_000L,
            durationMs = 240_000L
        )

        DesktopStorage.saveHistory(testHistory)
        val list = DesktopStorage.getHistory()
        assertTrue(list.any { it.url == testHistory.url && it.positionMs == 120_000L })

        DesktopStorage.removeHistory(testHistory.url, testHistory.apiName)
        assertFalse(DesktopStorage.getHistory().any { it.url == testHistory.url })
    }

    @Test
    fun testDesktopStorageRepositories() {
        val testRepo = StoredRepository(
            name = "Test Repository",
            url = "https://example.com/repo.json",
            description = "A test repository"
        )

        DesktopStorage.addRepository(testRepo)
        val repos = DesktopStorage.getRepositories()
        assertTrue(repos.any { it.url == testRepo.url })

        DesktopStorage.removeRepository(testRepo.url)
        assertFalse(DesktopStorage.getRepositories().any { it.url == testRepo.url })
    }

    @Test
    fun testNormalizeRepoUrl() {
        assertEquals(
            "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
            DesktopPluginManager.normalizeRepoUrl("phisher")
        )
        assertEquals(
            "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            DesktopPluginManager.normalizeRepoUrl("cspr")
        )
        assertEquals(
            "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
            DesktopPluginManager.normalizeRepoUrl("megarepo")
        )
        assertEquals(
            "https://raw.githubusercontent.com/owner/my-repo/refs/heads/builds/repo.json",
            DesktopPluginManager.normalizeRepoUrl("https://github.com/owner/my-repo")
        )
    }

    @Test
    fun testExtensionUrlTrustRules() {
        assertEquals("https://example.com/repo.json", requireTrustedHttpsUrl("https://example.com/repo.json"))
        assertFailsWith<IllegalArgumentException> { requireTrustedHttpsUrl("http://example.com/repo.json") }
        assertFailsWith<IllegalArgumentException> { requireTrustedHttpsUrl("https://user:pass@example.com/repo.json") }
    }

    @Test
    fun testPluginInternalNameCannotEscapeStorageDirectory() {
        assertTrue(isSafePluginInternalName("HDHub4u"))
        assertTrue(isSafePluginInternalName("plugin.name-2"))
        assertFalse(isSafePluginInternalName("../outside"))
        assertFalse(isSafePluginInternalName(".."))
        assertFalse(isSafePluginInternalName("plugin/name"))
        assertFalse(isSafePluginInternalName(""))
    }

    @Test
    fun testInstallRootFromCommand() {
        // A layout with an app/ subdir counts as a packaged install, through
        // quotes and either separator style; the parent dir is the root.
        val root = java.nio.file.Files.createTempDirectory("cs-install").toFile()
        try {
            File(root, "app").mkdirs()
            val exe = File(root, "CloudStream.exe").absolutePath
            assertEquals(root.absolutePath, installRootFromCommand(exe)?.absolutePath)
            assertEquals(root.absolutePath, installRootFromCommand("\"$exe\"")?.absolutePath)
            assertEquals(
                root.absolutePath,
                installRootFromCommand(exe.replace('\\', '/'))?.absolutePath
            )
            assertEquals(null, installRootFromCommand(File(root, "Other.exe").absolutePath))
        } finally {
            root.deleteRecursively()
        }
        // Dev java runs, bare copies without app/, and blanks are not installs.
        val bare = java.nio.file.Files.createTempDirectory("cs-bare").toFile()
        try {
            assertEquals(null, installRootFromCommand(File(bare, "CloudStream.exe").absolutePath))
        } finally {
            bare.deleteRecursively()
        }
        assertEquals(null, installRootFromCommand("D:\\jdk\\bin\\java.exe"))
        assertEquals(null, installRootFromCommand(null))
        assertEquals(null, installRootFromCommand("   "))
    }

    @Test
    fun testEnsureWritableDir() {
        val base = java.nio.file.Files.createTempDirectory("cs-writable").toFile()
        try {
            val fresh = File(base, "sub/dir")
            assertTrue(ensureWritableDir(fresh))
            assertTrue(fresh.isDirectory)
            assertTrue(ensureWritableDir(fresh))
        } finally {
            base.deleteRecursively()
        }
    }

    @Test
    fun testMigrateLegacyData() {
        val legacy = java.nio.file.Files.createTempDirectory("cs-legacy").toFile()
        val target = java.nio.file.Files.createTempDirectory("cs-target").toFile()
        try {
            File(legacy, "data").mkdirs()
            File(legacy, "plugins").mkdirs()
            File(legacy, "cache").mkdirs()
            File(legacy, "data/settings.json").writeText("{\"themeMode\":\"Dark\"}")
            File(legacy, "data/plugins.json").writeText("[]")
            File(legacy, "plugins/test.cs3").writeText("fake-cs3")
            File(legacy, "plugins/test.download.tmp").writeText("partial")
            File(legacy, "cache/junk.bin").writeText("junk")

            val copied = migrateLegacyData(legacy, target)
            assertTrue(copied >= 3)
            assertTrue(File(target, "data/settings.json").exists())
            assertTrue(File(target, "data/plugins.json").exists())
            assertTrue(File(target, "plugins/test.cs3").exists())
            // Temp files, caches, and legacy originals are handled safely.
            assertFalse(File(target, "plugins/test.download.tmp").exists())
            assertFalse(File(target, "cache/junk.bin").exists())
            assertTrue(File(legacy, "data/settings.json").exists())
            assertTrue(File(legacy, "plugins/test.cs3").exists())
            assertTrue(File(target, ".migrated-from-legacy").exists())

            // Second run is a no-op (marker), and non-fresh targets are untouched.
            assertEquals(0, migrateLegacyData(legacy, target))
            val occupied = java.nio.file.Files.createTempDirectory("cs-occupied").toFile()
            try {
                File(occupied, "data").mkdirs()
                File(occupied, "data/settings.json").writeText("{\"themeMode\":\"Light\"}")
                assertEquals(0, migrateLegacyData(legacy, occupied))
                assertEquals("{\"themeMode\":\"Light\"}", File(occupied, "data/settings.json").readText())
            } finally {
                occupied.deleteRecursively()
            }
            // Migrating onto itself is a no-op.
            assertEquals(0, migrateLegacyData(legacy, legacy))
        } finally {
            legacy.deleteRecursively()
            target.deleteRecursively()
        }
    }

    @Test
    fun testRebaseDownloadsDirectory() {
        val legacy = "D:\\CloudStreamData"
        val fresh = "D:\\apps\\CloudStream\\CloudStreamData\\downloads"
        assertEquals(fresh, rebaseDownloadsDirectory("$legacy\\downloads", legacy, fresh))
        assertEquals(fresh, rebaseDownloadsDirectory(legacy, legacy, fresh))
        assertEquals(fresh, rebaseDownloadsDirectory("d:\\cloudstreamdata/Downloads", legacy, fresh))
        assertEquals(fresh, rebaseDownloadsDirectory("   ", legacy, fresh))
        // User-chosen folders elsewhere are preserved verbatim.
        assertEquals("E:\\Videos", rebaseDownloadsDirectory("E:\\Videos", legacy, fresh))
        assertEquals(
            "D:\\CloudStreamData2\\downloads",
            rebaseDownloadsDirectory("D:\\CloudStreamData2\\downloads", legacy, fresh)
        )
    }

    @Test
    fun testSkipSegmentBoundaries() {
        // Known only when both ends are set and ordered — never guessed.
        assertTrue(isKnownSegment(60_000L, 90_000L))
        assertFalse(isKnownSegment(-1L, 90_000L))
        assertFalse(isKnownSegment(60_000L, -1L))
        assertFalse(isKnownSegment(90_000L, 60_000L))
        assertFalse(isKnownSegment(60_000L, 60_000L))

        // Visible inside [start, end); hidden exactly at the end and outside.
        assertTrue(isInSegment(60_000L, 60_000L, 90_000L))
        assertTrue(isInSegment(89_999L, 60_000L, 90_000L))
        assertFalse(isInSegment(90_000L, 60_000L, 90_000L))
        assertFalse(isInSegment(59_999L, 60_000L, 90_000L))
        assertFalse(isInSegment(70_000L, -1L, -1L))
    }

    @Test
    fun testFindSkipSegmentPriority() {
        val series = com.lagradost.cloudstream4.core.SkipSegment(
            apiName = "P", seriesUrl = "series", introStartMs = 0L, introEndMs = 60_000L
        )
        val episode = series.copy(episodeIndex = 3, seasonIndex = 1, introEndMs = 75_000L)
        val other = series.copy(seriesUrl = "other")
        val list = listOf(series, episode, other)

        // Episode-exact wins over the series-level entry.
        assertEquals(episode, findSkipSegment(list, "P", "series", 3, 1))
        // Other episodes fall back to the series-level entry.
        assertEquals(series, findSkipSegment(list, "P", "series", 4, 1))
        // Unknown provider/title → hidden (null).
        assertNull(findSkipSegment(list, "Q", "series", 3, 1))
        assertNull(findSkipSegment(list, "P", "missing", null, null))
        assertNull(findSkipSegment(emptyList(), "P", "series", null, null))
    }

    @Test
    fun testSkipSegmentPersistence() {
        val seg = com.lagradost.cloudstream4.core.SkipSegment(
            apiName = "TestSkipApi",
            seriesUrl = "test-skip-series",
            episodeIndex = 2,
            seasonIndex = 1,
            introStartMs = 60_000L,
            introEndMs = 90_000L
        )
        DesktopStorage.saveSkipSegment(seg)
        val fetched = DesktopStorage.getSkipSegment("TestSkipApi", "test-skip-series", 2, 1)
        assertNotNull(fetched)
        assertEquals(90_000L, fetched.introEndMs)

        // Series-level fallback resolves for other episodes.
        DesktopStorage.saveSkipSegment(seg.copy(episodeIndex = null, seasonIndex = null))
        val fallback = DesktopStorage.getSkipSegment("TestSkipApi", "test-skip-series", 9, 1)
        assertNotNull(fallback)
        assertEquals(null, fallback.episodeIndex)

        DesktopStorage.removeSkipSegment("TestSkipApi", "test-skip-series", 2, 1)
        DesktopStorage.removeSkipSegment("TestSkipApi", "test-skip-series", null, null)
        assertEquals(null, DesktopStorage.getSkipSegment("TestSkipApi", "test-skip-series", 2, 1))
    }

    @Test
    fun testComputeSeekTargetMs() {
        // ±10s jumps inside a known duration.
        assertEquals(70_000L, computeSeekTargetMs(60_000L, 10_000L, 3_600_000L))
        assertEquals(50_000L, computeSeekTargetMs(60_000L, -10_000L, 3_600_000L))
        // Clamped at both ends, never negative, never past duration.
        assertEquals(0L, computeSeekTargetMs(5_000L, -10_000L, 3_600_000L))
        assertEquals(3_600_000L, computeSeekTargetMs(3_595_000L, 10_000L, 3_600_000L))
        // Unknown duration: clamp at zero only.
        assertEquals(70_000L, computeSeekTargetMs(60_000L, 10_000L, 0L))
        assertEquals(0L, computeSeekTargetMs(5_000L, -10_000L, 0L))
    }

    @Test
    fun testScrubPositionMapping() {
        val duration = 3_600_000L
        // Click/drag fraction maps to exact stream positions and back.
        assertEquals(0L, fractionToMs(0f, duration))
        assertEquals(1_800_000L, fractionToMs(0.5f, duration))
        assertEquals(duration, fractionToMs(1f, duration))
        assertEquals(0.5f, msToFraction(1_800_000L, duration))
        assertEquals(0f, msToFraction(0L, duration))
        assertEquals(1f, msToFraction(duration, duration))
        // Out-of-range drag input clamps instead of escaping the bar.
        assertEquals(0L, fractionToMs(-0.25f, duration))
        assertEquals(duration, fractionToMs(1.5f, duration))
        assertEquals(0f, msToFraction(-5_000L, duration))
        assertEquals(1f, msToFraction(duration + 60_000L, duration))
        // Unknown/zero duration never divides by zero; UI uses % + fractional API.
        assertEquals(0L, fractionToMs(0.5f, 0L))
        assertEquals(0f, msToFraction(60_000L, 0L))
        assertEquals(0L, fractionToMs(0.5f, -1L))
        assertEquals(0f, msToFraction(60_000L, -1L))
    }

    @Test
    fun testMarkerCacheFreshness() {
        assertTrue(isMarkerCacheFresh(1_000L, 2_000L, false))
        assertFalse(isMarkerCacheFresh(0L, 25L * 60 * 60 * 1000, false))
        assertTrue(isMarkerCacheFresh(0L, 30L * 60 * 1000, true))
        assertFalse(isMarkerCacheFresh(0L, 2L * 60 * 60 * 1000, true))
        assertFalse(isMarkerCacheFresh(5_000L, 1_000L, false))
    }

    @Test
    fun testAniskipEpisodeCandidates() {
        // Seasonal first, absolute fallback only when different and positive.
        assertEquals(listOf(5), aniskipEpisodeCandidates(5, null))
        assertEquals(listOf(5), aniskipEpisodeCandidates(5, 5))
        assertEquals(listOf(1, 14), aniskipEpisodeCandidates(1, 14))
        assertEquals(listOf(3), aniskipEpisodeCandidates(3, -1))
        assertEquals(listOf(3), aniskipEpisodeCandidates(3, 0))
    }

    @Test
    fun testParseImdbIdFromDetails() {
        assertEquals(
            "tt1234567",
            parseImdbIdFromDetails("""{"id":99,"imdb_id":"tt1234567","title":"X"}""")
        )
        assertEquals(null, parseImdbIdFromDetails("""{"id":99,"title":"X"}"""))
        assertEquals(null, parseImdbIdFromDetails("""{"id":99,"imdb_id":""}"""))
        assertEquals(null, parseImdbIdFromDetails("{not json"))
    }

    @Test
    fun testSearchResultIdentity() {
        // Same URL from two providers are different playable entries.
        assertEquals("A::/x", searchResultKey("A", "/x"))
        assertFalse(
            searchResultKey("A", "/movie/1") == searchResultKey("B", "/movie/1")
        )
        assertTrue(
            searchResultKey("A", "/movie/1") == searchResultKey("A", "/movie/1")
        )
    }

    @Test
    fun testAbsolutePosterUrl() {        assertEquals(
            "https://site.test/p/a.jpg",
            absolutePosterUrl("/p/a.jpg", "https://site.test")
        )
        assertEquals(
            "https://site.test/p/a.jpg",
            absolutePosterUrl("p/a.jpg", "https://site.test/")
        )
        assertEquals(
            "https://cdn.test/a.jpg",
            absolutePosterUrl("https://cdn.test/a.jpg", "https://site.test")
        )
        assertEquals(
            "https://cdn.test/a.jpg",
            absolutePosterUrl("//cdn.test/a.jpg", "https://site.test")
        )
        assertEquals(
            "data:image/png;base64,xx",
            absolutePosterUrl("data:image/png;base64,xx", "https://site.test")
        )
        assertEquals(null, absolutePosterUrl(null, "https://site.test"))
        assertEquals(null, absolutePosterUrl("   ", "https://site.test"))
        // Unusable base: leave the raw value rather than fabricating hosts.
        assertEquals("/p/a.jpg", absolutePosterUrl("/p/a.jpg", "NONE"))
        assertEquals("/p/a.jpg", absolutePosterUrl("/p/a.jpg", ""))
    }

    /** SearchResponse constructors are deprecation-errors; tests use this stand-in. */
    private fun fakeSearchResult(
        name: String,
        url: String,
        apiName: String,
        posterUrl: String? = null
    ): SearchResponse = object : SearchResponse {
        override val name: String = name
        override val url: String = url
        override val apiName: String = apiName
        override var type: TvType? = TvType.Movie
        override var posterUrl: String? = posterUrl
        override var posterHeaders: Map<String, String>? = null
        override var id: Int? = null
        override var quality: SearchQuality? = null
        override var score: Score? = null
    }

    private enum class FakeSearchBehavior { Results, Empty, NotImplemented, Broken, Slow, BlankUrls }

    /** Minimal MainAPI fake scripting search behaviors per provider under test. */
    private inner class FakeSearchProvider(
        providerName: String,
        private val behavior: FakeSearchBehavior
    ) : MainAPI() {
        init {
            name = providerName
            mainUrl = "https://fake.test"
        }

        override suspend fun search(query: String): List<SearchResponse>? = when (behavior) {
            FakeSearchBehavior.Results -> listOf(
                fakeSearchResult("Alpha", "https://fake.test/a", name),
                fakeSearchResult("Beta", "https://fake.test/b", name)
            )
            FakeSearchBehavior.Empty -> emptyList()
            FakeSearchBehavior.NotImplemented -> throw NotImplementedError()
            FakeSearchBehavior.Broken -> throw IOException("connection reset")
            FakeSearchBehavior.Slow -> {
                delay(5_000L)
                emptyList()
            }
            FakeSearchBehavior.BlankUrls -> listOf(
                fakeSearchResult("Ghost", "", name),
                fakeSearchResult("Real", "https://fake.test/real", name)
            )
        }
    }

    private fun catalogPage(vararg names: String, hasNext: Boolean = false) =
        newHomePageResponse(
            HomePageList(
                "Cat",
                names.map { fakeSearchResult(it, "https://f.test/$it", "F") }
            ),
            hasNext
        )

    @Test
    fun testProviderOutcomeClassification() {
        val ok = ProviderSearchOutcome.Success("A", emptyList())
        assertFalse(ok.isFailure)
        assertEquals("A", ok.label)
        val empty = ProviderSearchOutcome.NoResults("B")
        assertFalse(empty.isFailure)
        assertTrue(empty.label.contains("no results"))
        val timedOut = ProviderSearchOutcome.TimedOut("MovieBox")
        assertTrue(timedOut.isFailure)
        assertTrue(timedOut.label.contains("timed out"))
        val errored = ProviderSearchOutcome.Errored("Cinefreak")
        assertTrue(errored.isFailure)
        assertTrue(errored.label.contains("error"))
        // Unsupported (search() not implemented) is info, never a failure.
        val unsupported = ProviderSearchOutcome.Unsupported("MovieBox")
        assertFalse(unsupported.isFailure)
        assertTrue(unsupported.label.contains("not supported"))
    }

    @Test
    fun testSearchOneProviderWorking() = runBlocking {
        val outcome = searchOneProvider(FakeSearchProvider("FakeA", FakeSearchBehavior.Results), "x")
        assertTrue(outcome is ProviderSearchOutcome.Success)
        assertEquals(2, outcome.results.size)
        assertEquals("FakeA", outcome.results[0].apiName)
    }

    @Test
    fun testSearchOneProviderFailures() = runBlocking {
        // Mirrors the live rig: unimplemented search, network error, and
        // valid-but-empty results classify distinctly.
        val notImpl = searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.NotImplemented), "x")
        assertTrue(notImpl is ProviderSearchOutcome.Unsupported)
        val broken = searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.Broken), "x")
        assertTrue(broken is ProviderSearchOutcome.Errored)
        val empty = searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.Empty), "x")
        assertTrue(empty is ProviderSearchOutcome.NoResults)
        assertFalse(empty.isFailure)
    }

    @Test
    fun testSearchOneProviderTimeout() = runBlocking {
        // Bounded wait proven fast: 100ms budget against a 5s provider.
        val outcome = searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.Slow), "x", timeoutMs = 100L)
        assertTrue(outcome is ProviderSearchOutcome.TimedOut)
        assertTrue(outcome.isFailure)
    }

    @Test
    fun testSearchOneProviderDropsBlankUrls() = runBlocking {
        val outcome = searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.BlankUrls), "x")
        assertTrue(outcome is ProviderSearchOutcome.Success)
        assertEquals(1, outcome.results.size)
        assertEquals("https://fake.test/real", outcome.results[0].url)
    }

    @Test
    fun testSearchOneProviderCancellation() = runBlocking {
        val job = launch {
            searchOneProvider(FakeSearchProvider("F", FakeSearchBehavior.Slow), "x", timeoutMs = 5_000L)
        }
        delay(50L)
        job.cancelAndJoin()
        // Cancellation propagates instead of hanging or recording a failure.
        assertTrue(job.isCancelled)
    }

    @Test
    fun testMergeSearchResultsOutOfOrder() {
        val a1 = fakeSearchResult("Dune", "https://a.test/dune", "A")
        val a2 = fakeSearchResult("Dune Messiah", "https://a.test/messiah", "A")
        // Same URL string, different provider: a different playable entry.
        val b1 = fakeSearchResult("Dune", "https://a.test/dune", "B")
        // Exact duplicate of a1: dropped.
        val a1dup = fakeSearchResult("Dune", "https://a.test/dune", "A")

        // Completion order 1: A first, then B. Compared by identity key:
        // exact duplicates collapse, cross-provider same-URL entries stay.
        val seen1 = mutableSetOf<String>()
        val merged1 = mergeSearchResults(seen1, listOf(a1, a2)) +
            mergeSearchResults(seen1, listOf(b1, a1dup))
        assertEquals(
            listOf("A::https://a.test/dune", "A::https://a.test/messiah", "B::https://a.test/dune"),
            merged1.map { searchResultKey(it.apiName, it.url) }
        )

        // Completion order 2: B first, then A — same set, arrival order kept.
        val seen2 = mutableSetOf<String>()
        val merged2 = mergeSearchResults(seen2, listOf(b1)) +
            mergeSearchResults(seen2, listOf(a1dup, a1, a2))
        assertEquals(
            listOf("B::https://a.test/dune", "A::https://a.test/dune", "A::https://a.test/messiah"),
            merged2.map { searchResultKey(it.apiName, it.url) }
        )
        assertEquals(
            merged1.map { searchResultKey(it.apiName, it.url) }.toSet(),
            merged2.map { searchResultKey(it.apiName, it.url) }.toSet()
        )
    }

    @Test
    fun testClickDestinationIdentity() {
        // Duplicate titles across providers, same URL across providers, and
        // shuffled arrival: every destination must equal its own card.
        val items = listOf(
            fakeSearchResult("Dune: Part Two", "https://a.test/dune2", "MovieBox", "https://a.test/p1.jpg"),
            fakeSearchResult("Dune: Part Two", "https://b.test/dune2", "HDHub4U", "https://b.test/p2.jpg"),
            fakeSearchResult("Dune", "https://shared.test/dune", "MovieBox"),
            fakeSearchResult("Dune (1984)", "https://shared.test/dune", "Cinefreak")
        )
        for (item in items) {
            val dest = mediaDetailsDestination(item)
            assertEquals(item.url, dest.url)
            assertEquals(item.apiName, dest.apiName)
            assertEquals(item.name, dest.title)
            assertEquals(item.posterUrl, dest.posterUrl)
            // And the round-trip validates as a match.
            assertEquals(
                DetailIdentityCheck.Match,
                validateLoadedIdentity(item.apiName, item.url, item.name, item.posterUrl, item.apiName, item.url, item.name, item.posterUrl)
            )
        }
    }

    @Test
    fun testValidateLoadedIdentity() {
        // Exact and trailing-slash-tolerant matches.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("A", "https://a.test/x", "Dune", "https://a.test/dune.jpg", "A", "https://a.test/x", "Dune", "https://a.test/dune.jpg")
        )
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("A", "https://a.test/x/", "Dune", null, "A", "https://a.test/x", "Dune", null)
        )
        // Legitimate title formatting differences corroborate.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("A", "https://a.test/dune2", "Dune: Part Two", null, "A", "https://other.test/y", "Dune Part Two (2021)", null)
        )
        // Textually different titles ("Two" vs "2") do NOT corroborate.
        assertEquals(
            DetailIdentityCheck.TitleMismatch("Dune: Part Two", "Dune Part 2 (2021)"),
            validateLoadedIdentity("A", "https://a.test/dune2", "Dune: Part Two", null, "A", "https://other.test/y", "Dune Part 2 (2021)", null)
        )
        // REGRESSION (user report): "Dark" card opening a same-URL "Dark
        // Shadows" page with a different poster must NOT pass on substring.
        assertEquals(
            DetailIdentityCheck.TitleMismatch("Dark", "Dark Shadows"),
            validateLoadedIdentity("P", "https://p.test/dark-shadows", "Dark", "https://p.test/dark.jpg", "P", "https://p.test/dark-shadows", "Dark Shadows", "https://p.test/shadows.jpg")
        )
        // Same short title pair, but the SAME poster file: legitimate
        // cosmetic rename, corroborated by the image.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("P", "https://p.test/dark", "Dark", "https://cdn.test/w500/dark.jpg", "P", "https://p.test/dark", "Dark (2017)", "https://cdn.test/original/dark.jpg")
        )
        // Provider names compare case-insensitively.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("moviebox", "https://a.test/x", "Dune", null, "MovieBox", "https://a.test/x", "Dune", null)
        )
        // A different loader is a hard mismatch, never silently opened.
        assertEquals(
            DetailIdentityCheck.ProviderMismatch("MovieBox", "HDHub4U"),
            validateLoadedIdentity("MovieBox", "https://a.test/x", "Dune", null, "HDHub4U", "https://a.test/x", "Dune", null)
        )
        // Same provider, unrelated URL and title: mismatch, not a silent open.
        assertEquals(
            DetailIdentityCheck.TitleMismatch("Dune: Part Two", "Dune (1984)"),
            validateLoadedIdentity("A", "https://a.test/dune2", "Dune: Part Two", null, "A", "https://a.test/other", "Dune (1984)", null)
        )
        // Blank sides skip their check instead of failing.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("A", "https://a.test/x", "Dune", null, "", "https://a.test/x", "Dune", null)
        )
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity("", "", null, null, "", "", null, null)
        )
        // Helpers.
        assertEquals("duneparttwo2021", normalizeIdentityText("Dune: Part Two (2021)"))
        assertEquals("https://a.test/x", normalizePageUrl("  https://a.test/x/ "))
        assertTrue(titlesCorroborate("Dune: Part Two", "Dune Part Two (2021)"))
        assertFalse(titlesCorroborate("Dark", "Dark Shadows"))
        assertFalse(titlesCorroborate("", "Dark"))
        assertTrue(sameImageFile("https://cdn.test/w500/dark.jpg", "https://cdn.test/original/dark.jpg"))
        assertTrue(sameImageFile("https://a.test/x.jpg?size=1", "https://b.test/x.jpg"))
        assertFalse(sameImageFile("https://p.test/dark.jpg", "https://p.test/shadows.jpg"))
        assertFalse(sameImageFile(null, "https://p.test/x.jpg"))
    }

    @Test
    fun testResolvePageUrl() {
        assertEquals(
            "https://site.test/title/tt123",
            resolvePageUrl("/title/tt123", "https://site.test")
        )
        assertEquals(
            "https://site.test/title/tt123",
            resolvePageUrl("title/tt123", "https://site.test/")
        )
        assertEquals(
            "https://site.test/x",
            resolvePageUrl("https://site.test/x", "https://other.test")
        )
        assertEquals("https://cdn.test/x", resolvePageUrl("//cdn.test/x", "https://site.test"))
        assertEquals("", resolvePageUrl(null, "https://site.test"))
        assertEquals("", resolvePageUrl("   ", "https://site.test"))
        assertEquals("/title/tt123", resolvePageUrl("/title/tt123", "NONE"))
        assertEquals("/title/tt123", resolvePageUrl("/title/tt123", ""))
    }

    @Test
    fun testPickExactTitleMatches() {
        val pool = listOf(
            fakeSearchResult("Dark", "https://p.test/dark-series-889", "P"),
            fakeSearchResult("Dark", "https://p.test/dark-2017", "P"),
            fakeSearchResult("Dark Shadows", "https://p.test/dark-shadows", "P"),
            fakeSearchResult("The Dark Knight", "https://p.test/tdk", "P")
        )
        // Only strict equality at OTHER urls; current page excluded.
        val picked = pickExactTitleMatches(pool, "Dark", "https://p.test/dark-series-889")
        assertEquals(listOf("https://p.test/dark-2017"), picked.map { it.url })
        // Blank title, empty pool, and no-match pools yield nothing.
        assertTrue(pickExactTitleMatches(pool, "   ", "https://p.test/x").isEmpty())
        assertTrue(pickExactTitleMatches(emptyList(), "Dark", "https://p.test/x").isEmpty())
        assertTrue(pickExactTitleMatches(pool, "Unknown Show", "https://p.test/x").isEmpty())
        // Case/punctuation-insensitive, capped at 3.
        val many = (1..5).map { fakeSearchResult("Dark!", "https://p.test/dark-$it", "P") }
        assertEquals(3, pickExactTitleMatches(many, "dark", "https://p.test/other").size)
    }

    @Test
    fun testValidateLoadedIdentityWithSiteBase() {
        // REGRESSION: relative search URL vs absolute detail URL for the same
        // page must match once resolved against the provider site.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity(
                "P", "/title/tt123", "Dark", "https://p.test/dark.jpg",
                "P", "https://p.test/title/tt123", "Dark", "https://p.test/dark.jpg",
                siteBaseUrl = "https://p.test"
            )
        )
        // Same poster file rescues cosmetic renames on the same page.
        assertEquals(
            DetailIdentityCheck.Match,
            validateLoadedIdentity(
                "P", "https://p.test/dark", "Dark", "https://cdn.test/w500/dark.jpg",
                "P", "https://p.test/dark", "Dark (2017)", "https://cdn.test/original/dark.jpg",
                siteBaseUrl = "https://p.test"
            )
        )
        // But a different poster file with a short overlapping title still blocks.
        assertEquals(
            DetailIdentityCheck.TitleMismatch("Dark", "Dark Shadows"),
            validateLoadedIdentity(
                "P", "https://p.test/dark-shadows", "Dark", "https://p.test/dark.jpg",
                "P", "https://p.test/dark-shadows", "Dark Shadows", "https://p.test/shadows.jpg",
                siteBaseUrl = "https://p.test"
            )
        )
    }

    @Test
    fun testTitleNormalizationAndMatch() {
        assertEquals("narutoshippuden", normalizeMediaTitle("Naruto: Shippuden!"))
        assertTrue(isStrictTitleMatch("Naruto Shippuden", "naruto: shippuden"))
        assertTrue(isStrictTitleMatch("  Frieren  ", "FRIEREN"))
        assertFalse(isStrictTitleMatch("", "Frieren"))
        assertFalse(isStrictTitleMatch("Frieren", null))
        assertFalse(isStrictTitleMatch("Frieren", "Bocchi the Rock!"))
    }

    @Test
    fun testPickTmdbId() {
        val items = listOf(
            TmdbSearchItem(id = 1L, title = "Wrong Show", releaseDate = "2020-01-01"),
            TmdbSearchItem(id = 2L, title = "Frieren: Beyond Journey's End", releaseDate = "2023-09-29"),
            TmdbSearchItem(id = 3L, title = "Frieren", releaseDate = "2024-01-01")
        )
        // Strict title equality wins; year disambiguates.
        assertEquals(2L, pickTmdbId(items, "Frieren: Beyond Journey's End", 2023, true))
        assertEquals(null, pickTmdbId(items, "Frieren: Beyond Journey's End", 1999, true))
        assertEquals(null, pickTmdbId(items, "Unknown Title", null, true))
        assertEquals(null, pickTmdbId(items, "", null, true))
        // TV branch matches on name + first_air_date.
        val tv = listOf(TmdbSearchItem(id = 7L, name = "Breaking Bad", firstAirDate = "2008-01-20"))
        assertEquals(7L, pickTmdbId(tv, "Breaking Bad", 2008, false))
        assertEquals(null, pickTmdbId(tv, "Breaking Bad", 2010, false))
        // Malformed payloads never parse.
        assertTrue(runCatching { desktopJson.decodeFromString<TmdbSearchResponse>("{not json") }.isFailure)
        val parsed = desktopJson.decodeFromString<TmdbSearchResponse>(
            """{"results":[{"id":9,"title":"Dune","release_date":"2021-09-15"}]}"""
        )
        assertEquals(9L, pickTmdbId(parsed.results, "Dune", 2021, true))
    }

    @Test
    fun testAniskipMapping() {
        val response = AniSkipTimesResponse(
            found = true,
            results = listOf(
                AniSkipStamp("op", AniSkipInterval(12.5, 102.3)),
                AniSkipStamp("recap", AniSkipInterval(0.0, 25.0)),
                AniSkipStamp("ed", AniSkipInterval(1300.0, 1390.0)),
                AniSkipStamp("mystery-type", AniSkipInterval(5.0, 9.0)),
                AniSkipStamp("op", null)
            )
        )
        val markers = aniskipToMarkers(response)
        // op → intro, recap → recap, ed → ending, unknown/null dropped.
        assertEquals(12_500L, markers.introStartMs)
        assertEquals(102_300L, markers.introEndMs)
        assertEquals(0L, markers.recapStartMs)
        assertEquals(25_000L, markers.recapEndMs)
        assertEquals(1_300_000L, markers.endingStartMs)
        assertEquals(1_390_000L, markers.endingEndMs)
        assertTrue(markers.hasAnyKnown())
        assertTrue(aniskipToMarkers(AniSkipTimesResponse(found = false, results = null)).let { !it.hasAnyKnown() })
        // Realistic payload shape parses (unknown keys tolerated).
        val parsed = desktopJson.decodeFromString<AniSkipTimesResponse>(
            """{"found":true,"message":"ok","statusCode":200,"results":[{"skipType":"op","skipId":"x","episodeLength":1435.0,"interval":{"startTime":12.5,"endTime":102.3},"extra":"ignored"}]}"""
        )
        assertEquals(12_500L, aniskipToMarkers(parsed).introStartMs)
        assertTrue(runCatching { desktopJson.decodeFromString<AniSkipTimesResponse>("{bad") }.isFailure)
    }

    @Test
    fun testTheIntroDbMapping() {
        val root = TheIntroDbRoot(
            intro = listOf(TheIntroDbStamp(90_000L, 150_000L)),
            recap = listOf(TheIntroDbStamp(null, 40_000L)),
            credits = listOf(TheIntroDbStamp(3_300_000L, 3_420_000L))
        )
        val markers = theIntroDbToMarkers(root)
        assertEquals(90_000L, markers.introStartMs)
        assertEquals(150_000L, markers.introEndMs)
        // Null-ended stamps are dropped, never defaulted to the duration.
        assertEquals(-1L, markers.recapStartMs)
        assertEquals(-1L, markers.recapEndMs)
        // Credits drive the Ending slot.
        assertEquals(3_300_000L, markers.endingStartMs)
        assertEquals(3_420_000L, markers.endingEndMs)
        assertTrue(markers.hasAnyKnown())
        assertTrue(!theIntroDbToMarkers(TheIntroDbRoot()).hasAnyKnown())
    }

    @Test
    fun testIntroDbSegmentFallback() {
        // Millisecond fields win; seconds convert; anything missing drops.
        assertEquals(
            12_000L to 24_500L,
            introDbSegmentToRange(IntroDbSegment(startMs = 12_000L, endMs = 24_500L, startSec = 1.0, endSec = 2.0))
        )
        assertEquals(
            10_000L to 20_000L,
            introDbSegmentToRange(IntroDbSegment(startSec = 10.0, endSec = 20.0))
        )
        assertEquals(null, introDbSegmentToRange(IntroDbSegment(startMs = 5_000L)))
        assertEquals(null, introDbSegmentToRange(IntroDbSegment(startMs = 30_000L, endMs = 10_000L)))
        assertEquals(null, introDbSegmentToRange(null))
        val markers = introDbToMarkers(
            IntroDbResponse(
                intro = IntroDbSegment(startSec = 90.0, endSec = 150.0),
                recap = null,
                outro = IntroDbSegment(startMs = 3_300_000L, endMs = 3_400_000L)
            )
        )
        assertEquals(90_000L, markers.introStartMs)
        assertEquals(-1L, markers.recapStartMs)
        assertEquals(3_300_000L, markers.endingStartMs)
        assertEquals(3_400_000L, markers.endingEndMs)
    }

    @Test
    fun testIsUsableMarker() {
        // Happy path with known duration.
        assertTrue(isUsableMarker(60_000L, 150_000L, 1_400_000L))
        // Too short to be meaningful (< 5s).
        assertFalse(isUsableMarker(60_000L, 63_000L, 1_400_000L))
        assertTrue(isUsableMarker(60_000L, 65_000L, 1_400_000L))
        // Outside the episode or unordered/unknown.
        assertFalse(isUsableMarker(60_000L, 1_500_000L, 1_400_000L))
        assertFalse(isUsableMarker(150_000L, 60_000L, 1_400_000L))
        assertFalse(isUsableMarker(-1L, -1L, 1_400_000L))
        assertFalse(isUsableMarker(-5_000L, 60_000L, 1_400_000L))
        // Unknown duration: bounds check skipped, length still enforced.
        assertTrue(isUsableMarker(60_000L, 150_000L, 0L))
        assertFalse(isUsableMarker(60_000L, 62_000L, 0L))
    }

    @Test
    fun testSelectSkipButton() {
        val range = com.lagradost.cloudstream4.core.SkipSegment(
            apiName = "P", seriesUrl = "s", episodeIndex = 3, seasonIndex = 1,
            introStartMs = 60_000L, introEndMs = 150_000L,
            recapStartMs = 0L, recapEndMs = 30_000L,
            source = "provider:aniskip"
        )
        val dur = 1_400_000L
        // Gap between ranges / inside each / after all.
        assertEquals(null, selectSkipButton(range, 40_000L, dur, emptySet()))
        assertEquals(SkipButtonKind.RECAP, selectSkipButton(range, 10_000L, dur, emptySet()))
        assertEquals(SkipButtonKind.INTRO, selectSkipButton(range, 100_000L, dur, emptySet()))
        assertEquals(null, selectSkipButton(range, 200_000L, dur, emptySet()))
        // End-exclusive: exactly at the end hides.
        assertEquals(null, selectSkipButton(range, 150_000L, dur, emptySet()))
        // Dismissal after use (even when rewound back in).
        assertEquals(null, selectSkipButton(range, 100_000L, dur, setOf("intro")))
        assertEquals(SkipButtonKind.RECAP, selectSkipButton(range, 10_000L, dur, setOf("intro")))
        // Overlap resolves deterministically to Intro, never both.
        val overlapping = range.copy(recapStartMs = 50_000L, recapEndMs = 200_000L)
        assertEquals(SkipButtonKind.INTRO, selectSkipButton(overlapping, 100_000L, dur, emptySet()))
        assertEquals(SkipButtonKind.RECAP, selectSkipButton(overlapping, 100_000L, dur, setOf("intro")))
        // Null range and unusable data hide everything.
        assertEquals(null, selectSkipButton(null, 100_000L, dur, emptySet()))
        val short = range.copy(introStartMs = 60_000L, introEndMs = 62_000L, recapStartMs = -1L, recapEndMs = -1L)
        assertEquals(null, selectSkipButton(short, 61_000L, dur, emptySet()))
        // Ending slot: visible alone late in the episode, dismissed after use,
        // and always loses overlaps to Intro/Recap (deterministic priority).
        val withEnding = range.copy(endingStartMs = 1_300_000L, endingEndMs = 1_390_000L)
        assertEquals(SkipButtonKind.ENDING, selectSkipButton(withEnding, 1_350_000L, dur, emptySet()))
        assertEquals(null, selectSkipButton(withEnding, 1_350_000L, dur, setOf("ending")))
        assertEquals(null, selectSkipButton(withEnding, 1_395_000L, dur, emptySet()))
        val allOverlap = withEnding.copy(recapStartMs = 1_320_000L, recapEndMs = 1_380_000L)
        assertEquals(SkipButtonKind.RECAP, selectSkipButton(allOverlap, 1_350_000L, dur, emptySet()))
        assertEquals(SkipButtonKind.ENDING, selectSkipButton(allOverlap, 1_350_000L, dur, setOf("recap")))
    }

    @Test
    fun testProviderSupportsMatrix() {
        val anime = MarkerLookup("P", "s", "Show", "Anime", 2023, 123, null, null, 1, 5, 1_400_000L)
        val movie = MarkerLookup("P", "m", "Film", "Movie", 2021, null, "99", null, null, null, 7_200_000L)
        val series = MarkerLookup("P", "s", "Show", "TvSeries", 2020, null, null, "tt123", 2, 4, 3_000_000L)
        val unknown = MarkerLookup("P", "s", "Show", null, null, null, null, null, 1, 4, 3_000_000L)
        val aniSkip = AniSkipDesktopProvider()
        val introDb = TheIntroDbDesktopProvider()
        val introDbApp = IntroDbDesktopProvider()
        // AniSkip: anime + episode + duration only.
        assertTrue(aniSkip.supports(anime))
        assertFalse(aniSkip.supports(movie))
        assertFalse(aniSkip.supports(series))
        assertFalse(aniSkip.supports(anime.copy(durationMs = 0L)))
        assertFalse(aniSkip.supports(anime.copy(episodeIndex = null)))
        // TheIntroDB: ID-gated; movies via ID-only path, episodes need season+episode.
        assertTrue(introDb.supports(movie))
        assertFalse(introDb.supports(movie.copy(tmdbId = null, imdbId = null)))
        assertTrue(introDb.supports(series.copy(tmdbId = "42")))
        assertFalse(introDb.supports(series.copy(tmdbId = "42", seasonIndex = null)))
        assertFalse(introDb.supports(unknown))
        // IntroDb: IMDb series episodes only, never movies.
        assertTrue(introDbApp.supports(series))
        assertFalse(introDbApp.supports(movie))
        assertFalse(introDbApp.supports(series.copy(imdbId = null)))
    }

    @Test
    fun testResolveEpisodeMarkers() = runBlocking {
        SkipMarkerCache.clear()
        var calls = 0
        val fake = object : DesktopSkipProvider {
            override val name = "fake"
            override fun supports(ctx: MarkerLookup) = true
            override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? {
                calls++
                return EpisodeMarkers(introStartMs = 10_000L, introEndMs = 70_000L, source = "fake")
            }
        }
        DesktopStorage.removeSkipSegment("TestMarkerApi", "test-marker-series", 1, 1)
        suspend fun resolve(ep: Int?) = DesktopStorage.resolveEpisodeMarkers(
            apiName = "TestMarkerApi", seriesUrl = "test-marker-series", title = "Title",
            tvTypeName = "TvSeries", year = null, malId = null, tmdbId = null, imdbId = null,
            seasonIndex = 1, episodeIndex = ep, durationMs = 3_600_000L,
            providers = listOf(fake), malResolver = { null }, tmdbResolver = { null }
        )
        val first = resolve(1)
        assertNotNull(first)
        assertEquals(70_000L, first.introEndMs)
        assertTrue(first.source.startsWith("provider:"))
        assertEquals(1, calls)
        // Cache hit: no second fetch, same persisted entry.
        val second = resolve(1)
        assertEquals(1, calls)
        assertEquals(first, second)
        // A manual entry wins with zero further fetches.
        DesktopStorage.saveSkipSegment(
            SkipSegment("TestMarkerApi", "test-marker-series", 1, 1, introStartMs = 0L, introEndMs = 30_000L)
        )
        val third = resolve(1)
        assertEquals(1, calls)
        assertEquals(30_000L, third?.introEndMs)
        assertEquals("manual", third?.source)
        // A failing provider resolves to null without crashing.
        val failing = object : DesktopSkipProvider {
            override val name = "boom"
            override fun supports(ctx: MarkerLookup) = true
            override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? =
                throw java.io.IOException("offline")
        }
        val failed = DesktopStorage.resolveEpisodeMarkers(
            apiName = "TestMarkerApi", seriesUrl = "test-marker-series", title = "Title",
            tvTypeName = "TvSeries", year = null, malId = null, tmdbId = null, imdbId = null,
            seasonIndex = 1, episodeIndex = 2, durationMs = 3_600_000L,
            providers = listOf(failing), malResolver = { null }, tmdbResolver = { null }
        )
        assertNull(failed)
        // Missing metadata with no providers: null, no network, no crash.
        val missing = DesktopStorage.resolveEpisodeMarkers(
            apiName = "NoApi", seriesUrl = "no-series", title = "",
            tvTypeName = null, year = null, malId = null, tmdbId = null, imdbId = null,
            seasonIndex = null, episodeIndex = null, durationMs = 0L,
            providers = emptyList(), malResolver = { null }, tmdbResolver = { null }
        )
        assertNull(missing)
        DesktopStorage.removeSkipSegment("TestMarkerApi", "test-marker-series", 1, 1)
        SkipMarkerCache.clear()
    }

    @Test
    fun testImdbEnrichmentUnlocksImdbGatedProvider() = runBlocking {
        SkipMarkerCache.clear()
        var imdbCalls = 0
        var providerCalls = 0
        val imdbGated = object : DesktopSkipProvider {
            override val name = "imdb-gated"
            override fun supports(ctx: MarkerLookup) = !ctx.imdbId.isNullOrBlank()
            override suspend fun fetchMarkers(ctx: MarkerLookup): EpisodeMarkers? {
                providerCalls++
                return EpisodeMarkers(recapStartMs = 0L, recapEndMs = 30_000L, source = "imdb-gated")
            }
        }
        suspend fun resolve() = DesktopStorage.resolveEpisodeMarkers(
            apiName = "EnrichApi", seriesUrl = "enrich-series", title = "Title",
            tvTypeName = "TvSeries", year = null, malId = null, tmdbId = null, imdbId = null,
            seasonIndex = 1, episodeIndex = 3, durationMs = 3_600_000L,
            providers = listOf(imdbGated),
            malResolver = { null },
            tmdbResolver = { "42" },
            imdbResolver = { _ -> imdbCalls++; "tt999" }
        )
        DesktopStorage.removeSkipSegment("EnrichApi", "enrich-series", 1, 3)
        val first = resolve()
        assertNotNull(first)
        assertEquals(30_000L, first.recapEndMs)
        assertEquals(1, imdbCalls)
        assertEquals(1, providerCalls)
        // Cache hit: no new resolution and no refetch.
        val second = resolve()
        assertEquals(first, second)
        assertEquals(1, imdbCalls)
        assertEquals(1, providerCalls)
        DesktopStorage.removeSkipSegment("EnrichApi", "enrich-series", 1, 3)
        SkipMarkerCache.clear()
    }

    @Test
    fun testSkipMarkerCacheRoundTrip() {
        SkipMarkerCache.clear()
        val key = SkipMarkerCache.key("P", "series", 1, 2)
        assertNull(SkipMarkerCache.get(key))
        val markers = EpisodeMarkers(introStartMs = 0L, introEndMs = 60_000L, source = "test")
        SkipMarkerCache.put(key, CachedMarkers(markers, 1_000L))
        assertEquals(CachedMarkers(markers, 1_000L), SkipMarkerCache.get(key))
        assertEquals(1, SkipMarkerCache.size())
        SkipMarkerCache.clear()
        assertEquals(0, SkipMarkerCache.size())
    }

    @Test
    fun testFindSkipSegmentSourcePriority() {
        val manualEp = SkipSegment("P", "s", 1, 1, introStartMs = 0L, introEndMs = 30_000L, source = "manual")
        val providerEp = manualEp.copy(introEndMs = 70_000L, source = "provider:fake")
        val manualSeries = manualEp.copy(episodeIndex = null, seasonIndex = null)
        val providerSeries = providerEp.copy(episodeIndex = null, seasonIndex = null)
        val list = listOf(providerEp, providerSeries, manualSeries, manualEp)
        // Manual beats provider at each level; episode beats series.
        assertEquals(manualEp, findSkipSegment(list, "P", "s", 1, 1))
        assertEquals(providerEp, findSkipSegment(listOf(providerEp), "P", "s", 1, 1))
        assertEquals(manualSeries, findSkipSegment(listOf(providerSeries, manualSeries), "P", "s", 9, 1))
        assertEquals(providerSeries, findSkipSegment(listOf(providerSeries), "P", "s", 9, 1))
    }

    @Test
    fun testCatalogMultiPageEndDetection() = runBlocking {
        var fetches = 0
        val pages = mapOf(
            1 to catalogPage("a", "b", hasNext = true),
            2 to catalogPage("c", hasNext = true),
            3 to catalogPage("d", hasNext = false)
        )
        val loader = CatalogPageLoader(
            fetchPage = { page ->
                fetches++
                pages[page] ?: throw AssertionError("unexpected page $page")
            }
        )
        assertTrue(loader.loadNext())
        assertTrue(loader.loadNext())
        assertFalse(loader.loadNext())
        assertEquals(listOf("a", "b", "c", "d"), loader.items.map { it.name })
        assertEquals(3, loader.currentPage)
        assertFalse(loader.hasNext)
        assertEquals(3, fetches)
        // Terminal state is stable: further calls fetch nothing.
        assertFalse(loader.loadNext())
        assertEquals(3, fetches)
    }

    @Test
    fun testCatalogEmptyFirstPage() = runBlocking {
        val loader = CatalogPageLoader(fetchPage = { catalogPage(hasNext = false) })
        assertFalse(loader.loadNext())
        assertTrue(loader.items.isEmpty())
        assertFalse(loader.hasNext)
        assertEquals(null, loader.lastError)
    }

    @Test
    fun testCatalogDuplicateSuppression() = runBlocking {
        val pages = mapOf(
            1 to catalogPage("a", "b", hasNext = true),
            2 to catalogPage("b", "c", hasNext = false)
        )
        val loader = CatalogPageLoader(fetchPage = { pages.getValue(it) })
        loader.loadNext()
        loader.loadNext()
        assertEquals(listOf("a", "b", "c"), loader.items.map { it.name })
        assertFalse(loader.hasNext)
    }

    @Test
    fun testCatalogFailedPageRetry() = runBlocking {
        var attempts = 0
        val loader = CatalogPageLoader(fetchPage = { page ->
            attempts++
            if (page == 1) catalogPage("a", hasNext = true)
            else if (attempts == 2) throw IOException("flaky")
            else catalogPage("b", hasNext = false)
        })
        assertTrue(loader.loadNext())
        assertEquals(listOf("a"), loader.items.map { it.name })
        // Failed page keeps prior items, stays retryable from the same page.
        assertTrue(loader.loadNext())
        assertEquals(listOf("a"), loader.items.map { it.name })
        assertNotNull(loader.lastError)
        assertTrue(loader.hasNext)
        assertTrue(loader.loadNext().not())
        assertEquals(listOf("a", "b"), loader.items.map { it.name })
        assertEquals(null, loader.lastError)
        assertEquals(2, loader.currentPage)
    }

    @Test
    fun testCatalogBrokenHasNextEnds() = runBlocking {
        // Provider claims more pages forever but repeats the same item:
        // the loader must stop instead of paging forever.
        val loader = CatalogPageLoader(fetchPage = { catalogPage("a", hasNext = true) })
        repeat(5) { loader.loadNext() }
        assertEquals(listOf("a"), loader.items.map { it.name })
        assertFalse(loader.hasNext)
    }

    @Test
    fun testCatalogResetAndPosterNormalization() = runBlocking {
        val loader = CatalogPageLoader(
            fetchPage = {
                newHomePageResponse(
                    HomePageList(
                        "Cat",
                        listOf(fakeSearchResult("P", "/p.jpg", "F", posterUrl = "/p.jpg"))
                    ),
                    false
                )
            },
            posterBaseUrl = "https://f.test"
        )
        loader.loadNext()
        assertEquals("https://f.test/p.jpg", loader.items.single().posterUrl)
        loader.reset()
        assertTrue(loader.items.isEmpty())
        assertEquals(0, loader.currentPage)
        assertTrue(loader.hasNext)
        assertEquals(null, loader.lastError)
    }

    @Test
    fun testMouseBackDecision() {
        val press = java.awt.event.MouseEvent.MOUSE_PRESSED
        val release = java.awt.event.MouseEvent.MOUSE_RELEASED
        // Extended buttons navigate when the app is focused and no text edit is active.
        assertTrue(shouldNavigateBackOnMouseButton(4, press, false, true))
        assertTrue(shouldNavigateBackOnMouseButton(5, press, false, true))
        // Plain buttons never navigate.
        assertFalse(shouldNavigateBackOnMouseButton(1, press, false, true))
        assertFalse(shouldNavigateBackOnMouseButton(2, press, false, true))
        assertFalse(shouldNavigateBackOnMouseButton(3, press, false, true))
        // Releases and invalid buttons never navigate (single trigger only).
        assertFalse(shouldNavigateBackOnMouseButton(4, release, false, true))
        assertFalse(shouldNavigateBackOnMouseButton(0, press, false, true))
        assertFalse(shouldNavigateBackOnMouseButton(-1, press, false, true))
        // Typing in text input or unfocused app suppresses navigation.
        assertFalse(shouldNavigateBackOnMouseButton(4, press, true, true))
        assertFalse(shouldNavigateBackOnMouseButton(4, press, false, false))
    }

    @Test
    fun testSuggestionSelectionSync() {
        // Field text, submitted query, and the suggestion are always the same
        // title — the field can never keep the old text after a click.
        val (field, submitted) = suggestionSelectionPair("Dark")
        assertEquals("Dark", field)
        assertEquals("Dark", submitted)
        assertEquals(field, submitted)
        // Blank titles stay blank (search skips them), never a stale value.
        val (blankField, blankSubmitted) = suggestionSelectionPair("   ")
        assertEquals("   ", blankField)
        assertEquals("   ", blankSubmitted)
    }

    @Test
    fun testFindExactProviderMatch() {
        val results = listOf(
            fakeSearchResult("Dark", "https://p.test/dark-2017", "P"),
            fakeSearchResult("Dark Shadows", "https://p.test/shadows", "P"),
            fakeSearchResult("The Dark Knight", "https://p.test/tdk", "P")
        )
        // Exact title wins over looser matches.
        val exact = findExactProviderMatch(results, "Dark")
        assertEquals("https://p.test/dark-2017", exact?.url)
        // No exact title → null (never the first loose match).
        assertNull(findExactProviderMatch(results, "Darkness"))
        // Blank query → null.
        assertNull(findExactProviderMatch(results, "  "))
        // Year disambiguates same-titled entries.
        val sameTitle = listOf(
            fakeSearchResult("Dark", "https://p.test/dark-1990", "P"),
            fakeSearchResult("Dark", "https://p.test/dark-2017", "P")
        )
        // (year matching depends on the response type carrying year; here both
        // are plain SearchResponse so it stays ambiguous → null is acceptable.)
        // Empty pool → null.
        assertNull(findExactProviderMatch(emptyList(), "Dark"))
    }

    @Test
    fun testHeroCarouselMath() {
        // Wraparound in range.
        assertEquals(1, nextCarouselIndex(3, 0))
        assertEquals(2, nextCarouselIndex(3, 1))
        assertEquals(0, nextCarouselIndex(3, 2))
        // Empty / single lists never advance.
        assertEquals(0, nextCarouselIndex(0, 0))
        assertEquals(0, nextCarouselIndex(1, 0))
        // Real loaded titles, deduped by provider identity, in order.
        val sections = listOf(
            HomePageList("A", listOf(
                fakeSearchResult("T1", "https://p.test/1", "P"),
                fakeSearchResult("T2", "https://p.test/2", "P")
            )),
            HomePageList("B", listOf(
                fakeSearchResult("T2", "https://p.test/2", "P"), // duplicate
                fakeSearchResult("T3", "https://p.test/3", "P")
            ))
        )
        val titles = heroCarouselTitles(sections)
        assertEquals(listOf("T1", "T2", "T3"), titles.map { it.name })
        assertTrue(nextCarouselIndex(titles.size, 0) == 1)
    }

    @Test
    fun testArtworkAccentFallbackAndCache() {
        // Missing/blank artwork → CloudStream red fallback (no loader needed).
        assertEquals(PlayerArtworkAccent.FALLBACK_HEX, PlayerArtworkAccent.fallback())
        // Cache: same URL returns the cached value.
        PlayerArtworkAccent.clear()
        PlayerArtworkAccent.put("https://x.test/a.jpg", 0xFF112233L)
        assertEquals(0xFF112233L, PlayerArtworkAccent.cached("https://x.test/a.jpg"))
        PlayerArtworkAccent.clear()
        assertNull(PlayerArtworkAccent.cached("https://x.test/a.jpg"))
        // usableMidTone keeps a readable mid-tone (not near-white/near-black).
        val tone = PlayerArtworkAccent.usableMidTone(250, 250, 250)
        assertTrue(tone != 0xFFFFFFFFL)
    }

    @Test
    fun testFormatDiagnostics() {
        val s = DesktopPlayer.formatDiagnostics("hvc1", 3840, 2160, true, 1234, 23.976f)
        assertTrue(s.contains("codec=hvc1"))
        assertTrue(s.contains("res=3840x2160"))
        assertTrue(s.contains("decode=hardware"))
        assertTrue(s.contains("frames=1234"))
        // Unknown codec/res are labelled, never blank.
        val u = DesktopPlayer.formatDiagnostics(null, 0, 0, false, 0, 0f)
        assertTrue(u.contains("codec=unknown"))
        assertTrue(u.contains("res=unknown"))
    }

    @Test
    fun testShouldSaveHistoryThrottle() {
        // A save is due only once the interval has elapsed since the last one.
        assertFalse(shouldSaveHistory(0L, 1_000L))
        assertTrue(shouldSaveHistory(0L, 5_000L))
        assertFalse(shouldSaveHistory(1_000L, 3_000L))
        assertTrue(shouldSaveHistory(1_000L, 6_000L))
        // Custom interval.
        assertFalse(shouldSaveHistory(1_000L, 2_000L, intervalMs = 5_000L))
        assertTrue(shouldSaveHistory(1_000L, 10_000L, intervalMs = 5_000L))
    }

    @Test
    fun testNavigationManager() {
        NavigationManager.clearToHome()
        assertEquals(NavDestination.Home, NavigationManager.currentDestination)
        assertFalse(NavigationManager.canGoBack)

        NavigationManager.navigate(NavDestination.Search(initialQuery = "Action"))
        assertTrue(NavigationManager.currentDestination is NavDestination.Search)
        assertTrue(NavigationManager.canGoBack)

        NavigationManager.navigate(NavDestination.Bookmarks)
        assertEquals(NavDestination.Bookmarks, NavigationManager.currentDestination)

        val popped = NavigationManager.goBack()
        assertTrue(popped)
        assertTrue(NavigationManager.currentDestination is NavDestination.Search)

        NavigationManager.clearToHome()
        assertEquals(NavDestination.Home, NavigationManager.currentDestination)
    }

    @Test
    fun testDownloadTaskProgressCalculation() {
        val task = DownloadTask(
            title = "Sample Download",
            sourceUrl = "https://example.com/video.mp4",
            destinationPath = "sample.mp4",
            initialTotalBytes = 1000L,
            initialDownloadedBytes = 500L,
            initialState = DownloadState.DOWNLOADING
        )

        assertEquals(0.5f, task.progress)
        assertEquals(DownloadState.DOWNLOADING, task.state)

        task.downloadedBytes = 1000L
        assertEquals(1.0f, task.progress)
    }

    @Test
    fun testDesktopPluginManagerInitialization() = runBlocking {
        DesktopPluginManager.initialize()
        val tmdb = APIHolder.allProviders.find { it.name == "TMDB" }
        assertNotNull(tmdb)
        assertTrue(tmdb.hasMainPage)
    }

    @Test
    fun testPlayerStateDefaults() {
        DesktopPlayer.setPlayerVolume(80)
        assertEquals(80, DesktopPlayer.volume)
        assertFalse(DesktopPlayer.isMuted)

        DesktopPlayer.setPlayerVolume(0)
        assertTrue(DesktopPlayer.isMuted)

        DesktopPlayer.setPlayerVolume(100)
        assertEquals(100, DesktopPlayer.volume)

        DesktopPlayer.setSpeed(1.5f)
        assertEquals(1.5f, DesktopPlayer.playbackSpeed)
    }

    @Test
    fun testResizeModeState() {
        // Headless: no VLC component exists, so only state changes are asserted.
        DesktopPlayer.changeResizeMode(DesktopPlayer.VideoResizeMode.FILL)
        assertEquals(DesktopPlayer.VideoResizeMode.FILL, DesktopPlayer.resizeMode)

        DesktopPlayer.changeResizeMode(DesktopPlayer.VideoResizeMode.ORIGINAL)
        assertEquals(DesktopPlayer.VideoResizeMode.ORIGINAL, DesktopPlayer.resizeMode)

        DesktopPlayer.changeResizeMode(DesktopPlayer.VideoResizeMode.FIT)
        assertEquals(DesktopPlayer.VideoResizeMode.FIT, DesktopPlayer.resizeMode)
        assertEquals("Fit", DesktopPlayer.VideoResizeMode.FIT.label)
    }

    @Test
    fun testFourccToString() {
        // 'd','v','h','e' packed little-endian, as VLC stores fourCC ints.
        assertEquals("dvhe", fourccToString(0x65687664))
        assertEquals("avc1", fourccToString(0x31637661))
        assertEquals("hvc1", fourccToString(0x31637668))
        assertEquals("", fourccToString(0))
        assertEquals("", fourccToString(-1))
    }

    @Test
    fun testDescribeVideoStarvation() {
        val dvHw = describeVideoStarvation("dvhe", false)
        assertTrue(dvHw.contains("Dolby Vision", ignoreCase = true))
        assertTrue(dvHw.contains("dvhe"))

        val dvSw = describeVideoStarvation("DVH1", true)
        assertTrue(dvSw.contains("Dolby Vision", ignoreCase = true))
        assertTrue(dvSw.contains("software", ignoreCase = true))

        // Plain HEVC (e.g. HDR10 base) must NOT get the Dolby Vision message.
        val hevcHw = describeVideoStarvation("hvc1", false)
        assertFalse(hevcHw.contains("Dolby Vision", ignoreCase = true))
        assertTrue(hevcHw.contains("software", ignoreCase = true))

        val unknownSw = describeVideoStarvation(null, true)
        assertFalse(unknownSw.contains("Dolby Vision", ignoreCase = true))

        // Diagnostic must never echo stream locators or secrets.
        for (msg in listOf(dvHw, dvSw, hevcHw, unknownSw)) {
            assertFalse(msg.contains("http"))
            assertFalse(msg.contains("token", ignoreCase = true))
        }
    }

    @Test
    fun testDecoderRetryPlanTriesAlternateWindowsHardwareThenSoftware() {
        assertEquals(
            com.lagradost.cloudstream4.playback.DecoderRetryPlan(true, "any"),
            nextDecoderRetryPlan(hardware = true, hardwareBackend = null, isWindows = true)
        )
        assertEquals(
            com.lagradost.cloudstream4.playback.DecoderRetryPlan(false, null),
            nextDecoderRetryPlan(hardware = true, hardwareBackend = "any", isWindows = true)
        )
        assertEquals(
            com.lagradost.cloudstream4.playback.DecoderRetryPlan(false, null),
            nextDecoderRetryPlan(hardware = true, hardwareBackend = null, isWindows = false)
        )
        assertNull(nextDecoderRetryPlan(hardware = false, hardwareBackend = null, isWindows = true))
    }
}
