package com.lagradost.cloudstream4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.HistoryItem
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.EmptyView

import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val historyItems = remember { mutableStateListOf<HistoryItem>().apply { addAll(DesktopStorage.getHistory()) } }
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Watch History",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "${historyItems.size} Titles in Progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShiopaStyle.TextMuted
                )
            }

            if (historyItems.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(Color(0x1AE11D48))
                        .border(1.dp, Color(0x33E11D48), ShiopaStyle.PillShape)
                        .clickable { showClearDialog = true }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Clear All History", color = Color(0xFFFB7185), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (historyItems.isEmpty()) {
            EmptyView(
                title = "No Watch History",
                description = "Shows and movies you watch will appear here so you can pick up right where you left off.",
                actionLabel = "Explore Media",
                onAction = { NavigationManager.navigate(NavDestination.Home) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyItems, key = { "${it.apiName}-${it.url}" }) { item ->
                    HistoryRowItem(
                        item = item,
                        onResumeClick = {
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = item.url,
                                    apiName = item.apiName,
                                    title = item.title,
                                        posterUrl = com.lagradost.cloudstream4.ui.screens.absolutePosterUrl(
                                            item.posterUrl,
                                            APIHolder.allProviders.find { it.name == item.apiName }?.mainUrl ?: ""
                                        ),
                                    resumePositionMs = item.positionMs,
                                    resumeEpisodeIndex = item.episodeIndex,
                                    resumeSeasonIndex = item.seasonIndex,
                                    resumeEpisodeTitle = item.episodeTitle
                                )
                            )
                        },
                        onDeleteClick = {
                            DesktopStorage.removeHistory(item.url, item.apiName)
                            historyItems.remove(item)
                        }
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = ShiopaStyle.SurfaceCard,
            title = { Text("Clear Watch History?", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently remove all continue-watching progress and history entries.", color = ShiopaStyle.TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        DesktopStorage.clearHistory()
                        historyItems.clear()
                        showClearDialog = false
                    },
                    shape = ShiopaStyle.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE11D48),
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = ShiopaStyle.TextMuted)
                }
            }
        )
    }
}

@Composable
private fun HistoryRowItem(
    item: HistoryItem,
    onResumeClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val progress = if (item.durationMs > 0) {
        (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
    } else 0f

    val posMin = item.positionMs / 60000
    val durMin = item.durationMs / 60000

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onResumeClick() }
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 110.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ShiopaStyle.SurfaceCard)
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val epTitle = item.episodeTitle
                if (!epTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ShiopaStyle.AccentGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Provider: ${item.apiName} • ${if (durMin > 0) "$posMin / $durMin min" else "Watched"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShiopaStyle.TextMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = ShiopaStyle.AccentGreen,
                    trackColor = Color(0x33FFFFFF)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onResumeClick,
                    shape = ShiopaStyle.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShiopaStyle.ActionPillBackground,
                        contentColor = ShiopaStyle.ActionPillText
                    )
                ) {
                    Text("▶  Resume", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, ShiopaStyle.GlassBorder, CircleShape)
                        .clickable { onDeleteClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = ShiopaStyle.TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
