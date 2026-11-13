package com.lagradost.cloudstream4

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.app_name
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.input.installMouseBackButtonHandler
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.playback.DesktopPlayer
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import com.lagradost.cloudstream4.ui.shell.DesktopShell
import com.lagradost.cloudstream4.ui.shell.applyDarkWindowChrome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Dimension
import java.awt.Frame
import java.awt.GraphicsDevice
import java.awt.Rectangle
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent as AwtKeyEvent
import javax.swing.SwingUtilities
import java.util.concurrent.TimeUnit

fun main() {
    // Compose interop blending lets the Compose player HUD draw over VLC's Swing surface.
    System.setProperty("compose.interop.blending", "true")
    application {
    var settings by remember { mutableStateOf(DesktopStorage.settings) }
    val windowState = rememberWindowState(size = DpSize(1320.dp, 840.dp))
    var isFullscreen by remember { mutableStateOf(false) }
    var placementBeforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
    var sizeBeforeFullscreen by remember { mutableStateOf<DpSize?>(null) }
    var appWindow by remember { mutableStateOf<java.awt.Window?>(null) }
    var fullscreenDevice by remember { mutableStateOf<GraphicsDevice?>(null) }
    var boundsBeforeFullscreen by remember { mutableStateOf<Rectangle?>(null) }
    var frameStateBeforeFullscreen by remember { mutableIntStateOf(Frame.NORMAL) }
    var isClosing by remember { mutableStateOf(false) }

    fun exitFullscreen(): Boolean {
        val target = appWindow
        val device = fullscreenDevice ?: target?.graphicsConfiguration?.device
            ?.takeIf { it.fullScreenWindow === target }
        if (!isFullscreen && device == null && windowState.placement != WindowPlacement.Fullscreen) return false

        // Release native fullscreen first, then restore the exact window mode
        // and bounds captured on entry (including a previously maximized app).
        if (device != null && device.fullScreenWindow === target) {
            runCatching { device.fullScreenWindow = null }
        }
        fullscreenDevice = null
        isFullscreen = false
        windowState.placement = placementBeforeFullscreen
        target?.let { window ->
            if (window is Frame) window.extendedState = Frame.NORMAL
            boundsBeforeFullscreen?.let(window::setBounds)
            if (window is Frame && placementBeforeFullscreen == WindowPlacement.Maximized) {
                window.extendedState = frameStateBeforeFullscreen.takeIf { it != Frame.NORMAL }
                    ?: Frame.MAXIMIZED_BOTH
            }
        }
        return true
    }

    fun toggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen()
        } else {
            val target = appWindow ?: return
            // Capture only on entry so repeated F11/button presses cannot
            // overwrite the user's normal or maximized window restore point.
            placementBeforeFullscreen = windowState.placement
            sizeBeforeFullscreen = windowState.size
            boundsBeforeFullscreen = Rectangle(target.bounds)
            frameStateBeforeFullscreen = (target as? Frame)?.extendedState ?: Frame.NORMAL
            try {
                val device = target.graphicsConfiguration.device
                if (target is Frame) target.extendedState = Frame.NORMAL
                device.fullScreenWindow = target
                fullscreenDevice = device
                isFullscreen = true
            } catch (error: Throwable) {
                // If the platform refuses AWT fullscreen, use Compose's native
                // fullscreen placement, which is also restored by exitFullscreen.
                println("AWT fullscreen unavailable; using Compose fullscreen: ${error.message}")
                windowState.placement = WindowPlacement.Fullscreen
                isFullscreen = true
            }
        }
    }

    // Programmatic exit (leaving the player): never debounced, never a
    // toggle — safe to call from navigation effects at any moment.
    fun forceExitFullscreen() {
        // Do not trust only our Compose flag here: native fullscreen and
        // Compose state can briefly disagree during Windows window transitions.
        // Always issue the restore placement when this explicit exit action is
        // requested, even if isFullscreen has already become false.
        if (isFullscreen || fullscreenDevice != null || windowState.placement == WindowPlacement.Fullscreen) {
            exitFullscreen()
        }
    }

    fun leavePlayer() {
        forceExitFullscreen()
        DesktopPlayer.stop()
        NavigationManager.goBack()
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            DesktopPlayer.initializeAsync()
            DesktopPluginManager.initialize()
            // Auto-update extensions on launch when enabled, then re-check
            // periodically while the app stays open/active.
            runCatching {
                if (DesktopStorage.settings.checkExtensionUpdatesOnStartup) {
                    val pending = DesktopPluginManager.checkForPluginUpdates()
                    if (pending.isNotEmpty()) {
                        DesktopPluginManager.autoUpdatePlugins(pending)
                    }
                }
            }
        }
        launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(6 * 60 * 60 * 1000L) // every 6h while active
                runCatching {
                    if (DesktopStorage.settings.checkExtensionUpdatesOnStartup) {
                        val pending = DesktopPluginManager.checkForPluginUpdates()
                        if (pending.isNotEmpty()) {
                            DesktopPluginManager.autoUpdatePlugins(pending)
                        }
                    }
                }
            }
        }
    }

    // Mouse Back button (XButton1) routes through the exact same behavior as
    // Escape: exit fullscreen first, otherwise go back. See MouseBackButton.kt
    // for delivery limitations on Windows.
    DisposableEffect(Unit) {
        val uninstall = installMouseBackButtonHandler {
            if (NavigationManager.currentDestination is NavDestination.Player) {
                leavePlayer()
            } else if (NavigationManager.canGoBack) {
                NavigationManager.goBack()
            }
        }
        onDispose { uninstall() }
    }

    val themeMode = when (settings.themeMode) {
        "Black" -> CloudStreamThemeMode.Amoled
        "Light" -> CloudStreamThemeMode.Light
        else -> CloudStreamThemeMode.Dark
    }

    LaunchedEffect(isFullscreen) {
        if (isFullscreen) {
            kotlinx.coroutines.delay(220)
            DesktopPlayer.refreshVideoSurface()
        } else {
            // Give Windows and the embedded VLC surface a moment to settle
            // after changing native fullscreen/placement before resizing it.
            val restoreSize = sizeBeforeFullscreen
            // A maximized window's remembered logical size is its old floating
            // size. Reapplying that after restoring Maximized can undo the
            // maximize transition and leave the window/player in a bad state.
            if (restoreSize != null && placementBeforeFullscreen == WindowPlacement.Floating) {
                kotlinx.coroutines.delay(180)
                windowState.size = restoreSize
            }
            sizeBeforeFullscreen = null
            kotlinx.coroutines.delay(220)
            DesktopPlayer.refreshVideoSurface()
        }
    }

    Window(
        onCloseRequest = {
            if (!isClosing) {
                isClosing = true
                appWindow?.isEnabled = false
                DesktopPlayer.releaseAsync()
                    .orTimeout(2500, TimeUnit.MILLISECONDS)
                    .whenComplete { _, _ -> SwingUtilities.invokeLater { exitApplication() } }
            }
        },
        state = windowState,
        title = "CloudStream Desktop",
        icon = painterResource(Res.drawable.default_icon),
        onKeyEvent = { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                when {
                    // Global Search Shortcut: Ctrl+K or Cmd+K
                    (keyEvent.isCtrlPressed || keyEvent.isMetaPressed) && keyEvent.key == Key.K -> {
                        NavigationManager.navigate(NavDestination.Search())
                        true
                    }

                    // Escape: Exit Fullscreen or Go Back
                    keyEvent.key == Key.Escape -> {
                        if (isFullscreen && exitFullscreen()) {
                            true
                        } else if (NavigationManager.canGoBack) {
                            if (NavigationManager.currentDestination is NavDestination.Player) {
                                leavePlayer()
                            } else NavigationManager.goBack()
                            true
                        } else false
                    }

                    // Spacebar: Play / Pause
                    keyEvent.key == Key.Spacebar && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.togglePlay()
                        true
                    }

                    // Left / Right Arrow: use the user's configured seek step
                    keyEvent.key == Key.DirectionLeft && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.seekRelative(-DesktopStorage.settings.defaultSeekSeconds.toLong())
                        true
                    }
                    keyEvent.key == Key.DirectionRight && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.seekRelative(DesktopStorage.settings.defaultSeekSeconds.toLong())
                        true
                    }

                    // Up / Down Arrow: Volume +/-
                    keyEvent.key == Key.DirectionUp && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.setPlayerVolume(DesktopPlayer.volume + 5)
                        true
                    }
                    keyEvent.key == Key.DirectionDown && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.setPlayerVolume(DesktopPlayer.volume - 5)
                        true
                    }

                    // M: Mute
                    keyEvent.key == Key.M && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.togglePlayerMute()
                        true
                    }

                    // Speed controls: [ and ]
                    keyEvent.key == Key.LeftBracket && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.setSpeed(maxOf(0.5f, DesktopPlayer.playbackSpeed - 0.25f))
                        true
                    }
                    keyEvent.key == Key.RightBracket && NavigationManager.currentDestination is NavDestination.Player -> {
                        DesktopPlayer.setSpeed(minOf(2.0f, DesktopPlayer.playbackSpeed + 0.25f))
                        true
                    }

                    else -> false
                }
            } else false
        }
    ) {
        window.minimumSize = Dimension(960, 600)
        LaunchedEffect(window) {
            // The native peer can appear just after the first composition.
            kotlinx.coroutines.delay(120)
            applyDarkWindowChrome(window)
        }
        DisposableEffect(window) {
            appWindow = window
            onDispose {
                if (fullscreenDevice?.fullScreenWindow === window) {
                    runCatching { fullscreenDevice?.fullScreenWindow = null }
                }
                fullscreenDevice = null
                appWindow = null
            }
        }

        // The player embeds a Swing video surface. When it has keyboard focus,
        // Compose Window.onKeyEvent may not receive F11/Escape. Dispatch these
        // at the AWT window level so fullscreen exit and player Back still work.
        DisposableEffect(window) {
            val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            val dispatcher = KeyEventDispatcher { event ->
                // A SwingPanel can own keyboard focus even though its top-level
                // ComposeWindow is still the active app window. Resolve from the
                // event component as well, so F11/Escape remain reliable while
                // the native video surface has focus.
                val eventWindow = event.component?.let(SwingUtilities::getWindowAncestor)
                val belongsToApp = focusManager.activeWindow === window || eventWindow === window
                if (event.id != AwtKeyEvent.KEY_PRESSED || !belongsToApp) {
                    false
                } else when (event.keyCode) {
                    AwtKeyEvent.VK_F11 -> {
                        if (NavigationManager.currentDestination is NavDestination.Player) {
                            toggleFullscreen()
                            true
                        } else false
                    }
                    AwtKeyEvent.VK_ESCAPE -> {
                        when {
                            isFullscreen -> exitFullscreen()
                            NavigationManager.currentDestination is NavDestination.Player -> {
                                leavePlayer()
                                true
                            }
                            NavigationManager.canGoBack -> {
                                NavigationManager.goBack()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            focusManager.addKeyEventDispatcher(dispatcher)
            onDispose { focusManager.removeKeyEventDispatcher(dispatcher) }
        }

        CloudStreamTheme(mode = themeMode) {
            CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
                Box(Modifier.fillMaxSize()) {
                    DesktopShell(
                        settings = settings,
                        onSettingsChanged = {
                            settings = it
                            DesktopPlayer.onSettingsChanged()
                        },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = {
                            toggleFullscreen()
                        },
                        onForceExitFullscreen = {
                            forceExitFullscreen()
                        },
                        onPlayerBack = { leavePlayer() }
                    )
                    if (isClosing) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 2.dp)
                                    Text("Closing CloudStream…")
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
