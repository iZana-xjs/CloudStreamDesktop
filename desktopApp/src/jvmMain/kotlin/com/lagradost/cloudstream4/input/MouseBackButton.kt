package com.lagradost.cloudstream4.input

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent

/**
 * Windows mouse Back button (typically XButton1) as app back navigation.
 *
 * Integration notes and limitations (read before "fixing" this file):
 * - The JVM only delivers extended mouse buttons (anything outside 1-3) if
 *   the platform AWT implementation supports them. On Windows this delivery
 *   is unreliable-to-absent depending on JDK/driver, so the button may do
 *   nothing even with this handler installed. There is deliberately no
 *   polling fallback and no new native dependency.
 * - The handler is passive observation: it consumes only matched Back
 *   presses, so a matched event cannot also navigate twice through another
 *   component, and unmatched events flow untouched.
 * - Compose text fields are not Swing text components, so typing inside them
 *   cannot be detected from here; Esc remains the safe path while typing.
 *   Swing text components (if any) are explicitly skipped.
 * - Events are honored only while this app's window holds focus, so clicks
 *   in other applications can never drive our navigation.
 */
fun shouldNavigateBackOnMouseButton(
    button: Int,
    eventId: Int,
    isTextInputFocused: Boolean,
    appWindowFocused: Boolean
): Boolean {
    if (eventId != MouseEvent.MOUSE_PRESSED) return false
    if (button <= 3) return false
    if (!appWindowFocused) return false
    if (isTextInputFocused) return false
    return true
}

/**
 * Installs a JVM-local AWT listener that routes Back-button presses to
 * [onBack]. Windows-only; returns an uninstaller (no-op elsewhere).
 * Safe to call once per app lifetime.
 */
fun installMouseBackButtonHandler(onBack: () -> Unit): () -> Unit {
    if (!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        return {}
    }
    val toolkit = Toolkit.getDefaultToolkit()
    val listener = AWTEventListener { event ->
        if (event !is MouseEvent) return@AWTEventListener
        val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
        val appFocused = focusManager.focusedWindow != null
        val textFocused = focusManager.focusOwner is JTextComponent
        if (!shouldNavigateBackOnMouseButton(event.button, event.id, textFocused, appFocused)) {
            return@AWTEventListener
        }
        event.consume()
        SwingUtilities.invokeLater { runCatching { onBack() } }
    }
    toolkit.addAWTEventListener(listener, AWTEvent.MOUSE_EVENT_MASK)
    return { toolkit.removeAWTEventListener(listener) }
}
