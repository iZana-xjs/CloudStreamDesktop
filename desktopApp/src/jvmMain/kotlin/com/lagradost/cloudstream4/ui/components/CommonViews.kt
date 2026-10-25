package com.lagradost.cloudstream4.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.core.ToastMessage
import com.lagradost.cloudstream4.core.ToastType
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType
import kotlinx.coroutines.delay

@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    val activeToasts = remember { mutableStateListOf<ToastMessage>() }

    LaunchedEffect(Unit) {
        NotificationService.notifications.collect { message ->
            activeToasts.add(message)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.End
        ) {
            for (toast in activeToasts) {
                key(toast) {
                    ToastItem(toast = toast, onDismiss = { activeToasts.remove(toast) })
                }
            }
        }
    }
}

@Composable
fun ToastItem(toast: ToastMessage, onDismiss: () -> Unit) {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(toast) {
        delay(toast.durationMs)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
    ) {
        val (symbol, accentColor) = when (toast.type) {
            is ToastType.Success -> ShiopaIcon.Check to ShiopaStyle.AccentGreen
            is ToastType.Error -> ShiopaIcon.Close to Color(0xFFEF5B5B)
            is ToastType.Warning -> ShiopaIcon.Close to ShiopaStyle.AccentGold
            is ToastType.Info -> ShiopaIcon.Search to ShiopaStyle.AccentCyan
        }

        Box(
            modifier = Modifier
                .width(348.dp)
                .clip(ShiopaStyle.CornerLg)
                .background(ShiopaStyle.SurfaceRaised)
                .border(1.dp, accentColor.copy(alpha = 0.28f), ShiopaStyle.CornerLg)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    ShiopaIcon(
                        icon = symbol,
                        size = 14.dp,
                        tint = accentColor,
                        strokeWidth = 1.8.dp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = toast.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = ShiopaStyle.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!toast.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = toast.description,
                            fontSize = 11.5.sp,
                            color = ShiopaStyle.TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingView(message: String = "Loading...", modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                color = ShiopaStyle.AccentMain,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(ShiopaSpacing.Lg))
            Text(
                text = message,
                style = ShiopaType.Caption,
                color = ShiopaStyle.TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun EmptyView(
    title: String,
    description: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: ShiopaIcon = ShiopaIcon.Explore
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ShiopaStyle.SurfaceCard)
                    .border(1.dp, ShiopaStyle.GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ShiopaIcon(
                    icon = icon,
                    size = 20.dp,
                    tint = ShiopaStyle.TextMuted,
                    strokeWidth = 1.5.dp
                )
            }
            Spacer(modifier = Modifier.height(ShiopaSpacing.Lg))
            Text(
                text = title,
                style = ShiopaType.Title,
                color = ShiopaStyle.TextPrimary,
                textAlign = TextAlign.Center
            )
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))
                Text(
                    text = description,
                    style = ShiopaType.Caption,
                    color = ShiopaStyle.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp)
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))
                ShiopaButton(
                    label = actionLabel,
                    onClick = onAction,
                    tone = ShiopaButtonTone.Primary,
                    compact = true
                )
            }
        }
    }
}

@Composable
fun ErrorView(
    title: String = "Something went wrong",
    details: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FEF5B5B))
                    .border(1.dp, Color(0x40EF5B5B), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                ShiopaIcon(
                    icon = ShiopaIcon.Close,
                    size = 20.dp,
                    tint = Color(0xFFEF5B5B),
                    strokeWidth = 1.8.dp
                )
            }
            Spacer(modifier = Modifier.height(ShiopaSpacing.Lg))
            Text(
                text = title,
                style = ShiopaType.Title,
                color = ShiopaStyle.TextPrimary,
                textAlign = TextAlign.Center
            )
            if (!details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(ShiopaSpacing.Sm))
                Text(
                    text = details,
                    style = ShiopaType.Caption,
                    color = ShiopaStyle.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 460.dp)
                )
            }
            if (onRetry != null) {
                Spacer(modifier = Modifier.height(ShiopaSpacing.Xl))
                ShiopaButton(
                    label = "Try again",
                    onClick = onRetry,
                    tone = ShiopaButtonTone.Primary,
                    compact = true
                )
            }
        }
    }
}
