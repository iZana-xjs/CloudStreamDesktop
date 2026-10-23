package com.lagradost.cloudstream4.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle

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
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val active = isHovered || isFocused

    val scale by animateFloatAsState(targetValue = if (active) 1.04f else 1.0f)
    val cardElevation by animateDpAsState(targetValue = if (active) 18.dp else 2.dp)
    val tilt by animateFloatAsState(targetValue = if (active) 2.2f else 0f)
    val cardShape = ShiopaStyle.CornerLg

    Column(
        modifier = modifier
            .width(170.dp)
            .padding(ShiopaSpacing.Sm)
            .scale(scale)
            .pointerHoverIcon(PointerIcon.Hand)
            .focusable(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .shadow(
                    elevation = cardElevation,
                    shape = cardShape,
                    ambientColor = ShiopaStyle.AccentPurple.copy(alpha = if (active) 0.50f else 0.12f),
                    spotColor = ShiopaStyle.AccentPurple.copy(alpha = if (active) 0.42f else 0.10f)
                )
                .graphicsLayer {
                    // Small perspective lift on hover/focus. Keep the angle subtle so
                    // poster artwork and text stay readable at every window size.
                    rotationX = -tilt * 0.55f
                    rotationY = tilt
                    cameraDistance = 12f * density
                }
                .border(
                    width = if (active) 1.5.dp else 1.dp,
                    color = if (active) ShiopaStyle.AccentPurple.copy(alpha = 0.95f) else ShiopaStyle.GlassBorder,
                    shape = cardShape
                ),
            shape = cardShape,
            color = ShiopaStyle.CardBackground
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF161528), Color(0xFF0F1019))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title.take(2).uppercase(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0x66FFFFFF)
                        )
                    }
                }

                // Bottom gradient so badges stay legible
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6090A10))
                            )
                        )
                )

                // Hover / focus play overlay
                if (active) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(ShiopaStyle.AccentMain),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 16.sp,
                            color = Color.White,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }

                // Top-right rating badge (gold = rating)
                if (rating != null && rating > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .clip(ShiopaStyle.CornerSm)
                            .background(Color(0xDD090A10))
                            .border(1.dp, ShiopaStyle.AccentGold.copy(alpha = 0.4f), ShiopaStyle.CornerSm)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "★ ${"%.1f".format(rating)}",
                            color = ShiopaStyle.AccentGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Top-left type / quality tag
                val tagText = when {
                    !providerName.isNullOrBlank() -> providerName
                    !type.isNullOrBlank() -> type.uppercase()
                    else -> "HD"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(7.dp)
                        .clip(ShiopaStyle.CornerSm)
                        .background(Color(0xDD090A10))
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerSm)
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = tagText,
                        color = Color(0xFFC0C0D4),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Resume watch progress indicator bar
                if (watchProgress != null && watchProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { watchProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .align(Alignment.BottomCenter),
                        color = ShiopaStyle.AccentMain,
                        trackColor = Color(0x66000000)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))

        Text(
            text = title,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 13.5.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                color = if (active) Color.White else Color(0xFFE2E2EC)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (year != null && year > 0) {
                Text(
                    text = year.toString(),
                    fontSize = 11.5.sp,
                    color = ShiopaStyle.TextMuted,
                    fontWeight = FontWeight.Normal
                )
            }
            if (!type.isNullOrBlank()) {
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    color = ShiopaStyle.TextMuted
                )
                Text(
                    text = type.replaceFirstChar { it.uppercase() },
                    fontSize = 11.5.sp,
                    color = ShiopaStyle.TextMuted
                )
            }
        }
    }
}
