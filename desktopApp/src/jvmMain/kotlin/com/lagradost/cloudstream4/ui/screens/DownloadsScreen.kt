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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.AppSettings
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.downloads.DesktopDownloadManager
import com.lagradost.cloudstream4.downloads.DownloadState
import com.lagradost.cloudstream4.downloads.DownloadTask
import com.lagradost.cloudstream4.navigation.NavDestination
import com.lagradost.cloudstream4.navigation.NavigationManager
import com.lagradost.cloudstream4.ui.components.EmptyView
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser

import androidx.compose.foundation.border
import androidx.compose.material3.ButtonDefaults
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle

@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    onSettingsChanged: (AppSettings) -> Unit = {}
) {
    var downloadsDirectory by remember { mutableStateOf(DesktopStorage.settings.downloadsDirectory) }
    val tasks = DesktopDownloadManager.tasks
    val activeTasks = tasks.filter { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PAUSED || it.state == DownloadState.QUEUED }
    val completedTasks = tasks.filter { it.state == DownloadState.COMPLETED }

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
                    text = "Downloads",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "${activeTasks.size} Active • ${completedTasks.size} Completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = ShiopaStyle.TextMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Download location bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ShiopaStyle.GlassSurface)
                .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DESTINATION FOLDER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ShiopaStyle.TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = downloadsDirectory,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                            .clickable {
                                try {
                                    val initialDir = File(downloadsDirectory).takeIf { it.isDirectory }
                                        ?: AppPaths.defaultDownloadsDir
                                    val chooser = JFileChooser(initialDir).apply {
                                        dialogTitle = "Choose download folder"
                                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                                        isAcceptAllFileFilterUsed = false
                                    }
                                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                        val selected = chooser.selectedFile
                                        if (selected == null) {
                                            NotificationService.error("No folder selected", null)
                                        } else if (selected.isFile) {
                                            NotificationService.error("Selected path is a file, not a folder", selected.absolutePath)
                                        } else if (!selected.exists() && !selected.mkdirs()) {
                                            NotificationService.error("Could not create folder", selected.absolutePath)
                                        } else if (!selected.isDirectory) {
                                            NotificationService.error("Selected path is not a folder", selected.absolutePath)
                                        } else {
                                            downloadsDirectory = selected.absolutePath
                                            val updated = DesktopStorage.settings.copy(downloadsDirectory = downloadsDirectory)
                                            DesktopStorage.saveSettings(updated)
                                            onSettingsChanged(updated)
                                            NotificationService.success("Download folder updated")
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    NotificationService.error("Could not change download folder", e.message)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Change", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0x1AFFFFFF))
                            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                            .clickable {
                                try {
                                    val dir = File(downloadsDirectory)
                                    if (!dir.exists()) dir.mkdirs()
                                    if (dir.isDirectory) Desktop.getDesktop().open(dir)
                                    else NotificationService.error("Download folder is not available", dir.absolutePath)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    NotificationService.error("Could not open download folder", e.message)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Open Folder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (tasks.isEmpty()) {
            EmptyView(
                title = "No Downloads",
                description = "Downloaded movies and episodes will appear here for seamless offline viewing."
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (activeTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Active Downloads (${activeTasks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    items(activeTasks, key = { it.id }) { task ->
                        ActiveDownloadRow(task)
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                if (completedTasks.isNotEmpty()) {
                    item {
                        Text(
                            text = "Completed (${completedTasks.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    items(completedTasks, key = { it.id }) { task ->
                        CompletedDownloadRow(task)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadRow(task: DownloadTask) {
    val downloadedMB = task.downloadedBytes / (1024 * 1024)
    val totalMB = task.totalBytes / (1024 * 1024)
    val speedMBs = task.speedBytesPerSec.toFloat() / (1024 * 1024)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val epTitle = task.episodeTitle
                    if (!epTitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = epTitle,
                            fontSize = 13.sp,
                            color = ShiopaStyle.AccentGreen
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (task.state == DownloadState.DOWNLOADING) {
                        Button(
                            onClick = { DesktopDownloadManager.pauseDownload(task) },
                            shape = ShiopaStyle.PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0x26FFFFFF),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Pause", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else if (task.state == DownloadState.PAUSED) {
                        Button(
                            onClick = { DesktopDownloadManager.resumeDownload(task) },
                            shape = ShiopaStyle.PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ShiopaStyle.ActionPillBackground,
                                contentColor = ShiopaStyle.ActionPillText
                            )
                        ) {
                            Text("Resume", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0x1AE11D48))
                            .border(1.dp, Color(0x33E11D48), ShiopaStyle.PillShape)
                            .clickable { DesktopDownloadManager.cancelDownload(task) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFFB7185), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            LinearProgressIndicator(
                progress = { task.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = ShiopaStyle.AccentGreen,
                trackColor = Color(0x33FFFFFF)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${(task.progress * 100).toInt()}% • $downloadedMB MB / ${if (totalMB > 0) "$totalMB MB" else "Unknown"}",
                    fontSize = 12.sp,
                    color = ShiopaStyle.TextMuted
                )

                if (task.state == DownloadState.DOWNLOADING && speedMBs > 0) {
                    Text(
                        text = "${"%.1f".format(speedMBs)} MB/s • ${task.etaSeconds}s remaining",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ShiopaStyle.AccentGreen
                    )
                } else {
                    Text(
                        text = task.state.name,
                        fontSize = 12.sp,
                        color = ShiopaStyle.TextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletedDownloadRow(task: DownloadTask) {
    val totalMB = task.totalBytes / (1024 * 1024)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val epTitle = task.episodeTitle
                if (!epTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = epTitle,
                        fontSize = 13.sp,
                        color = ShiopaStyle.AccentGreen
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Offline ready • ${if (totalMB > 0) "$totalMB MB" else "File ready"}",
                    fontSize = 12.sp,
                    color = ShiopaStyle.AccentGreen
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val file = File(task.destinationPath)
                        NavigationManager.navigate(
                            NavDestination.Player(
                                url = file.toURI().toString(),
                                title = task.title,
                                episodeTitle = task.episodeTitle,
                                mediaUrl = file.toURI().toString()
                            )
                        )
                    },
                    shape = ShiopaStyle.PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ShiopaStyle.ActionPillBackground,
                        contentColor = ShiopaStyle.ActionPillText
                    )
                ) {
                    Text("▶  Play Offline", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(Color(0x14FFFFFF))
                        .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                        .clickable {
                            DesktopDownloadManager.removeCompleted(task, deleteFile = true)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Delete", color = ShiopaStyle.TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
