package com.lagradost.cloudstream4.ui.shell

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.ptr.IntByReference
import java.awt.Window

/**
 * Best-effort native chrome integration.
 *
 * JNA is already shipped with the desktop distribution (VLC playback), so no
 * dependency is added. Every call is wrapped: non-Windows systems, older
 * Windows builds, or a JNA-free runtime simply keep the OS default title bar
 * instead of failing startup.
 */
internal interface DwmApi : Library {
    fun DwmSetWindowAttribute(
        hwnd: HWND,
        attribute: Int,
        value: Pointer,
        size: Int
    ): Int
}

private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
private const val DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY = 19
private const val DWMWA_CAPTION_COLOR = 35
private const val DWMWA_TEXT_COLOR = 36

/** 0xRRGGBB -> Windows COLORREF 0x00BBGGRR. */
private fun colorRef(rgb: Int): Int =
    ((rgb and 0xFF) shl 16) or (rgb and 0xFF00) or ((rgb shr 16) and 0xFF)

/**
 * Asks DWM for a dark title bar that matches the CloudStream canvas and text
 * colors. On Windows 10 the two color attributes are ignored, leaving the
 * standard dark caption; on non-Windows this is a no-op.
 */
fun applyDarkWindowChrome(window: Window) {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) {
        return
    }
    runCatching {
        val peer = Native.getWindowPointer(window) ?: return
        val hwnd = HWND(peer)
        val dwm = Native.load("dwmapi", DwmApi::class.java)

        fun set(attribute: Int, value: Int): Int {
            val ref = IntByReference(value)
            return dwm.DwmSetWindowAttribute(hwnd, attribute, ref.pointer, 4)
        }

        if (set(DWMWA_USE_IMMERSIVE_DARK_MODE, 1) != 0) {
            set(DWMWA_USE_IMMERSIVE_DARK_MODE_LEGACY, 1)
        }
        // Match the caption to ShiopaStyle.CanvasBlack (#0A0B0F) and the
        // caption text to TextPrimary (#F4F5F7).
        set(DWMWA_CAPTION_COLOR, colorRef(0x0A0B0F))
        set(DWMWA_TEXT_COLOR, colorRef(0xF4F5F7))
    }
}
