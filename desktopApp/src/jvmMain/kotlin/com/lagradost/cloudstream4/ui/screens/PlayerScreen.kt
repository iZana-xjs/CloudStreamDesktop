package com.lagradost.cloudstream4.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.core.SkipButtonKind
import com.lagradost.cloudstream4.core.selectSkipButton
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.navigation.PlaybackSource
import com.lagradost.cloudstream4.navigation.handleSourceListKey
import com.lagradost.cloudstream4.navigation.sortPlaybackSources
import com.lagradost.cloudstream4.ui.components.ShiopaIcon
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.playback.DesktopPlayer
import com.lagradost.cloudstream4.playback.fractionToMs
import com.lagradost.cloudstream4.playback.msToFraction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent

/**
 * History-save throttle decision (pure): a save is due only when at least
 * [intervalMs] elapsed since the last one, so playback doesn't rewrite the
 * whole history file on every position tick (~4x/sec).
 */
fun shouldSaveHistory(lastSaveMs: Long, nowMs: Long, intervalMs: Long = 5_000L): Boolean =
    nowMs - lastSaveMs >= intervalMs

@Composable
fun PlayerScreen(
    url: String,
    title: String,
    episodeTitle: String? = null,
    mediaUrl: String? = null,
    apiName: String? = null,
    episodeIndex: Int? = null,
    seasonIndex: Int? = null,
    startPositionMs: Long = 0L,
    posterUrl: String? = null,
    sources: List<PlaybackSource> = emptyList(),
    mediaHeaders: Map<String, String> = emptyMap(),
    tvTypeName: String? = null,
    year: Int? = null,
    malId: Int? = null,
    tmdbId: String? = null,
    imdbId: String? = null,
    absoluteEpisodeIndex: Int? = null,
    hasNextEpisode: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onForceExitFullscreen: () -> Unit = {},
    onPlayerBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var showLoadingIndicator by remember { mutableStateOf(false) }
    val revealControls = remember {
        {
            controlsVisible = true
            lastInteractionTime = System.currentTimeMillis()
        }
    }

    // VLC can emit very short buffering/cache events while switching tracks or
    // settling a seek. Keep the loading affordance truthful without flashing:
    // show it after a short sustained wait, hide it promptly when output resumes.
    LaunchedEffect(DesktopPlayer.isBuffering, DesktopPlayer.isSeeking) {
        if (DesktopPlayer.isBuffering || DesktopPlayer.isSeeking) {
            delay(220)
            showLoadingIndicator = DesktopPlayer.isBuffering || DesktopPlayer.isSeeking
        } else {
            delay(120)
            showLoadingIndicator = false
        }
    }

    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showResizeMenu by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    val playerListScope = rememberCoroutineScope()
    // Artwork-derived accent for player UI only (seek bar, active controls,
    // selection highlights). Cached by artwork URL; falls back to CloudStream
    // red. Resolved off the UI thread; never tints the video image.
    var playerAccent by remember { mutableStateOf(com.lagradost.cloudstream4.playback.PlayerArtworkAccent.fallback()) }
    LaunchedEffect(posterUrl, title) {
        val artworkLoader = com.lagradost.cloudstream4.playback.PlayerArtworkAccent.ArtworkBytesLoader { url ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = java.net.URL(url).openConnection().apply {
                        connectTimeout = 5_000
                        readTimeout = 5_000
                        useCaches = true
                    }
                    if (connection.contentLengthLong > 8L * 1024 * 1024) return@runCatching null
                    connection.getInputStream().buffered().use { it.readNBytes(8 * 1024 * 1024 + 1) }
                        .takeIf { it.size <= 8 * 1024 * 1024 }
                }.getOrNull()
            }
        }
        val accent = withContext(Dispatchers.IO) {
            com.lagradost.cloudstream4.playback.PlayerArtworkAccent.accentFor(posterUrl, artworkLoader)
        }
        playerAccent = accent
    }
    val playerAccentColor = com.lagradost.cloudstream4.playback.PlayerArtworkAccent.toColor(playerAccent)
    var skipUsedKinds by remember { mutableStateOf(setOf<String>()) }
    var skipRange by remember(url, apiName, episodeIndex, seasonIndex) {
        mutableStateOf(DesktopStorage.getSkipSegment(apiName ?: "", url, episodeIndex, seasonIndex))
    }

    // Start playback before reading duration-dependent skip metadata. playAsync
    // clears the old stream's duration immediately so the new episode cannot
    // use a stale length (and cache a false AniSkip miss) while VLC is loading.
    LaunchedEffect(url, mediaUrl) {
        val streamUrl = mediaUrl ?: url
        DesktopPlayer.playAsync(
            url = streamUrl,
            title = title,
            episodeTitle = episodeTitle,
            startMs = startPositionMs,
            headers = mediaHeaders
        )
    }

    // Automatic skip markers: manual/persisted result first, then provider
    // lookup off the UI thread. Retry when this stream's duration becomes
    // available (AniSkip needs episode length). Never reuse a previous stream's
    // duration. Missing metadata or service data simply hides the button.
    val skipDurationKnown = DesktopPlayer.totalDurationMs > 0L &&
        DesktopPlayer.currentTitle == title && DesktopPlayer.currentEpisodeTitle == episodeTitle
    LaunchedEffect(url, apiName, episodeIndex, seasonIndex) {
        skipUsedKinds = emptySet()
    }
    LaunchedEffect(url, apiName, episodeIndex, seasonIndex, skipDurationKnown) {
        val fresh = withContext(Dispatchers.IO) {
            DesktopStorage.resolveEpisodeMarkers(
                apiName = apiName ?: "",
                seriesUrl = url,
                title = title,
                tvTypeName = tvTypeName,
                year = year,
                malId = malId,
                tmdbId = tmdbId,
                imdbId = imdbId,
                seasonIndex = seasonIndex,
                episodeIndex = episodeIndex,
                durationMs = DesktopPlayer.totalDurationMs,
                absoluteEpisodeIndex = absoluteEpisodeIndex
            )
        }
        if (fresh != null) skipRange = fresh
    }

    LaunchedEffect(isFullscreen) {
        delay(200)
        DesktopPlayer.refreshVideoSurface()
    }

    val videoComponent = DesktopPlayer.mediaPlayerComponent
    val videoSurface = DesktopPlayer.videoSurfaceComponent()
    DisposableEffect(videoSurface, revealControls) {
        var lastMeaningfulPoint: java.awt.Point? = null
        fun onMeaningfulMove(event: MouseEvent) {
            val point = event.point
            val previous = lastMeaningfulPoint
            // Compare to the last accepted point (not the prior raw event) so
            // sub-pixel/one-pixel cursor vibration cannot keep controls alive,
            // while gradual deliberate movement still crosses the threshold.
            if (previous == null || previous.distanceSq(point) >= 25.0) {
                lastMeaningfulPoint = point
                revealControls()
            }
        }
        val activityListener = object : MouseAdapter() {
            override fun mouseEntered(event: MouseEvent) = revealControls()
            override fun mouseMoved(event: MouseEvent) = onMeaningfulMove(event)
            override fun mouseDragged(event: MouseEvent) = revealControls()
            override fun mousePressed(event: MouseEvent) = revealControls()
        }
        videoSurface?.addMouseListener(activityListener)
        videoSurface?.addMouseMotionListener(activityListener)
        onDispose {
            videoSurface?.removeMouseListener(activityListener)
            videoSurface?.removeMouseMotionListener(activityListener)
        }
    }

    // OTT-style behavior: hide shortly after active playback goes idle; keep
    // controls available while paused, loading, or choosing a track/source.
    LaunchedEffect(lastInteractionTime, controlsVisible, DesktopPlayer.isPlaying,
        DesktopPlayer.isBuffering, showAudioDialog, showSubtitleDialog, showSpeedMenu,
        showResizeMenu, showSourceDialog
    ) {
        val menuOpen = showAudioDialog || showSubtitleDialog || showSpeedMenu || showResizeMenu || showSourceDialog
        if (controlsVisible && DesktopPlayer.isPlaying && !DesktopPlayer.isBuffering && !menuOpen) {
            delay(1800)
            controlsVisible = false
        }
    }

    // Continuously save history progress — throttled to one write every 5s so
    // playback doesn't rewrite the whole file on every position tick (~4x
    // sec). The final position is written on exit via the DisposableEffect.
    var lastHistorySaveMs by remember { mutableStateOf(0L) }
    LaunchedEffect(DesktopPlayer.currentTimeMs) {
        val now = System.currentTimeMillis()
        if (DesktopPlayer.currentTimeMs > 5000L && DesktopPlayer.totalDurationMs > 0L &&
            shouldSaveHistory(lastHistorySaveMs, now)
        ) {
            lastHistorySaveMs = now
            DesktopStorage.saveHistory(
                HistoryItem(
                    url = url,
                    apiName = apiName ?: "External",
                    title = title,
                    posterUrl = posterUrl,
                    type = tvTypeName,
                    episodeTitle = episodeTitle,
                    episodeIndex = episodeIndex,
                    seasonIndex = seasonIndex,
                    positionMs = DesktopPlayer.currentTimeMs,
                    durationMs = DesktopPlayer.totalDurationMs
                )
            )
        }
    }

    // Stop playback on exit and persist the final resume position.
    DisposableEffect(Unit) {
        onDispose {
            val finalPositionMs = DesktopPlayer.currentTimeMs
            val finalDurationMs = DesktopPlayer.totalDurationMs
            DesktopPlayer.stop()
            if (finalPositionMs > 5000L && finalDurationMs > 0L) {
                DesktopStorage.saveHistory(
                    HistoryItem(
                        url = url,
                        apiName = apiName ?: "External",
                        title = title,
                        posterUrl = posterUrl,
                        type = tvTypeName,
                        episodeTitle = episodeTitle,
                        episodeIndex = episodeIndex,
                        seasonIndex = seasonIndex,
                        positionMs = finalPositionMs,
                        durationMs = finalDurationMs
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    var lastMeaningfulPosition: Offset? = null
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press) {
                            revealControls()
                        } else if (event.type == PointerEventType.Move) {
                            val point = event.changes.firstOrNull()?.position
                            val previous = lastMeaningfulPosition
                            if (point != null && (previous == null ||
                                    (point - previous).getDistanceSquared() >= 25f)
                            ) {
                                lastMeaningfulPosition = point
                                revealControls()
                            }
                        }
                    }
                }
            }
    ) {
        // Video Surface
        val component = DesktopPlayer.mediaPlayerComponent
        if (component != null) {
            SwingPanel(
                factory = { component },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Simulated video view if VLC is in fallback
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎬", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(color = Color.White, fontWeight = FontWeight.Bold)
                    )
                    if (!episodeTitle.isNullOrBlank()) {
                        Text(
                            text = episodeTitle,
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (!DesktopPlayer.isVlcAvailable) "(Playing in fallback video mode - LibVLC not detected)" else "Buffering stream...",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Buffering Indicator (also covers seeking catch-up after coalesced seeks)
        if (showLoadingIndicator) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x990A0A0D))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    CircularProgressIndicator(color = playerAccentColor, modifier = Modifier.size(44.dp))
                    Text(
                        text = if (DesktopPlayer.isSeeking) "Seeking…" else "Buffering…",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        // Audio-only / black-screen recovery banner. VLC advances the clock even
        // when no video frames arrive (unsupported 4K/DV/AVC path), so surface this.
        val playerError = DesktopPlayer.lastError
        val audioOnly = !DesktopPlayer.hasVideo && DesktopPlayer.currentTimeMs > 3000L && DesktopPlayer.isPlaying
        if (playerError != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color(0xCC1A1A22),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    audioOnly && DesktopPlayer.usingHardwareDecoding -> "Audio playing — no video frames (HW decode)"
                                    audioOnly -> "Audio playing — no video frames (SW decode)"
                                    else -> "Playback needs attention"
                                },
                                color = Color(0xFFF59E0B),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = playerError,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .clickable { DesktopPlayer.retryWithOtherRenderer() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = if (DesktopPlayer.usingHardwareDecoding) "Retry software decode" else "Retry hardware decode",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Gradient & Header Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .clickable { onPlayerBack() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("←", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(
                            modifier = Modifier.weight(1f).padding(end = 16.dp)
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                ),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!episodeTitle.isNullOrBlank()) {
                                Text(
                                    text = episodeTitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.8f)
                                    ),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Native window chrome (including its close button) is
                        // intentionally hidden by true fullscreen. Keep a clear
                        // on-screen escape route alongside the keyboard shortcut.
                        if (isFullscreen) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .clickable { onForceExitFullscreen() }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "↙ Exit fullscreen",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // Center transport cluster in a frosted pill for clear hierarchy
                Box(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(36.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                    ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        // Rewind 10s
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { DesktopPlayer.seekRelative(-DesktopStorage.settings.defaultSeekSeconds.toLong()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ShiopaIcon(icon = ShiopaIcon.VideoRewind, size = 23.dp, tint = Color.White, strokeWidth = 1.8.dp)
                                Text("${DesktopStorage.settings.defaultSeekSeconds}s", color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Big Play / Pause
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .shadow(12.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(playerAccentColor, playerAccentColor.copy(alpha = 0.72f))))
                                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                .clickable { DesktopPlayer.togglePlay() },
                            contentAlignment = Alignment.Center
                        ) {
                            ShiopaIcon(
                                icon = if (DesktopPlayer.isPlaying) ShiopaIcon.Pause else ShiopaIcon.Play,
                                size = 27.dp,
                                tint = Color.White,
                                strokeWidth = 0.dp
                            )
                        }

                        // Forward 10s
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .clickable { DesktopPlayer.seekRelative(DesktopStorage.settings.defaultSeekSeconds.toLong()) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ShiopaIcon(icon = ShiopaIcon.VideoForward, size = 23.dp, tint = Color.White, strokeWidth = 1.8.dp)
                                Text("${DesktopStorage.settings.defaultSeekSeconds}s", color = Color.White.copy(alpha = 0.78f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    }
                }

                // Bottom Gradient & Controls Bar (roomier in fullscreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isFullscreen) 150.dp else 130.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                            )
                        )
                        .padding(horizontal = if (isFullscreen) 48.dp else 24.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
                        // Seek Bar: click jumps, drag scrubs with a live preview,
                        // backend commit happens once on release. While scrubbing
                        // the thumb shows the preview position; otherwise it
                        // follows live playback, so bar and video stay in sync.
                        // Unknown duration (live): preview in %, commit via the
                        // fractional API — no division by zero anywhere.
                        val scrubDuration = DesktopPlayer.totalDurationMs
                        val liveProgress = if (scrubDuration > 0) {
                            msToFraction(DesktopPlayer.currentTimeMs, scrubDuration)
                        } else DesktopPlayer.position
                        var scrubFraction by remember { mutableStateOf<Float?>(null) }
                        val scrubbing = scrubFraction != null
                        val sliderValue = scrubFraction ?: liveProgress
                        val seekHoverSource = remember { MutableInteractionSource() }
                        val seekHovered by seekHoverSource.collectIsHoveredAsState()
                        val seekActive = scrubbing || seekHovered

                        if (seekActive) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = if (scrubDuration > 0) {
                                            if (scrubbing) {
                                                "${formatMs(fractionToMs(sliderValue, scrubDuration))} → ${formatMs(scrubDuration)}"
                                            } else {
                                                formatMs(DesktopPlayer.currentTimeMs)
                                            }
                                        } else {
                                            "${(sliderValue * 100).toInt()}%"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Slider(
                            value = sliderValue,
                            onValueChange = { scrubFraction = it },
                            onValueChangeFinished = {
                                val target = scrubFraction
                                scrubFraction = null
                                if (target != null) {
                                    if (scrubDuration > 0) {
                                        DesktopPlayer.seekToTime(fractionToMs(target, scrubDuration))
                                    } else {
                                        DesktopPlayer.seekTo(target)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(24.dp)
                                .hoverable(interactionSource = seekHoverSource),
                            colors = SliderDefaults.colors(
                                thumbColor = playerAccentColor,
                                activeTrackColor = playerAccentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )

                        // Bottom Control Row
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Play/Pause, Volume, Time
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (DesktopPlayer.isPlaying) "❚❚" else "▶",
                                    fontSize = 18.sp,
                                    color = Color.White,
                                    modifier = Modifier
                                        .clickable { DesktopPlayer.togglePlay() }
                                        .padding(horizontal = 8.dp)
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                // Volume icon + slider
                                Text(
                                    text = if (DesktopPlayer.isMuted || DesktopPlayer.volume == 0) "🔇" else "🔊",
                                    fontSize = 16.sp,
                                    color = Color.White,
                                    modifier = Modifier.clickable { DesktopPlayer.togglePlayerMute() }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Slider(
                                    value = DesktopPlayer.volume.toFloat(),
                                    onValueChange = { DesktopPlayer.setPlayerVolume(it.toInt()) },
                                    valueRange = 0f..100f,
                                    modifier = Modifier.width(90.dp).height(20.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = playerAccentColor,
                                        activeTrackColor = playerAccentColor,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                // Current Time / Total Duration
                                val curStr = formatMs(DesktopPlayer.currentTimeMs)
                                val durStr = formatMs(DesktopPlayer.totalDurationMs)
                                Text(
                                    text = "$curStr / $durStr",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Right: labeled mini actions with a clear visual hierarchy.
                            // Each action keeps its existing behavior and dialog; only
                            // the presentation is unified (value on top, label below).
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                // Speed Selector
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        Text(
                                            text = "${DesktopPlayer.playbackSpeed}x",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { showSpeedMenu = true }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )

                                        DropdownMenu(
                                            expanded = showSpeedMenu,
                                            onDismissRequest = { showSpeedMenu = false }
                                        ) {
                                            for (spd in listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) {
                                                DropdownMenuItem(
                                                    text = { Text("${spd}x") },
                                                    onClick = {
                                                        DesktopPlayer.setSpeed(spd)
                                                        showSpeedMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text("Speed", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                }

                                // Resize Mode Selector (Fit / Fill / Original)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box {
                                        Text(
                                            text = "⛶ ${DesktopPlayer.resizeMode.label}",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clickable { showResizeMenu = true }
                                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                        )

                                        DropdownMenu(
                                            expanded = showResizeMenu,
                                            onDismissRequest = { showResizeMenu = false }
                                        ) {
                                            for (mode in DesktopPlayer.VideoResizeMode.entries) {
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            "${mode.label}${if (mode == DesktopPlayer.resizeMode) "  ✓" else ""}",
                                                            fontWeight = if (mode == DesktopPlayer.resizeMode) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    onClick = {
                                                        DesktopPlayer.changeResizeMode(mode)
                                                        showResizeMenu = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    Text("Size", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                }

                                // Audio Track
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ShiopaIcon(
                                        icon = ShiopaIcon.Volume,
                                        size = 20.dp,
                                        tint = Color.White,
                                        modifier = Modifier.clickable { showAudioDialog = true }
                                    )
                                    Text(
                                        text = if (!DesktopPlayer.tracksReady) "Audio …" else "Audio",
                                        color = Color.White.copy(alpha = 0.55f),
                                        fontSize = 10.sp
                                    )
                                }

                                // Subtitles
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ShiopaIcon(
                                        icon = ShiopaIcon.Subtitles,
                                        size = 20.dp,
                                        tint = Color.White,
                                        modifier = Modifier.clickable { showSubtitleDialog = true }
                                    )
                                    Text("Subtitles", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                }

                                if (sources.isNotEmpty()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        ShiopaIcon(
                                            icon = ShiopaIcon.Playlist,
                                            size = 20.dp,
                                            tint = Color.White,
                                            modifier = Modifier.clickable { showSourceDialog = true }
                                        )
                                        Text("Source", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                    }
                                }

                                // Fullscreen Toggle
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ShiopaIcon(
                                        icon = ShiopaIcon.Fullscreen,
                                        size = 21.dp,
                                        tint = Color.White,
                                        modifier = Modifier.clickable { onToggleFullscreen() }
                                    )
                                    Text("Full", color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Skip actions are independent of the auto-hiding transport controls.
        // OTT skip prompts must appear as soon as the current playhead enters a
        // known range, even if the mouse has been idle and the control bar has
        // faded out. They still disappear at the segment end, after use, or
        // when seeking outside the range.
        val skipButton = selectSkipButton(
            skipRange, DesktopPlayer.currentTimeMs,
            DesktopPlayer.totalDurationMs, skipUsedKinds
        )
        AnimatedVisibility(
            visible = skipButton != null && skipRange != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val activeButton = skipButton ?: return@AnimatedVisibility
            val activeRange = skipRange ?: return@AnimatedVisibility
            val segmentEndMs = when (activeButton) {
                SkipButtonKind.INTRO -> activeRange.introEndMs
                SkipButtonKind.RECAP -> activeRange.recapEndMs
                SkipButtonKind.ENDING -> activeRange.endingEndMs
            }
            // Ending with a known next episode and autoplay enabled lands near
            // the credits tail so the normal finished flow picks up promptly.
            val durationMs = DesktopPlayer.totalDurationMs
            val targetMs = if (activeButton == SkipButtonKind.ENDING &&
                hasNextEpisode && DesktopStorage.settings.autoPlayNextEpisode && durationMs > 0
            ) {
                maxOf(segmentEndMs, durationMs - 2000L)
            } else segmentEndMs
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 108.dp, end = 20.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.End
            ) {
                SkipJumpButton(
                    when (activeButton) {
                        SkipButtonKind.INTRO -> "Skip Intro ▶"
                        SkipButtonKind.RECAP -> "Skip Recap ▶"
                        SkipButtonKind.ENDING -> "Skip Ending ▶"
                    }
                ) {
                    DesktopPlayer.seekToTime(targetMs)
                    if (!DesktopPlayer.isPlaying) DesktopPlayer.togglePlay()
                    skipUsedKinds = skipUsedKinds + activeButton.key
                }
            }
        }
    }

    // Audio Track Selection Dialog
    if (showAudioDialog) {
        AlertDialog(
            onDismissRequest = { showAudioDialog = false },
            title = { Text("Audio Track") },
            text = {
                Column {
                    if (DesktopPlayer.audioTracks.isEmpty()) {
                        Text(if (DesktopPlayer.tracksReady) "This video has no selectable audio tracks." else "Reading audio tracks…")
                    } else {
                        for (track in DesktopPlayer.audioTracks) {
                            val isSelected = track.id == DesktopPlayer.selectedAudioTrackId
                            val channelSuffix = track.channels?.let { " · ${it}ch" } ?: ""
                            Text(
                                text = "${track.name}$channelSuffix ${if (isSelected) "✓" else ""}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        DesktopPlayer.selectAudioTrack(track.id)
                                        showAudioDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column {
                    if (DesktopPlayer.diagnostics.isNotBlank()) {
                        Text(
                            text = DesktopPlayer.diagnostics,
                            fontSize = 10.5.sp,
                            color = ShiopaStyle.TextMuted,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }
                    TextButton(onClick = { showAudioDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Subtitle Track Selection Dialog
    if (showSubtitleDialog) {
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("Subtitles & Timing") },
            text = {
                Column {
                    Text("Subtitle Delay: ${DesktopPlayer.subtitleDelayMs} ms", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { DesktopPlayer.setSubtitleDelay(-500L) }) { Text("-500ms") }
                        Button(onClick = { DesktopPlayer.setSubtitleDelay(500L) }) { Text("+500ms") }
                        TextButton(onClick = { DesktopPlayer.setSubtitleDelay(-DesktopPlayer.subtitleDelayMs) }) { Text("Reset") }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Available Tracks:", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Turn subtitles off${if (DesktopPlayer.selectedSubtitleTrackId == -1) "  ✓" else ""}",
                        color = if (DesktopPlayer.selectedSubtitleTrackId == -1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable {
                            DesktopPlayer.selectSubtitleTrack(-1)
                            showSubtitleDialog = false
                        }.padding(vertical = 8.dp)
                    )

                    if (DesktopPlayer.subtitleTracks.isEmpty()) {
                        Text(if (DesktopPlayer.tracksReady) "No embedded subtitle tracks detected." else "Reading subtitle tracks…")
                    } else {
                        for (track in DesktopPlayer.subtitleTracks) {
                            val isSelected = track.id == DesktopPlayer.selectedSubtitleTrackId
                            Text(
                                text = "${track.name} ${if (isSelected) "✓" else ""}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        DesktopPlayer.selectSubtitleTrack(track.id)
                                        showSubtitleDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = {
                        showSubtitleDialog = false
                        SwingUtilities.invokeLater {
                            val chooser = JFileChooser().apply { dialogTitle = "Select subtitle file" }
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                DesktopPlayer.addSubtitleFile(chooser.selectedFile)
                            }
                        }
                    }) { Text("Load subtitle file…") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showSourceDialog) {
        val sortedPlaybackSources = sortPlaybackSources(sources, { it.name }, { it.quality })
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Playback source") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "${sortedPlaybackSources.size} streams available:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = "Recommended option is listed first. SDR 1080p is preferred when available; 4K/HDR remains selectable.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Scroll state is keyed by playback so switching streams
                    // restarts at the top; the set is fixed while picking.
                    val playbackListFocus = remember { FocusRequester() }
                    val playbackSourceListState = remember(title, mediaUrl ?: url) { LazyListState() }
                    LaunchedEffect(showSourceDialog) {
                        if (showSourceDialog) {
                            playbackSourceListState.scrollToItem(0)
                            runCatching { playbackListFocus.requestFocus() }
                        }
                    }
                    // Picker shares one measured viewport capped to the dialog
                    // space: no dead area, no desync in any window mode.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val playbackViewportMax = (maxHeight * 0.62f).coerceIn(220.dp, 480.dp)
                        val playbackLayout = playbackSourceListState.layoutInfo
                        val playbackTotal = playbackLayout.totalItemsCount
                        val playbackFirst = playbackLayout.visibleItemsInfo.firstOrNull()?.index ?: 0
                        val playbackLast = playbackLayout.visibleItemsInfo.lastOrNull()?.index ?: -1
                        if (playbackFirst > 0) {
                            Text(
                                text = "↑ Scroll up — $playbackFirst above",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = playerAccentColor,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                            // Overlay pattern (same as Available Streams): the list sets
                            // the height; the scrollbar overlays at the measured
                            // height and shows only when content scrolls.
                            var playbackListHeightPx by remember { mutableIntStateOf(0) }
                            val playbackScrollable = playbackSourceListState.canScrollForward || playbackSourceListState.canScrollBackward
                            Box(modifier = Modifier.fillMaxWidth()) {
                        LazyColumn(
                            state = playbackSourceListState,
                            modifier = Modifier.fillMaxWidth().heightIn(max = playbackViewportMax)
                                .onSizeChanged { playbackListHeightPx = it.height }
                                .focusRequester(playbackListFocus).focusable()
                                .onPreviewKeyEvent { handleSourceListKey(it, playbackSourceListState, playerListScope) },
                            contentPadding = PaddingValues(end = 16.dp, top = 2.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(sortedPlaybackSources, key = { it.url }) { source ->
                                Text(
                                    text = "${source.name} · ${source.quality}p · ${source.type}${if (source.url == sortedPlaybackSources.firstOrNull()?.url) " · Recommended" else ""}${if (source.url == DesktopPlayer.currentMediaUrl) "  ✓ Playing" else ""}",
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        showSourceDialog = false
                                        DesktopPlayer.playAsync(
                                            source.url, title, episodeTitle, DesktopPlayer.currentTimeMs,
                                            headers = source.headers
                                        )
                                    }.padding(vertical = 10.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                            if (playbackScrollable && playbackListHeightPx > 0) {
                                VerticalScrollbar(
                                    adapter = rememberScrollbarAdapter(playbackSourceListState),
                                    style = ScrollbarStyle(
                                        minimalHeight = 48.dp,
                                        thickness = 10.dp,
                                        shape = RoundedCornerShape(5.dp),
                                        hoverDurationMillis = 300,
                                        unhoverColor = playerAccentColor.copy(alpha = 0.55f),
                                        hoverColor = playerAccentColor
                                    ),
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                        .height(with(LocalDensity.current) { playbackListHeightPx.toDp() })
                                )
                            }
                            }
                        if (playbackLast < playbackTotal - 1) {
                            val playbackRemaining = playbackTotal - 1 - playbackLast
                            Text(
                                text = "↓ Scroll for more — $playbackRemaining of $playbackTotal remaining",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = playerAccentColor,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSourceDialog = false }) { Text("Close") } }
        )
    }

    // Manual skip-marking dialog removed: automatic detection works without any
    // setup, and previously stored marks are still honored by the lookup.
    // (Manual capture rows removed with the setup dialog; kept functions below
    // are the jump button used by automatic detection.)
}

@Composable
private fun SkipJumpButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
