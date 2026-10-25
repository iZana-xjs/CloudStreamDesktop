package com.lagradost.cloudstream4.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType

/**
 * Poster-led media card. Artwork carries the color; chrome stays quiet:
 * a hairline border, a small lift on hover, and a single play affordance.
 * Only real metadata is shown (year / type / rating supplied by the caller).
 */
@Composable
fun MediaCard(
    title: String,
    posterUrl: String?,
    year: Int? = null,
    rating: Double? = null,
    type: String? = null,
    providerName: String? = null,
    watchProgress: Float? = null,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 168.dp,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val active = isHovered || isFocused

    val scale by animateFloatAsState(
        targetValue = if (active) 1.015f else 1f,
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "card-scale"
    )
    val elevation by animateDpAsState(
        targetValue = if (active) 10.dp else 2.dp,
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "card-elevation"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> ShiopaStyle.AccentMain
            isHovered -> ShiopaStyle.GlassBorderHover
            else -> ShiopaStyle.GlassBorder
        },
        animationSpec = androidx.compose.animation.core.tween(140),
        label = "card-border"
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .scale(scale)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = elevation,
                    shape = ShiopaStyle.CornerLg,
                    ambientColor = Color.Black.copy(alpha = 0.55f),
                    spotColor = Color.Black.copy(alpha = 0.45f)
                )
                .clip(ShiopaStyle.CornerLg)
                .border(1.dp, borderColor, ShiopaStyle.CornerLg)
                .background(ShiopaStyle.CardBackground)
        ) {
            // Intentional missing-artwork state: title initials on a quiet
            // gradient. Always painted underneath the image so a failed load
            // never leaves a blank rectangle.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(ShiopaStyle.SurfaceCard, ShiopaStyle.Sidebar)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title.trim().take(2).uppercase(),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ShiopaStyle.TextPrimary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .clip(ShiopaStyle.CornerSm)
                            .background(ShiopaStyle.AccentMain.copy(alpha = 0.55f))
                    )
                }
            }

            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // A single legible playback affordance on hover/focus. No permanent
            // badge stacks, no tilt, no colored bloom around the artwork.
            if (active) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.28f))
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center)
                        .clip(CircleShape)
                        .background(ShiopaStyle.AccentMain),
                    contentAlignment = Alignment.Center
                ) {
                    ShiopaIcon(
                        icon = ShiopaIcon.Play,
                        size = 14.dp,
                        tint = ShiopaStyle.TextOnAccent,
                        strokeWidth = 0.dp,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            if (watchProgress != null && watchProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x66000000))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(watchProgress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(ShiopaStyle.AccentMain)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))

        Text(
            text = title,
            style = ShiopaType.BodyStrong,
            color = ShiopaStyle.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!providerName.isNullOrBlank()) {
            Text(
                text = "via $providerName",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Normal,
                color = ShiopaStyle.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (year != null && year > 0) {
                Text(
                    text = year.toString(),
                    fontSize = 11.sp,
                    color = ShiopaStyle.TextMuted,
                    maxLines = 1
                )
            }
            val prettyType = type?.takeIf { it.isNotBlank() }?.let(::displayMediaType)
            if (prettyType != null) {
                if (year != null && year > 0) {
                    Text(text = "·", fontSize = 10.sp, color = ShiopaStyle.TextMuted)
                }
                Text(
                    text = prettyType,
                    fontSize = 11.sp,
                    color = ShiopaStyle.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (rating != null && rating > 0) {
                if ((year != null && year > 0) || prettyType != null) {
                    Text(text = "·", fontSize = 10.sp, color = ShiopaStyle.TextMuted)
                }
                Text(
                    text = "★ ${"%.1f".format(rating)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = ShiopaStyle.AccentGold,
                    maxLines = 1
                )
            }
        }
    }
}

/** Turns provider enum names into human labels without inventing metadata. */
internal fun displayMediaType(raw: String): String = when (raw.lowercase()) {
    "tvseries" -> "TV Series"
    "animemovie" -> "Anime Film"
    "animeseries" -> "Anime Series"
    "movie" -> "Movie"
    "anime" -> "Anime"
    "ova" -> "OVA"
    "ona" -> "ONA"
    else -> raw.replace(Regex("(?<=[a-z])([A-Z])"), " $1")
}
