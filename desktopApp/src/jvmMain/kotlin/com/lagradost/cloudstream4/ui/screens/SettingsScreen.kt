package com.lagradost.cloudstream4.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.AppSettings
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.playback.DesktopPlayer
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import java.awt.Desktop
import java.io.File
import javax.swing.JFileChooser

enum class SettingsSection(val title: String, val icon: String, val subtitle: String) {
    PLAYBACK("Playback & Player", "▶", "Hardware acceleration, LibVLC engine, and seek behavior"),
    SUBTITLES("Subtitles & Audio", "💬", "Subtitle typography, scale, and preferred audio streams"),
    APPEARANCE("Appearance", "✦", "Shiopa dark themes, ambient glow, and motion effects"),
    EXTENSIONS("Network & Extensions", "❖", "Plugin auto-updates, active providers, and indexing"),
    STORAGE("Downloads & Storage", "⤓", "Download path, cache cleanup, and local data directory"),
    SHORTCUTS("Keyboard Controls", "⌨", "Fast hotkeys for power playback and navigation"),
    ABOUT("About", "ℹ", "Version details, multiplatform architecture, and licenses")
}

@Composable
fun SettingsScreen(
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(SettingsSection.PLAYBACK) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(ShiopaStyle.CanvasBlack)) {
        val isCompact = maxWidth < 768.dp

        if (isCompact) {
            // Mobile/Compact Viewport Layout
            Column(modifier = Modifier.fillMaxSize()) {
                // Horizontally Scrollable Category Chips
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(SettingsSection.values()) { section ->
                        val isSelected = section == selectedSection
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(
                                    if (isSelected) ShiopaStyle.ActionPillBackground
                                    else ShiopaStyle.PillChipBackground
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.White else ShiopaStyle.PillChipBorder,
                                    ShiopaStyle.PillShape
                                )
                                .clickable { selectedSection = section }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(section.icon, fontSize = 12.sp, color = if (isSelected) Color.Black else Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = section.title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.5.sp,
                                    color = if (isSelected) Color.Black else Color(0xFFD4D4E2)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = ShiopaStyle.GlassBorder)

                // Compact Settings Content List
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    renderSettingsSection(selectedSection, currentSettings, onSettingsChanged)
                }
            }
        } else {
            // Expanded Desktop Multi-Pane Layout
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Navigation Sidebar
                Box(
                    modifier = Modifier
                        .width(260.dp)
                        .fillMaxHeight()
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RectangleShape)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Preferences",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        for (section in SettingsSection.values()) {
                            val isSelected = section == selectedSection
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShiopaStyle.PillShape)
                                    .background(
                                        if (isSelected) ShiopaStyle.ActionPillBackground
                                        else Color.Transparent
                                    )
                                    .clickable { selectedSection = section }
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .padding(horizontal = 16.dp, vertical = 11.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = section.icon,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.Black else ShiopaStyle.TextMuted
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = section.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (isSelected) Color.Black else Color(0xFFE2E2EA)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                // Right Content Panel
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 40.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    renderSettingsSection(selectedSection, currentSettings, onSettingsChanged)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderSettingsSection(
    section: SettingsSection,
    currentSettings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit
) {
    item {
        SettingsHeader(title = section.title, subtitle = section.subtitle)
    }

    when (section) {
        SettingsSection.PLAYBACK -> {
            item {
                // Hardware Acceleration
                SettingsToggleRow(
                    title = "Hardware Video Acceleration",
                    subtitle = "Utilize GPU decoding (Direct3D 11 / DXVA2 / VA-API) via LibVLC for fluid 4K/HDR playback",
                    checked = currentSettings.hardwareAcceleration,
                    onCheckedChange = {
                        val s = currentSettings.copy(hardwareAcceleration = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                // Auto Play Next
                SettingsToggleRow(
                    title = "Auto-play Next Episode",
                    subtitle = "Seamlessly queue and start the subsequent episode when current video reaches 95% completion",
                    checked = currentSettings.autoPlayNextEpisode,
                    onCheckedChange = {
                        val s = currentSettings.copy(autoPlayNextEpisode = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                // Seek Seconds Card
                SettingsSliderCard(
                    title = "Default Seek Step",
                    valueDisplay = "${currentSettings.defaultSeekSeconds} seconds",
                    value = currentSettings.defaultSeekSeconds.toFloat(),
                    valueRange = 5f..60f,
                    steps = 10,
                    onValueChange = {
                        val s = currentSettings.copy(defaultSeekSeconds = it.toInt())
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                // LibVLC Status Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Media Engine Status", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                if (DesktopPlayer.isVlcAvailable) "LibVLC high-performance native core is loaded and operating normally"
                                else "LibVLC native library not detected; running in fallback mode",
                                fontSize = 12.sp,
                                color = ShiopaStyle.TextSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(if (DesktopPlayer.isVlcAvailable) Color(0x2210B981) else Color(0x22F59E0B))
                                .border(1.dp, if (DesktopPlayer.isVlcAvailable) ShiopaStyle.AccentGreen else Color(0xFFF59E0B), ShiopaStyle.PillShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (DesktopPlayer.isVlcAvailable) "● Active (LibVLC)" else "⚠ Fallback",
                                color = if (DesktopPlayer.isVlcAvailable) ShiopaStyle.AccentGreen else Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        SettingsSection.SUBTITLES -> {
            item {
                SettingsSliderCard(
                    title = "Subtitle Font Size",
                    valueDisplay = "${currentSettings.subtitleFontSize} pt",
                    value = currentSettings.subtitleFontSize.toFloat(),
                    valueRange = 14f..40f,
                    steps = 12,
                    onValueChange = {
                        val s = currentSettings.copy(subtitleFontSize = it.toInt())
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                SettingsPillSelector(
                    title = "Preferred Subtitle Language",
                    subtitle = "CloudStream will automatically select matching subtitle streams when available",
                    options = listOf("en" to "English", "es" to "Spanish", "fr" to "French", "ja" to "Japanese", "de" to "German"),
                    selectedOption = currentSettings.subtitleLanguage,
                    onSelect = {
                        val s = currentSettings.copy(subtitleLanguage = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                SettingsPillSelector(
                    title = "Preferred Audio Track Language",
                    subtitle = "Default language prioritized during multi-audio HLS/MKV playback",
                    options = listOf("en" to "English", "ja" to "Japanese (Original)", "es" to "Spanish", "fr" to "French"),
                    selectedOption = currentSettings.preferredAudioLanguage,
                    onSelect = {
                        val s = currentSettings.copy(preferredAudioLanguage = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
        }

        SettingsSection.APPEARANCE -> {
            item {
                SettingsPillSelector(
                    title = "Interface Palette",
                    subtitle = "Choose your preferred dark foundation colorway",
                    options = listOf(
                        "Black" to "Pure OLED Black",
                        "Dark" to "Deep Shiopa Navy & Purple",
                        "Light" to "Subtle Slate"
                    ),
                    selectedOption = currentSettings.themeMode,
                    onSelect = {
                        val s = currentSettings.copy(themeMode = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                SettingsToggleRow(
                    title = "Ambient Glow & Backlight",
                    subtitle = "Show soft atmospheric glow beneath active hero backdrops and elevated media cards",
                    checked = currentSettings.ambientGlow,
                    onCheckedChange = {
                        val s = currentSettings.copy(ambientGlow = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                SettingsToggleRow(
                    title = "Reduced Motion",
                    subtitle = "Minimize rapid scale transformations and animated transitions across carousels and cards",
                    checked = currentSettings.reducedMotion,
                    onCheckedChange = {
                        val s = currentSettings.copy(reducedMotion = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
        }

        SettingsSection.EXTENSIONS -> {
            item {
                SettingsToggleRow(
                    title = "Check for Extension Updates on Launch",
                    subtitle = "Automatically inspect configured CloudStream repositories and update .cs3 plugins upon startup",
                    checked = currentSettings.checkExtensionUpdatesOnStartup,
                    onCheckedChange = {
                        val s = currentSettings.copy(checkExtensionUpdatesOnStartup = it)
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Installed Providers", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            Text("${APIHolder.allProviders.size} Active", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShiopaStyle.AccentGreen)
                        }
                        Text(
                            text = "Extensions are dynamically loaded via the desktop dex2jar bytecode translator with full Android framework emulation.",
                            fontSize = 12.sp,
                            color = ShiopaStyle.TextMuted,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        SettingsSection.STORAGE -> {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Download Directory", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text(currentSettings.downloadsDirectory, fontSize = 12.5.sp, color = ShiopaStyle.TextMuted)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    try {
                                        val initialDir = File(currentSettings.downloadsDirectory).takeIf { it.isDirectory }
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
                                                val s = currentSettings.copy(downloadsDirectory = selected.absolutePath)
                                                DesktopStorage.saveSettings(s)
                                                onSettingsChanged(s)
                                                NotificationService.success("Download folder updated")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        NotificationService.error("Could not change download folder", e.message)
                                    }
                                },
                                shape = ShiopaStyle.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ShiopaStyle.ActionPillBackground,
                                    contentColor = ShiopaStyle.ActionPillText
                                )
                            ) {
                                Text("Change Folder", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = {
                                    try {
                                        val dir = File(currentSettings.downloadsDirectory)
                                        if (!dir.exists()) dir.mkdirs()
                                        if (dir.isDirectory) Desktop.getDesktop().open(dir)
                                        else NotificationService.error("Download folder is not available", dir.absolutePath)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        NotificationService.error("Could not open download folder", e.message)
                                    }
                                },
                                shape = ShiopaStyle.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x1AFFFFFF),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Open Downloads Folder", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                SettingsSliderCard(
                    title = "Concurrent Download Streams",
                    valueDisplay = "${currentSettings.maxConcurrentDownloads} tasks",
                    value = currentSettings.maxConcurrentDownloads.toFloat(),
                    valueRange = 1f..8f,
                    steps = 6,
                    onValueChange = {
                        val s = currentSettings.copy(maxConcurrentDownloads = it.toInt())
                        DesktopStorage.saveSettings(s)
                        onSettingsChanged(s)
                    }
                )
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("App Storage & Temporary Cache", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        Text("All user data is stored at ${AppPaths.appDataDir.absolutePath}", fontSize = 12.sp, color = ShiopaStyle.TextMuted)
                        for (notice in AppPaths.locationNotices) {
                            Text(notice, fontSize = 11.5.sp, color = Color(0xFFF59E0B), lineHeight = 16.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    try {
                                        Desktop.getDesktop().open(AppPaths.appDataDir)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                },
                                shape = ShiopaStyle.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x1AFFFFFF),
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Open AppData Folder", fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    try {
                                        AppPaths.cacheDir.deleteRecursively()
                                        AppPaths.cacheDir.mkdirs()
                                        NotificationService.success("Cache cleared successfully")
                                    } catch (e: Exception) {
                                        NotificationService.error("Failed to clear cache", e.message)
                                    }
                                },
                                shape = ShiopaStyle.PillShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0x1AE11D48),
                                    contentColor = Color(0xFFFB7185)
                                )
                            ) {
                                Text("Clear Image & Stream Cache", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        SettingsSection.SHORTCUTS -> {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShortcutCard("Space", "Play or pause current playback")
                    ShortcutCard("Left / Right Arrow", "Seek 10 seconds backward or forward")
                    ShortcutCard("Up / Down Arrow", "Increase or decrease playback volume (5%)")
                    ShortcutCard("M", "Mute or restore audio volume")
                    ShortcutCard("Esc", "Exit fullscreen or navigate back")
                    ShortcutCard("[ / ]", "Decrease / increase video playback rate")
                    ShortcutCard("N", "Skip to next episode in season")
                    ShortcutCard("Ctrl + K", "Open quick search bar from anywhere")
                }
            }
        }

        SettingsSection.ABOUT -> {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ShiopaStyle.GlassSurface)
                        .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(16.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE50914)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("CloudStream Desktop", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                                Text("Version 1.0.0 • Production Build", fontSize = 12.sp, color = ShiopaStyle.TextMuted)
                            }
                        }

                        HorizontalDivider(color = ShiopaStyle.GlassBorder, modifier = Modifier.padding(vertical = 4.dp))

                        Text("• Framework: Kotlin Multiplatform & Compose for Desktop (JVM 17)", fontSize = 13.sp, color = Color(0xFFD4D4E2))
                        Text("• Video Engine: Native LibVLC with direct hardware rendering", fontSize = 13.sp, color = Color(0xFFD4D4E2))
                        Text("• Extension Pipeline: CloudStream .cs3 + dex2jar runtime translator", fontSize = 13.sp, color = Color(0xFFD4D4E2))
                        Text("• UI/UX Language: Shiopa-inspired Dark-First Ambient Media Architecture", fontSize = 13.sp, color = Color(0xFFD4D4E2))

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            "Licensed under GNU General Public License v3.0. Built with pride for high-performance desktop media streaming.",
                            fontSize = 11.5.sp,
                            color = ShiopaStyle.TextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = ShiopaStyle.TextMuted
        )
        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = ShiopaStyle.GlassBorder)
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = ShiopaStyle.TextMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = ShiopaStyle.AccentGreen,
                    uncheckedThumbColor = Color(0xFFAAAAAA),
                    uncheckedTrackColor = Color(0xFF22222A)
                )
            )
        }
    }
}

@Composable
private fun SettingsSliderCard(
    title: String,
    valueDisplay: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                Text(text = valueDisplay, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ShiopaStyle.AccentGreen)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = ShiopaStyle.AccentGreen
                )
            )
        }
    }
}

@Composable
private fun SettingsPillSelector(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selectedOption: String,
    onSelect: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, fontSize = 12.sp, color = ShiopaStyle.TextMuted)
            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((key, label) in options) {
                    val isSelected = selectedOption == key
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(
                                if (isSelected) ShiopaStyle.ActionPillBackground
                                else Color(0x1AFFFFFF)
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color.White else ShiopaStyle.GlassBorder,
                                ShiopaStyle.PillShape
                            )
                            .clickable { onSelect(key) }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ShiopaStyle.ActionPillText else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCard(key: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ShiopaStyle.GlassSurface)
            .border(1.dp, ShiopaStyle.GlassBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = desc, fontSize = 13.sp, color = Color.White)
        Box(
            modifier = Modifier
                .clip(ShiopaStyle.PillShape)
                .background(Color(0x2AFFFFFF))
                .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(text = key, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
        }
    }
}
