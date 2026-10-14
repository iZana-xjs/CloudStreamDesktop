package com.lagradost.cloudstream4.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.ui.theme.ShiopaSpacing
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import com.lagradost.cloudstream4.ui.theme.ShiopaType

@Composable
fun ContentRail(
    title: String,
    subtitle: String? = null,
    onSeeAllClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth().padding(vertical = ShiopaSpacing.Lg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ShiopaSpacing.PageInset, vertical = ShiopaSpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    fontSize = ShiopaType.Headline.fontSize,
                    fontWeight = ShiopaType.Headline.fontWeight,
                    color = ShiopaStyle.TextPrimary
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = ShiopaType.Caption.fontSize,
                        color = ShiopaStyle.TextMuted
                    )
                }
            }

            if (onSeeAllClick != null) {
                TextButton(onClick = onSeeAllClick) {
                    Text(
                        text = "View all ›",
                        color = ShiopaStyle.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = ShiopaType.Body.fontSize
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = ShiopaSpacing.PageInset - ShiopaSpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(ShiopaSpacing.Sm)
            ) {
                content()
            }
        }
    }
}
