package com.lagradost.cloudstream4.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream4.core.DesktopStorage
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.media.MediaRef
import uk.co.caprica.vlcj.media.TrackType
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent
import uk.co.caprica.vlcj.player.component.callback.FilledCallbackImagePainter
import uk.co.caprica.vlcj.player.component.callback.FixedCallbackImagePainter
import uk.co.caprica.vlcj.player.component.callback.ScaledCallbackImagePainter
import java.awt.Graphics2D
import java.io.File
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.awt.Component
import javax.swing.JComponent
import javax.swing.SwingUtilities

data class PlayerTrack(
    val id: Int,
    val name: String,
    /** Audio channel count when known (e.g. 2 = stereo, 6 = 5.1). Null for subtitles/unknown. */
    val channels: Int? = null
)

/**
 * Pure seek-target math (unit-tested). Unknown/zero duration: clamp at zero
 * only; known duration: clamp into [0, duration].
 */
fun computeSeekTargetMs(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = currentMs + deltaMs
    if (durationMs > 0) return target.coerceIn(0L, durationMs)
    return target.coerceAtLeast(0L)
}

/**
 * Scrub-bar mapping (unit-tested). Fraction is always clamped to [0, 1].
 * Zero/unknown duration maps to 0 ms / 0 fraction — callers must use the
 * fractional seek API for live/unknown-duration streams instead.
 */
fun fractionToMs(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    return (fraction.coerceIn(0f, 1f) * durationMs).toLong()
}

