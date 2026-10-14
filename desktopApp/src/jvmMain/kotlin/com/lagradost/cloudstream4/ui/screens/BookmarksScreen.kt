package com.lagradost.cloudstream4.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.core.BookmarkItem
import com.lagradost.cloudstream4.core.BookmarkStatus
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.EmptyView
import com.lagradost.cloudstream4.ui.components.MediaCard
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle

@Composable
fun BookmarksScreen(modifier: Modifier = Modifier) {
    var selectedTab by remember { mutableStateOf("All") }
    val allBookmarks = remember { mutableStateListOf<BookmarkItem>().apply { addAll(DesktopStorage.getBookmarks()) } }

    val tabs = listOf("All", "Watching", "Plan to Watch", "Completed", "On Hold", "Dropped")

    val filtered = when (selectedTab) {
        "All" -> allBookmarks
        "Watching" -> allBookmarks.filter { it.status == BookmarkStatus.WATCHING }
        "Plan to Watch" -> allBookmarks.filter { it.status == BookmarkStatus.PLAN_TO_WATCH }
        "Completed" -> allBookmarks.filter { it.status == BookmarkStatus.COMPLETED }
        "On Hold" -> allBookmarks.filter { it.status == BookmarkStatus.ON_HOLD }
        "Dropped" -> allBookmarks.filter { it.status == BookmarkStatus.DROPPED }
        else -> allBookmarks
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Shiopa Watchlist Filter Capsules
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                val isSelected = tab == selectedTab
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(if (isSelected) Color.White else Color(0xFF14141A))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Color.White else ShiopaStyle.GlassBorder,
                            shape = ShiopaStyle.PillShape
                        )
                        .clickable { selectedTab = tab }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) {
                    Text(
                        text = tab,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else ShiopaStyle.TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (filtered.isEmpty()) {
            EmptyView(
                title = "No Bookmarks in $selectedTab",
                description = "Bookmark movies and TV series from their details page to organize your watchlist.",
                actionLabel = "Discover Titles",
                onAction = { NavigationManager.navigate(NavDestination.Discover()) }
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 175.dp),
                contentPadding = PaddingValues(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filtered) { bookmark ->
                    MediaCard(
                        title = bookmark.title,
                        posterUrl = bookmark.posterUrl,
                        year = bookmark.year,
                        onClick = {
                            NavigationManager.navigate(
                                NavDestination.MediaDetails(
                                    url = bookmark.url,
                                    apiName = bookmark.apiName,
                                    title = bookmark.title,
                                    posterUrl = bookmark.posterUrl
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
