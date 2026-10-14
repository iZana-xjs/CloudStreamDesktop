package com.lagradost.cloudstream4.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloudStream desktop design tokens.
 *
 * Design rules:
 * - One main accent (cinema red, the CloudStream brand color) drives
 *   primary actions, selected states, and focus. Other palette colors
 *   are reserved for meaningful states: gold = rating, green = quality /
 *   match / playback status, violet = provider identity, cyan = links.
 * - Corner radii come from one scale (CornerSm…CornerXl). PillShape is
 *   reserved for genuine pills (compact filter chips / tags), not every
 *   button and card.
 * - Typography follows a single type scale (ShiopaType) so every screen
 *   speaks the same hierarchy.
 */
object ShiopaStyle {
    // ------------------------------------------------------------------
    // Surfaces — dark cinematic canvas with clear contrast
    // ------------------------------------------------------------------
    val CanvasBlack = Color(0xFF090A14)
    val CanvasNavy = Color(0xFF0D1020)
    val CanvasPurple = Color(0xFF141329)
    val SurfaceNavy = Color(0xFF101222)
    val SurfacePurple = Color(0xFF17162D)
    val GlassSurface = Color(0xE60E1020)
    val GlassSurfaceHigh = Color(0xF2141528)
    val CardBackground = Color(0xFF121426)
    val CardHover = Color(0xFF1B1A34)
    val SurfaceCard = Color(0xFF141629)
    val ActionPillBackground = Color(0xFFFFFFFF)
    val ActionPillText = Color(0xFF090A10)
    val PillChipBackground = Color(0x1AFFFFFF)
    val PillChipBorder = Color(0x22FFFFFF)

    // ------------------------------------------------------------------
    // One main accent + reserved semantic colors
    // ------------------------------------------------------------------
    /** Single primary accent for actions, selection, and focus. */
    val AccentMain = Color(0xFFE50914)       // Cinema Red (CloudStream brand)
    val AccentMainBright = Color(0xFFFF2E51) // Brighter red for gradients on the accent

    // Reserved for meaningful states only
    val AccentRed = AccentMain
    val AccentPurple = Color(0xFF8B5CF6)    // Provider identity
    val AccentCyan = Color(0xFF06B6D4)      // Informational links
    val AccentGreen = Color(0xFF10B981)     // Quality / match / playback status
    val AccentGold = Color(0xFFFBBF24)      // Star rating

    // Restrained borders (no decorative glow everywhere)
    val GlassBorder = Color(0x18FFFFFF)
    val GlassBorderHover = Color(0x38FFFFFF)
    val GlassBorderActive = Color(0x75FFFFFF)

    // ------------------------------------------------------------------
    // Typography
    // ------------------------------------------------------------------
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA2A2B8)
    val TextMuted = Color(0xFF6B6C82)

    // ------------------------------------------------------------------
    // Corner radii — one scale, pill reserved for genuine pills
    // ------------------------------------------------------------------
    val CornerSm = RoundedCornerShape(6.dp)    // badges, small tags
    val CornerMd = RoundedCornerShape(10.dp)   // buttons, inputs, controls
    val CornerLg = RoundedCornerShape(14.dp)   // cards, rows
    val CornerXl = RoundedCornerShape(20.dp)   // dialogs, hero panels
    /** Compact filter chips / genre tags only — not every button. */
    val PillShape = RoundedCornerShape(50.dp)
    // Legacy aliases kept so existing call sites keep compiling.
    val CardShape = CornerLg
    val CardLargeShape = CornerXl
    val ChipShape = CornerSm
    /** Buttons and tappable controls. */
    val ControlShape = CornerMd

    // ------------------------------------------------------------------
    // Subtle gradients & dark overlays (restrained)
    // ------------------------------------------------------------------
    val HeroGradient = Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.45f to Color(0x40090A10),
        0.75f to Color(0xD0090A10),
        1.0f to Color(0xFF090A10)
    )

    val HeroSideGradient = Brush.horizontalGradient(
        0.0f to Color(0xEE090A10),
        0.45f to Color(0x99090A10),
        0.85f to Color.Transparent
    )

    val AmbientBackgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF0C0D17), Color(0xFF090A10), Color(0xFF07070D))
    )

    val GlassBorderStroke = BorderStroke(1.dp, GlassBorder)
    val GlassBorderHoverStroke = BorderStroke(1.dp, GlassBorderHover)
    val GlassBorderActiveStroke = BorderStroke(1.dp, GlassBorderActive)

    /** Accent-tinted focus/focus ring used on interactive fields. */
    val AccentBorderStroke = BorderStroke(1.5.dp, AccentMain.copy(alpha = 0.85f))
}

/**
 * Single type scale for the desktop app. Every screen uses these so the
 * hierarchy reads consistently instead of ad-hoc sp values.
 */
object ShiopaType {
    // Hero / large display
    val Display = androidx.compose.ui.text.TextStyle(
        fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = 0.3.sp
    )
    val Headline = androidx.compose.ui.text.TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.Bold
    )
    val Title = androidx.compose.ui.text.TextStyle(
        fontSize = 17.sp, fontWeight = FontWeight.Bold
    )
    val Body = androidx.compose.ui.text.TextStyle(
        fontSize = 13.5.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp
    )
    val BodyStrong = androidx.compose.ui.text.TextStyle(
        fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold
    )
    val Caption = androidx.compose.ui.text.TextStyle(
        fontSize = 11.5.sp, fontWeight = FontWeight.Normal
    )
    val Overline = androidx.compose.ui.text.TextStyle(
        fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp
    )
}

/** Consistent spacing grid so rails, headers, and actions align. */
object ShiopaSpacing {
    val Xs = 4.dp
    val Sm = 8.dp
    val Md = 12.dp
    val Lg = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    /** Standard horizontal page inset. */
    val PageInset = 28.dp
    /** Wider inset used by cinematic pages (details). */
    val PageInsetWide = 48.dp
}
