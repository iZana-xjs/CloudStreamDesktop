package com.lagradost.cloudstream4.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloudStream desktop design tokens.
 *
 * Design rules:
 * - One product voice: a calm near-black canvas, clearly separated raised
 *   surfaces, hairline borders, and strong readable type.
 * - Exactly one primary accent (CloudStream cinema red) drives primary
 *   actions, selection, and focus. Gold, green, cyan and violet are reserved
 *   for meaningful states or provider identity, never decoration.
 * - Corner radii come from one scale. Pills are reserved for chips and tags,
 *   not for every button or card.
 * - Typography follows a single type scale (ShiopaType) so every screen
 *   speaks the same hierarchy.
 * - Legacy token names are kept as aliases so existing screens compile and
 *   inherit the refreshed visual language without a screen-by-screen rewrite.
 */
object ShiopaStyle {
    // ------------------------------------------------------------------
    // Surfaces — one canvas, one rail, three raised levels
    // ------------------------------------------------------------------
    /** The page canvas every screen sits on. */
    val CanvasBlack = Color(0xFF0A0B0F)
    /** Persistent navigation background; a touch deeper than the canvas. */
    val Sidebar = Color(0xFF0C0E13)
    /** Raised panel, top bar field, chips. */
    val SurfaceCard = Color(0xFF14171D)
    /** One step above SurfaceCard for hovered rows and grouped controls. */
    val SurfaceRaised = Color(0xFF1A1D25)
    /** Highest common surface: pressed/hovered controls. */
    val SurfaceHover = Color(0xFF20242D)

    // Legacy names kept for existing screens.
    val CanvasNavy = Color(0xFF0C0E13)
    val CanvasPurple = Color(0xFF10121A)
    val SurfaceNavy = Color(0xFF111319)
    val SurfacePurple = Color(0xFF171A22)
    val GlassSurface = Color(0xF20E1016)
    val GlassSurfaceHigh = Color(0xF8161921)
    val CardBackground = Color(0xFF12141A)
    val CardHover = Color(0xFF1A1D25)
    val ActionPillBackground = Color(0xFFF5F6F8)
    val ActionPillText = Color(0xFF0A0B0F)
    val PillChipBackground = Color(0x14FFFFFF)
    val PillChipBorder = Color(0x24FFFFFF)

    // ------------------------------------------------------------------
    // One main accent + reserved semantic colors
    // ------------------------------------------------------------------
    /** Single primary accent for actions, selection, and focus. */
    val AccentMain = Color(0xFFE50914)       // Cinema Red (CloudStream brand)
    val AccentMainBright = Color(0xFFF41D28) // Hover/pressed primary
    /** Low-opacity accent wash for selected rows and chips. */
    val AccentSoft = Color(0x24E50914)

    // Reserved for meaningful states only.
    val AccentRed = AccentMain
    val AccentPurple = Color(0xFF8C7CF0)    // Provider identity
    val AccentCyan = Color(0xFF63B3D7)      // Informational links
    val AccentGreen = Color(0xFF4CC38A)     // Online / playback status
    val AccentGold = Color(0xFFE8B45A)      // Star rating

    // Restrained borders (no decorative glow).
    val GlassBorder = Color(0x1AFFFFFF)
    val GlassBorderHover = Color(0x33FFFFFF)
    val GlassBorderActive = Color(0x80FFFFFF)
    /** Structural hairline used for rails/dividers. */
    val Divider = Color(0x12FFFFFF)

    // ------------------------------------------------------------------
    // Typography colors
    // ------------------------------------------------------------------
    val TextPrimary = Color(0xFFF4F5F7)
    val TextSecondary = Color(0xFFA6ACB9)
    val TextMuted = Color(0xFF7E8593)
    val TextOnAccent = Color(0xFFFFFFFF)

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
    // Restrained gradients & dark overlays
    // ------------------------------------------------------------------
    val HeroGradient = Brush.verticalGradient(
        0.0f to Color.Transparent,
        0.42f to Color(0x2E0A0B0F),
        0.74f to Color(0xC70A0B0F),
        1.0f to Color(0xFF0A0B0F)
    )

    val HeroSideGradient = Brush.horizontalGradient(
        0.0f to Color(0xF50A0B0F),
        0.40f to Color(0xBB0A0B0F),
        0.76f to Color(0x280A0B0F),
        1.0f to Color.Transparent
    )

    val AmbientBackgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF0C0E13), Color(0xFF0A0B0F), Color(0xFF08090C))
    )

    val GlassBorderStroke = BorderStroke(1.dp, GlassBorder)
    val GlassBorderHoverStroke = BorderStroke(1.dp, GlassBorderHover)
    val GlassBorderActiveStroke = BorderStroke(1.dp, GlassBorderActive)

    /** Accent-tinted focus ring used on interactive fields. */
    val AccentBorderStroke = BorderStroke(1.5.dp, AccentMain.copy(alpha = 0.9f))
}

/**
 * Single type scale for the desktop app. Every screen uses these so the
 * hierarchy reads consistently instead of ad-hoc sp values.
 */
object ShiopaType {
    // Hero / large display
    val Display = TextStyle(
        fontSize = 38.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp
    )
    val Headline = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp
    )
    val Title = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    val Body = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal
    )
    val BodyStrong = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )
    val Caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 17.sp,
        fontWeight = FontWeight.Normal
    )
    val Overline = TextStyle(
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp
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
    val Xxxl = 48.dp

    /** Standard horizontal page inset. */
    val PageInset = 32.dp
    /** Narrower inset for small desktop windows. */
    val PageInsetCompact = 24.dp
    /** Wider inset used by cinematic pages (details). */
    val PageInsetWide = 48.dp
    /** Vertical gap between Home sections. */
    val SectionGap = 28.dp
}