fun msToFraction(timeMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (timeMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

/** VLC fourCC int is little-endian ASCII (d|v<<8|h<<16|e<<24 for "dvhe"). */
fun fourccToString(fourcc: Int): String {
    if (fourcc == 0) return ""
    val chars = CharArray(4) { i -> ((fourcc shr (i * 8)) and 0xFF).toChar() }
    if (chars.any { it.code !in 32..126 }) return ""
    return String(chars)
}

/** HEVC Dolby Vision codec tags. Plain "hev1"/"hvc1" (incl. HDR10) is NOT in this set. */
private val DOLBY_VISION_FOURCCS = setOf("dvhe", "dvh1", "dva1", "dvav", "dvc1")

/**
 * Pure, unit-tested diagnosis for audio-with-black-picture starvation.
 * Reports codec tags only — never URLs, headers, or tokens.
 */
fun describeVideoStarvation(codecFourcc: String?, softwareFallbackAttempted: Boolean): String {
    val codec = codecFourcc?.lowercase()?.takeIf { it.isNotBlank() }
    val isDolbyVision = codec != null && codec in DOLBY_VISION_FOURCCS
    if (isDolbyVision) {
        return if (softwareFallbackAttempted) {
            "Dolby Vision ($codec) shows no picture on the bundled VLC 3.0.23 decoder " +
                "even with software decoding — it cannot process Dolby Vision enhancement " +
                "data. Audio continues; video is unavailable for this stream. Try another source."
        } else {
            "Dolby Vision ($codec) detected — this VLC build cannot render its enhancement " +
                "data with hardware decoding. Retrying this stream with software decoding…"
        }
    }
    val codecPart = if (codec != null) " (video codec $codec)" else ""
    return if (softwareFallbackAttempted) {
        "No video frames arrived$codecPart after trying software decoding. " +
            "This build cannot present this stream's video — audio continues. " +
            "Try another source; working SDR playback is unaffected."
    } else {
        "No video frames arrived$codecPart with hardware decoding. " +
            "Retrying this stream with software decoding…"
    }
}

/** Select the next decoder path after a stream advances without rendering frames. */
data class DecoderRetryPlan(val hardware: Boolean, val hardwareBackend: String?)

fun nextDecoderRetryPlan(
    hardware: Boolean,
    hardwareBackend: String?,
    isWindows: Boolean
): DecoderRetryPlan? = when {
    !hardware -> null
    isWindows && hardwareBackend != "any" -> DecoderRetryPlan(hardware = true, hardwareBackend = "any")
    else -> DecoderRetryPlan(hardware = false, hardwareBackend = null)
}

object DesktopPlayer {
    private var factory: MediaPlayerFactory? = null
    private val initializationLock = Any()
    /** Serialize player lifecycle work away from Compose/AWT event threads. */
    private val lifecycleExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "CloudStream-player-lifecycle").apply { isDaemon = true }
    }
    /** Delay LibVLC error handling long enough to distinguish transient demux/decoder errors from a failed stream. */
    private val playbackErrorScheduler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "CloudStream-player-error-watchdog").apply { isDaemon = true }
    }
    private val playRequestGeneration = AtomicLong(0L)
    private val errorScheduledForSession = AtomicLong(-1L)

    /**
     * Counts surface paints as a best-effort rendered-frame signal: vlcj's
     * callback surface repaints on every displayed frame (plus an initial
     * background/show paint). Painting behavior itself is untouched — the
     * default render callback still draws every frame.
     */
    private class FrameCountingCallbackComponent(factory: MediaPlayerFactory) :
        CallbackMediaPlayerComponent(factory, null, null, false, null) {
        @Volatile var paintCount = 0L
        override fun onPaintOverlay(g: Graphics2D) {
            paintCount++
            super.onPaintOverlay(g)
        }
    }

    private var callbackComponent: FrameCountingCallbackComponent? = null

    /** Rendered-frame paint count, or 0 when no component exists. */
    private fun framePaintCount(): Long = callbackComponent?.paintCount ?: 0L

    /** Callback Swing surface hosted inside Compose SwingPanel. */
    var mediaPlayerComponent by mutableStateOf<JComponent?>(null)
        private set

    /** True when VLC hardware decoding is requested for the active callback renderer. */
    var usingHardwareDecoding by mutableStateOf(false)
        private set

    /** Set when VLC reports a video track / video output, used to detect audio-only black screen. */
    var hasVideo by mutableStateOf(false)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    /** Advertised video codec tag (e.g. "dvhe", "hvc1", "avc1"). Codec only — never a URL. */
    var videoCodecFourcc by mutableStateOf<String?>(null)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    /**
     * Human-readable playback diagnostics snapshot (codec, profile, resolution,
     * decoder path, HW status, renderer, rendered-frame cadence). Refreshed on
     * track/video events — never per frame. Empty until the first video track.
     */
    var diagnostics by mutableStateOf("")
        private set

    private var lastDiagnosticsAtMs = 0L
    private var lastDiagnosticsPaints = 0L
    private var lastDiagnosticsPositionMs = 0L

    /** Pure diagnostics formatter (unit-tested). No URLs/headers/tokens. */
    fun formatDiagnostics(
        codecFourcc: String?,
        width: Int,
        height: Int,
        hardwareDecoding: Boolean,
        renderedFrames: Long,
        fpsEstimate: Float = 0f
    ): String {
        val codec = codecFourcc?.takeIf { it.isNotBlank() } ?: "unknown"
        val res = if (width > 0 && height > 0) "${width}x${height}" else "unknown"
        val decoder = if (hardwareDecoding) "hardware" else "software"
        val fps = if (fpsEstimate > 0f) " ~${"%.1f".format(fpsEstimate)}fps" else ""
        return "codec=$codec res=$res decode=$decoder renderer=callback frames=$renderedFrames$fps"
    }

    private fun refreshDiagnostics(fpsEstimate: Float = 0f) {
        diagnostics = formatDiagnostics(
            videoCodecFourcc, videoWidth, videoHeight, usingHardwareDecoding, framePaintCount(), fpsEstimate
        )
    }

    private var pendingStartMs: Long = 0L
    private var currentStreamHeaders: Map<String, String> = emptyMap()

    /**
     * Session-scoped starvation fallback bookkeeping. Each play() bumps
     * [playbackSessionId] so stale watchdog threads can never act on a newer
     * playback. Automatic recovery steps through one alternate Windows
     * hardware backend, then software decoding, so retries cannot loop.
     */
    @Volatile private var playbackSessionId = 0L
    /** Windows backend override; null is the default D3D11VA path (or software when HW is off). */
    @Volatile private var activeHardwareBackend: String? = null

    val isVlcAvailable: Boolean by lazy {
        try {
            NativeDiscovery().discover()
        } catch (e: Throwable) {
            false
        }
    }

    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var position by mutableFloatStateOf(0f)
    var currentTimeMs by mutableLongStateOf(0L)
    var totalDurationMs by mutableLongStateOf(0L)
    var volume by mutableIntStateOf(100)
    var isMuted by mutableStateOf(false)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var subtitleDelayMs by mutableLongStateOf(0L)

    /** Resize/fit modes every common player offers; applied via the callback image painter. */
    enum class VideoResizeMode(val label: String) {
        FIT("Fit"),
        FILL("Fill screen"),
        ORIGINAL("Original size")
    }

    var resizeMode by mutableStateOf(VideoResizeMode.FIT)
        private set

    fun changeResizeMode(mode: VideoResizeMode) {
        resizeMode = mode
        applyResizeMode()
    }

    private fun applyResizeMode() {
        val component = callbackComponent ?: return
        val painter = when (resizeMode) {
            VideoResizeMode.FIT -> ScaledCallbackImagePainter()
            VideoResizeMode.FILL -> FilledCallbackImagePainter()
            VideoResizeMode.ORIGINAL -> FixedCallbackImagePainter()
        }
        // Painter swap touches Swing state; keep it on the EDT.
        if (SwingUtilities.isEventDispatchThread()) {
            runCatching { component.setImagePainter(painter); refreshVideoSurface() }
        } else {
            SwingUtilities.invokeLater { runCatching { component.setImagePainter(painter); refreshVideoSurface() } }
        }
    }
    var currentMediaUrl by mutableStateOf<String?>(null)
    var currentTitle by mutableStateOf("")
    var currentEpisodeTitle by mutableStateOf<String?>(null)
    var tracksReady by mutableStateOf(false)

    val audioTracks = mutableStateListOf<PlayerTrack>()
    val subtitleTracks = mutableStateListOf<PlayerTrack>()
    var selectedAudioTrackId by mutableIntStateOf(-1)
    var selectedSubtitleTrackId by mutableIntStateOf(-1)

    private fun wantsHardware(): Boolean = try {
        DesktopStorage.settings.hardwareAcceleration
    } catch (_: Throwable) {
        true
    }

    private fun buildVlcArgs(hardware: Boolean, hardwareBackend: String? = null): Array<String> {
        val base = mutableListOf(
            "--no-snapshot-preview",
            "--quiet",
            "--no-sub-autodetect-file",
            // Streaming robustness: bigger buffers stop 4K/HLS stutter and AVC underruns.
            "--network-caching=2000",
            "--file-caching=1500",
            "--live-caching=1500",
            "--clock-jitter=0",
            // Keep presentation in real time under load: on 4K/high-bitrate
            // streams, discard frames that missed their deadline instead of
            // building an ever-growing render backlog that looks like freezes.
            "--drop-late-frames",
            "--skip-frames",
            // Let libavcodec use all cores for 4K AVC/HEVC software fallback.
            "--avcodec-threads=0",
            // Never skip frames/loopfilter for correctness; HW path handles the load.
            "--avcodec-skip-frame=0",
            "--avcodec-skip-idct=0",
            "--avcodec-skiploopfilter=0",
            "--http-reconnect"
        )
        if (hardware) {
            if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
                // Keep Compose-compatible callback output; forcing Direct3D11 vout
                // embeds a heavyweight Canvas that can stay black in SwingPanel.
                // Prefer D3D11VA, then retry VLC's automatic Windows backend
                // selection if this driver cannot render through the callback surface.
                base.add(if (hardwareBackend == "any") "--avcodec-hw=any" else "--avcodec-hw=d3d11va")
            } else {
                base.add("--avcodec-hw=any")
            }
            // NOTE: no --tone-mapping flag. Verified against the bundled VLC 3.0.23:
            // there is no tonemap video-filter plugin and no "tone-mapping" option in
            // libvlccore or the direct3d11 output, so such a flag would be silently
            // ignored. HDR presentation relies on the software-decode fallback path.
        } else {
            // Software decode remains available as a compatibility retry.
            base.add("--avcodec-hw=none")
        }
        return base.toTypedArray()
    }

    private fun buildMediaOptions(headers: Map<String, String>): Array<String> {
        // HW decode mode is fixed at factory level (--avcodec-hw); per-media
        // options carry only buffering/network tuning. Passing -- style codec
        // flags here would be rejected by VLC's media parser on some builds.
        val options = mutableListOf(
            ":network-caching=2000",
            ":file-caching=1500",
            ":live-caching=1500",
            ":clock-jitter=0",
            ":http-reconnect=true"
        )
        headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value?.takeIf(String::isNotBlank)?.let { options += ":http-user-agent=$it" }
        headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) || it.key.equals("Referrer", ignoreCase = true) }
            ?.value?.takeIf(String::isNotBlank)?.let { options += ":http-referrer=$it" }
        return options.toTypedArray()
    }

    /** vlcj treats Java's `file:/D:/...` URI form as a relative filename on Windows. */
    private fun normalizeMediaUrl(url: String): String =
        if (url.startsWith("file:", ignoreCase = true)) {
            runCatching { Paths.get(URI(url)).toAbsolutePath().toString() }.getOrDefault(url)
        } else {
            url
        }

    fun initialize(): JComponent? {
        return initializeInternal(wantsHardware())
    }

    fun initializeAsync(): CompletableFuture<JComponent?> =
        CompletableFuture.supplyAsync({ initialize() }, lifecycleExecutor)

    private fun initializeInternal(hardware: Boolean, hardwareBackend: String? = null): JComponent? {
        if (!isVlcAvailable) {
            println("VLC native libraries not found on this system. Playback will run in fallback mode.")
            return null
        }
        mediaPlayerComponent?.let {
            // Recreate when the decoder mode changes.
            if (usingHardwareDecoding == hardware && (!hardware || activeHardwareBackend == hardwareBackend)) return it
            releasePlayer()
        }

        val initializeOnEdt = {
            synchronized(initializationLock) {
                mediaPlayerComponent ?: try {
                    lastError = null
                    hasVideo = false
                    videoWidth = 0
                    videoHeight = 0
                    videoCodecFourcc = null
                    val args = buildVlcArgs(hardware, hardwareBackend)
                    var activeFactory = MediaPlayerFactory(*args)
                    val component: JComponent = try {
                        FrameCountingCallbackComponent(activeFactory)
                    } catch (e: Throwable) {
                        activeFactory.release()
                        if (!hardware) throw e
                        lastError = "Hardware decoder setup failed; using software decoding: ${e.message}"
                        activeFactory = MediaPlayerFactory(*buildVlcArgs(false))
                        FrameCountingCallbackComponent(activeFactory)
                    }
                    factory = activeFactory
                    callbackComponent = component as FrameCountingCallbackComponent
                    usingHardwareDecoding = hardware && lastError?.startsWith("Hardware decoder setup failed") != true
                    activeHardwareBackend = if (usingHardwareDecoding) hardwareBackend else null
                    mediaPlayerComponent = component
                    setupEventListeners()
                    applyResizeMode()
                    component
                } catch (e: Throwable) {
                    e.printStackTrace()
                    lastError = "Player init failed: ${e.message}"
                    null
                }
            }
        }

        if (SwingUtilities.isEventDispatchThread()) return initializeOnEdt()
        val result = AtomicReference<JComponent?>()
        SwingUtilities.invokeAndWait { result.set(initializeOnEdt()) }
        return result.get()
    }

    private fun activePlayer(): MediaPlayer? {
        callbackComponent?.let { return it.mediaPlayer() }
        return null
    }

    /** The actual child receiving pointer input in vlcj's callback surface. */
    fun videoSurfaceComponent(): Component? = callbackComponent?.videoSurfaceComponent()

    private fun setupEventListeners() {
        val player = activePlayer() ?: return
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    isPlaying = true
                    isBuffering = false
                    if (pendingStartMs > 0L) {
                        runCatching { mediaPlayer.controls().setTime(pendingStartMs) }
                        pendingStartMs = 0L
                    }
                    updateTracks()
                    refreshVideoSurface()
                }
            }

            override fun paused(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    isPlaying = false
                }
            }

            override fun stopped(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    isPlaying = false
                }
            }

            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) {
                SwingUtilities.invokeLater {
                    isBuffering = newCache < 100f
                }
            }

            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                SwingUtilities.invokeLater {
                    currentTimeMs = newTime
                    settleSeekIfLanded(newTime)
                }
            }

            override fun positionChanged(mediaPlayer: MediaPlayer, newPosition: Float) {
                SwingUtilities.invokeLater {
                    position = newPosition
                }
            }

            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                SwingUtilities.invokeLater {
                    totalDurationMs = newLength
                    updateTracks()
                }
            }

            override fun finished(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    isPlaying = false
                    position = 1.0f
                }
            }

            override fun videoOutput(mediaPlayer: MediaPlayer, newCount: Int) {
                SwingUtilities.invokeLater {
                    if (newCount > 0) {
                        hasVideo = true
                        lastError = null
                        detectVideoSize(mediaPlayer)
                    }
                }
            }

            override fun mediaChanged(mediaPlayer: MediaPlayer, media: MediaRef?) {
                SwingUtilities.invokeLater {
                    hasVideo = false
                    lastError = null
                }
            }

            override fun error(mediaPlayer: MediaPlayer) {
                if (mediaPlayer !== activePlayer()) return
                // LibVLC can emit an error while probing a stream or changing
                // tracks even when playback recovers. Do not immediately show
                // a scary banner or claim a retry is happening: wait briefly,
                // then check whether this session has actually rendered video.
                val errorSession = playbackSessionId
                if (errorScheduledForSession.getAndSet(errorSession) == errorSession) return
                val startPositionMs = currentTimeMs
                val startPaintCount = framePaintCount()
                playbackErrorScheduler.schedule({
                    if (errorSession != playbackSessionId) return@schedule
                    SwingUtilities.invokeLater {
                        if (errorSession != playbackSessionId) return@invokeLater
                        val renderedSinceError = framePaintCount() > startPaintCount
                        val playbackAdvanced = currentTimeMs > startPositionMs
                        if (renderedSinceError || (hasVideo && isPlaying && playbackAdvanced)) {
                            // A decoder/demux warning was transient; the active
                            // stream is producing output, so keep the UI clear.
                            if (!isBuffering) lastError = null
                            return@invokeLater
                        }

                        val retryPlan = nextDecoderRetryPlan(
                            hardware = usingHardwareDecoding,
                            hardwareBackend = activeHardwareBackend,
                            isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
                        )
                        if (retryPlan != null) {
                            val message = if (retryPlan.hardware) {
                                "Video output did not start. Retrying with VLC's alternate hardware decoder…"
                            } else {
                                describeVideoStarvation(videoCodecFourcc, softwareFallbackAttempted = false)
                            }
                            replayWithDecoder(retryPlan.hardware, retryPlan.hardwareBackend, message)
                        } else {
                            isPlaying = false
                            isBuffering = false
                            lastError = buildString {
                                append("VLC could not start video for this source after trying available decoder modes.")
                                videoCodecFourcc?.takeIf(String::isNotBlank)?.let { append(" Video codec: ").append(it).append('.') }
                                append(" Try another source or check your connection.")
                            }
                        }
                    }
                }, 3, TimeUnit.SECONDS)
            }
        })
    }

    private fun detectVideoSize(mediaPlayer: MediaPlayer) {
        try {
            val tracks = mediaPlayer.media().info()?.videoTracks()
            val vt = tracks?.firstOrNull()
            if (vt != null) {
                videoCodecFourcc = fourccToString(vt.codec()).takeIf { it.isNotEmpty() }
                if (vt.width() > 0) {
                    videoWidth = vt.width()
                    videoHeight = vt.height()
                    hasVideo = true
                    return
                }
            }
            // Fallback: any video track advertised means video exists even before first frame.
            val hasVideoTrack = mediaPlayer.media().info()?.tracks(TrackType.VIDEO)?.isNotEmpty() == true
            if (hasVideoTrack) hasVideo = true
        } catch (_: Throwable) {}
    }

    fun refreshTracks() = updateTracks()

    private fun updateTracks() {
        val player = activePlayer() ?: return
        audioTracks.clear()
        subtitleTracks.clear()

        // Channel counts help tell a real stereo track from a 5.1 track or a
        // broken leftover, which matters when a switched-to language loses
        // its dialogue. Best effort: missing info leaves channels null.
        val channelsById = try {
            player.media().info()?.audioTracks()
                ?.filter { it.channels() > 0 }
                ?.associate { it.id() to it.channels() }
                .orEmpty()
        } catch (_: Throwable) {
            emptyMap()
        }
        player.audio().trackDescriptions()?.forEach {
            audioTracks.add(PlayerTrack(it.id(), it.description(), channelsById[it.id()]))
        }
        selectedAudioTrackId = player.audio().track()

        player.subpictures().trackDescriptions()?.forEach {
            subtitleTracks.add(PlayerTrack(it.id(), it.description()))
        }
        selectedSubtitleTrackId = player.subpictures().track()
        tracksReady = audioTracks.isNotEmpty() || subtitleTracks.isNotEmpty()
        try {
            detectVideoSize(player)
            refreshDiagnostics()
        } catch (_: Throwable) {}
    }

    fun refreshVideoSurface() {
        SwingUtilities.invokeLater {
            mediaPlayerComponent?.let { component ->
                component.revalidate()
                component.doLayout()
                component.repaint()
            }
        }
    }

    fun play(
        url: String,
        title: String,
        episodeTitle: String? = null,
        startMs: Long = 0L,
        forceHardwareRenderer: Boolean? = null,
        headers: Map<String, String> = emptyMap(),
        forceHardwareBackend: String? = null
    ) {
        val vlcUrl = normalizeMediaUrl(url)
        currentMediaUrl = vlcUrl
        currentTitle = title
        currentEpisodeTitle = episodeTitle
        currentStreamHeaders = headers.toMap()
        currentTimeMs = 0L
        position = 0f
        isPlaying = false
        isBuffering = true
        tracksReady = false
        hasVideo = false
        videoCodecFourcc = null
        lastError = null
        clearSeeking()
        audioTracks.clear()
        subtitleTracks.clear()
        pendingStartMs = startMs
        playbackSessionId++
        val sessionId = playbackSessionId

        val hardware = forceHardwareRenderer ?: wantsHardware()
        if (mediaPlayerComponent == null || usingHardwareDecoding != hardware ||
            (hardware && activeHardwareBackend != forceHardwareBackend)
        ) {
            initializeInternal(hardware, forceHardwareBackend)
        }
        // Re-apply the user's persisted resize choice to reused pipelines.
        applyResizeMode()
        // Frame baseline for this session (after any pipeline rebuild, so a
        // recreated surface correctly starts from zero).
        val paintsAtStart = framePaintCount()

        val player = activePlayer()
        if (player != null) {
            val accepted = player.media().play(vlcUrl, *buildMediaOptions(currentStreamHeaders))
            if (!accepted) {
                lastError = "VLC could not open this source. It may have expired or require unavailable access headers. Try another source."
                isPlaying = false
                isBuffering = false
                return
            }
            player.audio().setVolume(volume)
            Thread {
                repeat(20) {
                    Thread.sleep(500)
                    SwingUtilities.invokeLater { updateTracks() }
                }
                // Session-scoped video-starvation fallback, now driven by RENDERED
                // FRAMES, not by track advertisement: HDR/DV streams advertise a
                // video track (dimensions/codec known) yet deliver zero frames to
                // the callback surface, so the old !hasVideo check never fired.
                // Self-calibrating signal: any shown surface paints at least once
                // (background/show paint), which proves the paint hook works. If
                // the hook never fires, fall back to the legacy track check so a
                // dormant hook can never fake starvation over working video.
                // Otherwise require a real frame cadence (>= 5 new paints while
                // the clock advances past 5s); slow starters get a 10s grace.
                SwingUtilities.invokeLater {
                    if (sessionId != playbackSessionId) return@invokeLater
                    val paintsNow = framePaintCount()
                    val newPaints = paintsNow - paintsAtStart
                    val starving = if (paintsNow > 0) {
                        currentTimeMs > 5000L && isPlaying && newPaints < 5
                    } else {
                        currentTimeMs > 5000L && isPlaying && !hasVideo
                    }
                    if (!starving) return@invokeLater
                    val retryPlan = nextDecoderRetryPlan(
                        hardware = usingHardwareDecoding,
                        hardwareBackend = activeHardwareBackend,
                        isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
                    )
                    if (retryPlan != null) {
                        val retryMessage = if (retryPlan.hardware) {
                            "Video frames are not rendering with D3D11VA; retrying with VLC's automatic hardware decoder…"
                        } else {
                            describeVideoStarvation(videoCodecFourcc, false)
                        }
                        replayWithDecoder(retryPlan.hardware, retryPlan.hardwareBackend, retryMessage)
                    } else if (lastError == null) {
                        lastError = describeVideoStarvation(videoCodecFourcc, softwareFallbackAttempted = true)
                    }
                }
            }.apply { isDaemon = true; name = "CloudStream-track-refresh"; start() }
        } else {
            // Simulated playback state for environments where VLC native libs are unavailable
            isPlaying = true
            isBuffering = false
            totalDurationMs = 3600_000L // 1 hour simulated
        }
    }

    /** Queue stream setup so native VLC and network work never blocks Compose. */
    fun playAsync(
        url: String,
        title: String,
        episodeTitle: String? = null,
        startMs: Long = 0L,
        forceHardwareRenderer: Boolean? = null,
        headers: Map<String, String> = emptyMap()
    ): CompletableFuture<Void> {
        val request = playRequestGeneration.incrementAndGet()
        val safeHeaders = headers.toMap()
        // Clear metadata synchronously with the new request. The player UI
        // observes duration to trigger duration-dependent skip-marker lookup;
        // retaining the previous stream's duration can cache a false miss for
        // the new episode before VLC reports its actual length.
        currentMediaUrl = normalizeMediaUrl(url)
        currentTitle = title
        currentEpisodeTitle = episodeTitle
        currentTimeMs = 0L
        position = 0f
        totalDurationMs = 0L
        isPlaying = false
        lastError = null
        isBuffering = true
        return CompletableFuture.runAsync({
            if (request == playRequestGeneration.get()) {
                play(url, title, episodeTitle, startMs, forceHardwareRenderer, safeHeaders)
            }
        }, lifecycleExecutor)
    }

    /** Session-scoped decoder replay: rebuilds only this playback's pipeline. */
    private fun replayWithDecoder(hardware: Boolean, hardwareBackend: String? = null, retryMessage: String? = null) {
        val url = currentMediaUrl ?: return
        val resumeMs = currentTimeMs
        val title = currentTitle
        val episode = currentEpisodeTitle
        val headers = currentStreamHeaders.toMap()
        val expectedSession = playbackSessionId
        val request = playRequestGeneration.incrementAndGet()
        lifecycleExecutor.execute {
            if (request != playRequestGeneration.get() || expectedSession != playbackSessionId) return@execute
            releasePlayer()
            play(
                url, title, episode, resumeMs,
                forceHardwareRenderer = hardware,
                headers = headers,
                forceHardwareBackend = hardwareBackend
            )
            // Keep VLC's definitive media-open error if play() rejected the
            // retried URL; only show the retry notice while a retry is active.
            if (retryMessage != null && (isBuffering || isPlaying)) lastError = retryMessage
        }
    }

    /** Black-screen recovery: tear down and replay with the opposite decoder mode. */
    fun retryWithOtherRenderer(): Boolean {
        val hardware = !usingHardwareDecoding
        if (currentMediaUrl == null) return false
        replayWithDecoder(hardware = hardware)
        return true
    }

    fun togglePlay() {
        val player = activePlayer()
        if (player != null) {
            if (player.status().isPlaying) {
                player.controls().pause()
            } else {
                player.controls().play()
            }
        } else {
            isPlaying = !isPlaying
        }
    }

    // --- Coalesced seeking --------------------------------------------------
    // Diagnosis: every press used to fire one setTime() on the UI thread and
    // storm the demuxer — each seek flushes VLC's buffers (≈2s refill from
    // network-caching, needed for stable 4K) and overlapping seeks reset each
    // other, so mashing ±10s took forever to resume video. Slider drags did
    // the same per tick. Now only the latest target within a 220ms window is
    // sent, from a daemon thread, with no player restart (a single setTime on
    // the existing pipeline keeps buffers already held). The UI shows an
    // honest seeking state until the clock lands near the target (or 8s pass),
    // and positions are never faked: currentTimeMs only moves on real
    // timeChanged events.
    // External limits this cannot fix (still reported as seeking/buffering):
    // HLS seeks snap to segment boundaries, MP4 seeks land on keyframes and
    // decode forward, and slow remotes rebuffer at their own pace.
    var isSeeking by mutableStateOf(false)
        private set
    @Volatile private var seekTargetMs = -1L
    private var seekDeadlineMs = 0L
    @Volatile private var pendingSeekMs = -1L
    private var pendingSeekFuture: ScheduledFuture<*>? = null
    private val seekScheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "CloudStream-seek").apply { isDaemon = true }
    }

    private fun requestSeek(targetMs: Long) {
        pendingSeekMs = targetMs
        seekTargetMs = targetMs
        seekDeadlineMs = System.currentTimeMillis() + 8000L
        SwingUtilities.invokeLater { isSeeking = true }
        pendingSeekFuture?.cancel(false)
        pendingSeekFuture = seekScheduler.schedule({
            activePlayer()?.controls()?.setTime(pendingSeekMs)
        }, 220, TimeUnit.MILLISECONDS)
    }

    private fun settleSeekIfLanded(newTime: Long) {
        if (!isSeeking) return
        val target = seekTargetMs
        if (target < 0L) {
            isSeeking = false
            return
        }
        if (kotlin.math.abs(newTime - target) < 2500L || System.currentTimeMillis() > seekDeadlineMs) {
            isSeeking = false
        }
    }

    private fun clearSeeking() {
        pendingSeekFuture?.cancel(false)
        pendingSeekMs = -1L
        seekTargetMs = -1L
        isSeeking = false
    }

    fun seekTo(newPosition: Float) {
        position = newPosition.coerceIn(0f, 1f)
        val player = activePlayer()
        if (player != null) {
            player.controls().setPosition(position)
        } else {
            currentTimeMs = (position * totalDurationMs).toLong()
        }
    }

    fun seekRelative(deltaSeconds: Long) {
        val targetMs = computeSeekTargetMs(currentTimeMs, deltaSeconds * 1000L, totalDurationMs)
        if (activePlayer() != null) {
            requestSeek(targetMs)
        } else {
            currentTimeMs = targetMs
            if (totalDurationMs > 0) position = currentTimeMs.toFloat() / totalDurationMs
        }
    }

    /** Absolute seek in ms with the same coalescing + feedback. Used by skip buttons. */
    fun seekToTime(targetMs: Long) {
        val clamped = computeSeekTargetMs(targetMs, 0L, totalDurationMs)
        if (activePlayer() != null) {
            requestSeek(clamped)
        } else {
            currentTimeMs = clamped
            if (totalDurationMs > 0) position = currentTimeMs.toFloat() / totalDurationMs
        }
    }

    fun setPlayerVolume(vol: Int) {
        volume = vol.coerceIn(0, 100)
        isMuted = volume == 0
        activePlayer()?.audio()?.let { audio ->
            audio.setVolume(volume)
            // Moving the slider while muted should unmute immediately; zero means mute.
            audio.setMute(isMuted)
        }
    }

    fun togglePlayerMute() {
        isMuted = !isMuted
        val player = activePlayer()
        if (player != null) {
            player.audio().setMute(isMuted)
        }
    }

    fun setSpeed(spd: Float) {
        playbackSpeed = spd
        activePlayer()?.controls()?.setRate(spd)
    }

    fun selectAudioTrack(trackId: Int) {
        selectedAudioTrackId = trackId
        activePlayer()?.audio()?.setTrack(trackId)
        refreshTracks()
    }

    fun selectSubtitleTrack(trackId: Int) {
        selectedSubtitleTrackId = trackId
        activePlayer()?.subpictures()?.setTrack(trackId)
        refreshTracks()
    }

    fun setSubtitleDelay(deltaMs: Long) {
        subtitleDelayMs += deltaMs
        activePlayer()?.subpictures()?.setDelay(subtitleDelayMs * 1000L) // microseconds
    }

    fun addSubtitleFile(file: File) {
        activePlayer()?.subpictures()?.setSubTitleFile(file.absolutePath)
        updateTracks()
    }

    fun stop() {
        clearSeeking()
        val player = activePlayer()
        playbackSessionId++
        val request = playRequestGeneration.incrementAndGet()
        pendingStartMs = 0L
        isPlaying = false
        isBuffering = false
        if (player != null) {
            lifecycleExecutor.execute {
                if (request == playRequestGeneration.get() && activePlayer() === player) {
                    runCatching { player.controls().stop() }
                }
            }
        }
    }

    private fun releasePlayer() {
        runCatching { callbackComponent?.release() }
        runCatching { factory?.release() }
        callbackComponent = null
        mediaPlayerComponent = null
        factory = null
    }

    fun release() {
        releasePlayer()
        runCatching { seekScheduler.shutdown() }
        runCatching { playbackErrorScheduler.shutdown() }
    }

    /** Queue native VLC teardown; callers should not block the window thread. */
    fun releaseAsync(): CompletableFuture<Void> {
        playRequestGeneration.incrementAndGet()
        playbackSessionId++
        return CompletableFuture.runAsync({ release() }, lifecycleExecutor)
    }

    /** Called when the Hardware Acceleration toggle changes so the next play uses the right path. */
    fun onSettingsChanged() {
        val hardware = wantsHardware()
        if (mediaPlayerComponent != null && usingHardwareDecoding != hardware) {
            val url = currentMediaUrl
            val resumeMs = currentTimeMs
            val wasPlaying = isPlaying
            val title = currentTitle
            val episode = currentEpisodeTitle
            val headers = currentStreamHeaders.toMap()
            val request = playRequestGeneration.incrementAndGet()
            lifecycleExecutor.execute {
                if (request != playRequestGeneration.get()) return@execute
                releasePlayer()
                initializeInternal(hardware)
                if (url != null && wasPlaying) {
                    play(url, title, episode, resumeMs, headers = headers)
                }
            }
        }
    }
}
