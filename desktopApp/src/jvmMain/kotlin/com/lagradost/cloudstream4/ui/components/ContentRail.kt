package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType

/**
 * One Home section: aligned heading, optional "View all" action, and a
 * horizontally scrolling row of media cards. The heading and the first card
 * share the same page inset so the page reads on one vertical grid.
 */
@Composable
fun ContentRail(
    title: String,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = ShiopaSpacing.PageInset,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ShiopaSpacing.Xl, bottom = ShiopaSpacing.Xs)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ShiopaType.Title,
                    color = ShiopaStyle.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = ShiopaType.Caption,
                        color = ShiopaStyle.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onSeeAllClick != null) {
                ShiopaTextAction(
                    label = "View all",
                    onClick = onSeeAllClick
                )
            }
        }

        Spacer(modifier = Modifier.height(ShiopaSpacing.Md))

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = horizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Lg)
            ) {
                content()
            }
        }
    }
}
