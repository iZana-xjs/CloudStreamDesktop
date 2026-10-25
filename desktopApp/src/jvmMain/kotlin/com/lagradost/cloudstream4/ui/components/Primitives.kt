package com.lagradost.cloudstream4.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle

/** Shared button tones: one filled primary, one quiet bordered, one bare. */
enum class ShiopaButtonTone { Primary, Secondary, Ghost }

@Composable
fun ShiopaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ShiopaButtonTone = ShiopaButtonTone.Secondary,
    icon: ShiopaIcon? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val pressed by source.collectIsPressedAsState()

    val shape = ShiopaStyle.ControlShape
    val background by animateColorAsState(
        targetValue = when (tone) {
            ShiopaButtonTone.Primary -> when {
                !enabled -> ShiopaStyle.AccentMain.copy(alpha = 0.35f)
                pressed -> ShiopaStyle.AccentMain
                hovered -> ShiopaStyle.AccentMainBright
                else -> ShiopaStyle.AccentMain
            }
            ShiopaButtonTone.Secondary -> when {
                selected -> ShiopaStyle.AccentSoft
                pressed -> ShiopaStyle.SurfaceHover
                hovered || focused -> ShiopaStyle.SurfaceHover
                else -> ShiopaStyle.SurfaceCard
            }
            ShiopaButtonTone.Ghost -> when {
                hovered || focused -> Color(0x14FFFFFF)
                else -> Color.Transparent
            }
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "button-background"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> ShiopaStyle.AccentMain
            selected -> ShiopaStyle.AccentMain.copy(alpha = 0.75f)
            tone == ShiopaButtonTone.Primary -> Color.Transparent
            hovered -> ShiopaStyle.GlassBorderHover
            else -> ShiopaStyle.GlassBorder
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "button-border"
    )

    val textColor = when {
        !enabled -> ShiopaStyle.TextMuted
        tone == ShiopaButtonTone.Primary -> ShiopaStyle.TextOnAccent
        else -> ShiopaStyle.TextPrimary
    }
    val iconColor = when {
        !enabled -> ShiopaStyle.TextMuted
        tone == ShiopaButtonTone.Primary -> ShiopaStyle.TextOnAccent
        selected -> ShiopaStyle.AccentMain
        else -> ShiopaStyle.TextPrimary
    }

    Row(
        modifier = modifier
            .height(if (compact) 34.dp else 40.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .padding(horizontal = if (compact) 13.dp else 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon != null) {
            ShiopaIcon(icon = icon, size = 14.dp, tint = iconColor, strokeWidth = 1.7.dp)
        }
        Text(
            text = label,
            fontSize = if (compact) 12.5.sp else 13.5.sp,
            fontWeight = if (tone == ShiopaButtonTone.Primary) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Low-emphasis text action used for section "View all" links. */
@Composable
fun ShiopaTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val background by animateColorAsState(
        targetValue = if (hovered) Color(0x0FFFFFFF) else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "text-action-background"
    )
    val color by animateColorAsState(
        targetValue = if (hovered || focused) ShiopaStyle.TextPrimary else ShiopaStyle.TextSecondary,
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "text-action-color"
    )

    Row(
        modifier = modifier
            .clip(ShiopaStyle.CornerSm)
            .background(background)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
        ShiopaIcon(
            icon = ShiopaIcon.ChevronRight,
            size = 12.dp,
            tint = color,
            strokeWidth = 1.7.dp
        )
    }
}

/** Compact filter pill. Selection uses an accent wash, not a solid block. */
@Composable
fun ShiopaFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()

    val background by animateColorAsState(
        targetValue = when {
            selected -> ShiopaStyle.AccentSoft
            hovered || focused -> ShiopaStyle.SurfaceHover
            else -> ShiopaStyle.SurfaceCard
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "chip-background"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            focused -> ShiopaStyle.AccentMain
            selected -> ShiopaStyle.AccentMain.copy(alpha = 0.7f)
            hovered -> ShiopaStyle.GlassBorderHover
            else -> ShiopaStyle.GlassBorder
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "chip-border"
    )

    Box(
        modifier = modifier
            .height(32.dp)
            .clip(ShiopaStyle.PillShape)
            .background(background)
            .border(1.dp, borderColor, ShiopaStyle.PillShape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) ShiopaStyle.TextPrimary else ShiopaStyle.TextSecondary,
            maxLines = 1
        )
    }
}

/** Borderless icon action with a restrained selected/hover treatment. */
@Composable
fun ShiopaIconButton(
    icon: ShiopaIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    buttonSize: Dp = 36.dp,
    iconSize: Dp = 17.dp,
    enabled: Boolean = true
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val background by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            selected -> ShiopaStyle.AccentSoft
            hovered || focused -> ShiopaStyle.SurfaceHover
            else -> Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "icon-button-background"
    )
    val tint by animateColorAsState(
        targetValue = when {
            !enabled -> ShiopaStyle.TextMuted.copy(alpha = 0.4f)
            selected -> ShiopaStyle.AccentMain
            else -> ShiopaStyle.TextSecondary
        },
        animationSpec = androidx.compose.animation.core.tween(120),
        label = "icon-button-tint"
    )
    val borderColor = when {
        !enabled -> Color.Transparent
        focused -> ShiopaStyle.AccentMain
        selected -> ShiopaStyle.AccentMain.copy(alpha = 0.45f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(ShiopaStyle.CornerMd)
            .background(background)
            .border(1.dp, borderColor, ShiopaStyle.CornerMd)
            .semantics {
                this.contentDescription = contentDescription
                if (!enabled) disabled()
            }
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        contentAlignment = Alignment.Center
    ) {
        ShiopaIcon(icon = icon, size = iconSize, tint = tint, strokeWidth = 1.6.dp)
    }
}

/** Small keyboard hint used by the global search entry point. */
@Composable
fun ShiopaKeyCap(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(ShiopaStyle.CornerSm)
            .background(Color(0x0AFFFFFF))
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerSm)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Medium,
            color = ShiopaStyle.TextMuted,
            maxLines = 1
        )
    }
}

@Composable
fun ShiopaStatusDot(
    color: Color = ShiopaStyle.AccentGreen,
    size: Dp = 6.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
