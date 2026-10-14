package com.lagradost.cloudstream4.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.core.StoredPlugin
import com.lagradost.cloudstream4.core.StoredRepository
import com.lagradost.cloudstream4.extensions.DesktopPluginManager
import com.lagradost.cloudstream4.extensions.RemotePlugin
import com.lagradost.cloudstream4.ui.components.EmptyView
import com.lagradost.cloudstream4.ui.components.LoadingView
import com.lagradost.cloudstream4.ui.theme.ShiopaStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtensionsScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(1) } // 0: Installed, 1: Marketplace

    val installedPlugins = remember {
        mutableStateListOf<StoredPlugin>().apply { addAll(DesktopStorage.getInstalledPlugins()) }
    }
    val repositories = remember {
        mutableStateListOf<StoredRepository>().apply { addAll(DesktopStorage.getRepositories()) }
    }
    val repositoryRevision by DesktopStorage.repositoryRevision.collectAsState()

    var selectedRepo by remember { mutableStateOf(repositories.firstOrNull()) }
    var isLoadingRepo by remember { mutableStateOf(false) }
    var repoLoadError by remember { mutableStateOf<String?>(null) }
    val remotePlugins = remember { mutableStateListOf<RemotePlugin>() }

    // Filters
    var selectedLanguage by remember { mutableStateOf("All") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }

    var showAddRepoDialog by remember { mutableStateOf(false) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoUrl by remember { mutableStateOf("") }
    var isAddingRepo by remember { mutableStateOf(false) }
    var addRepoError by remember { mutableStateOf<String?>(null) }

    var installingPluginId by remember { mutableStateOf<String?>(null) }

    fun refreshInstalled() {
        installedPlugins.clear()
        installedPlugins.addAll(DesktopStorage.getInstalledPlugins())
    }

    fun loadRepoPlugins(repo: StoredRepository?) {
        if (repo == null) return
        selectedRepo = repo
        isLoadingRepo = true
        repoLoadError = null
        scope.launch {
            val result = runCatching { DesktopPluginManager.fetchRepoPlugins(repo) }
            withContext(Dispatchers.Main) {
                remotePlugins.clear()
                result.onSuccess { remotePlugins.addAll(it) }
                    .onFailure { repoLoadError = it.message ?: "Could not load this repository." }
                isLoadingRepo = false
            }
        }
    }

    LaunchedEffect(selectedRepo) {
        if (selectedTab == 1 && selectedRepo != null) {
            loadRepoPlugins(selectedRepo)
        }
    }

    LaunchedEffect(repositoryRevision) {
        val latest = DesktopStorage.getRepositories()
        if (latest.map { it.url } != repositories.map { it.url }) {
            val previousUrl = selectedRepo?.url
            repositories.clear()
            repositories.addAll(latest)
            selectedRepo = repositories.firstOrNull { it.url == previousUrl } ?: repositories.firstOrNull()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ShiopaStyle.CanvasBlack)
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        // Header Row: Title & Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Extensions Marketplace",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Discover, install, and manage streaming scrapers and subtitle providers.",
                    fontSize = 13.sp,
                    color = ShiopaStyle.TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Check for Updates / Update All Button
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(ShiopaStyle.AccentMain)
                        .clickable {
                            if (installingPluginId != null) return@clickable
                            installingPluginId = "__updates__"
                            scope.launch {
                                val pending = DesktopPluginManager.checkForPluginUpdates()
                                withContext(Dispatchers.Main) {
                                    if (pending.isEmpty()) {
                                        installingPluginId = null
                                        NotificationService.success("All extensions are up to date")
                                        refreshInstalled()
                                    } else {
                                        NotificationService.info("Updating ${pending.size} extension${if (pending.size == 1) "" else "s"}...")
                                    }
                                }
                                if (pending.isNotEmpty()) {
                                    DesktopPluginManager.autoUpdatePlugins(pending)
                                    withContext(Dispatchers.Main) {
                                        installingPluginId = null
                                        refreshInstalled()
                                    }
                                }
                            }
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (installingPluginId == "__updates__") {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Updating...", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("⟳", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update All", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                // Add Repository Button
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.ControlShape)
                        .background(ShiopaStyle.SurfaceCard)
                        .border(1.dp, ShiopaStyle.GlassBorderHover, ShiopaStyle.ControlShape)
                        .clickable {
                            addRepoError = null
                            showAddRepoDialog = true
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("+", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Repository", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Extensions are third-party code and run with the same access as this app. Install only repositories and extensions you trust.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            fontSize = 12.sp,
            color = ShiopaStyle.TextMuted
        )

        // Main Tab Switcher + Quick Search Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented Tab Switcher
            Row(
                modifier = Modifier
                    .clip(ShiopaStyle.CornerLg)
                    .background(ShiopaStyle.SurfaceCard)
                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.CornerLg)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val marketBg by animateColorAsState(if (selectedTab == 1) ShiopaStyle.AccentMain else Color.Transparent)
                val marketText by animateColorAsState(if (selectedTab == 1) Color.White else ShiopaStyle.TextMuted)

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerMd)
                        .background(marketBg)
                        .clickable {
                            selectedTab = 1
                            if (selectedRepo == null && repositories.isNotEmpty()) {
                                selectedRepo = repositories.first()
                            }
                            loadRepoPlugins(selectedRepo)
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Browse Marketplace",
                        fontSize = 13.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = marketText
                    )
                }

                val installedBg by animateColorAsState(if (selectedTab == 0) ShiopaStyle.AccentMain else Color.Transparent)
                val installedText by animateColorAsState(if (selectedTab == 0) Color.White else ShiopaStyle.TextMuted)

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.CornerMd)
                        .background(installedBg)
                        .clickable {
                            selectedTab = 0
                            refreshInstalled()
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Installed Extensions",
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                            color = installedText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (selectedTab == 0) Color.White.copy(alpha = 0.2f) else Color(0xFF333344))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = installedPlugins.size.toString(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Quick live filter field
            Box(
                modifier = Modifier
                    .width(300.dp)
                    .clip(ShiopaStyle.ControlShape)
                    .background(ShiopaStyle.SurfaceCard)
                    .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.ControlShape)
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚲", fontSize = 13.sp, color = ShiopaStyle.TextSecondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 13.sp
                        ),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    "Filter by name, tag, author...",
                                    color = Color(0xFF6E6E7E),
                                    fontSize = 12.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (selectedTab == 0) {
            // INSTALLED EXTENSIONS VIEW
            if (installedPlugins.isEmpty()) {
                EmptyView(
                    title = "No Extensions Installed",
                    description = "Install streaming providers from the marketplace to start streaming movies, TV shows, and anime.",
                    actionLabel = "Browse Marketplace",
                    onAction = { selectedTab = 1 }
                )
            } else {
                val filteredInstalled = if (searchQuery.isBlank()) installedPlugins else installedPlugins.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.description?.contains(searchQuery, ignoreCase = true) == true ||
                    it.authors.any { a -> a.contains(searchQuery, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredInstalled, key = { it.internalName }) { plugin ->
                        ShiopaInstalledPluginCard(
                            plugin = plugin,
                            onToggle = { enabled ->
                                scope.launch {
                                    DesktopPluginManager.togglePlugin(plugin.internalName, enabled)
                                    refreshInstalled()
                                }
                            },
                            onUninstall = {
                                scope.launch {
                                    DesktopPluginManager.uninstallPlugin(plugin.internalName)
                                    refreshInstalled()
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        } else {
            // MARKETPLACE VIEW

            // 1. Repository Selection Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Repository:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFAAAAAA),
                    modifier = Modifier.padding(end = 10.dp)
                )

                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(repositories) { repo ->
                        val isSelected = selectedRepo?.url == repo.url
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(if (isSelected) Color.White else Color(0xFF14141B))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color.White else Color(0x22FFFFFF),
                                    shape = ShiopaStyle.PillShape
                                )
                                .clickable { loadRepoPlugins(repo) }
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = repo.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else Color(0xFFCCCCCC)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Organized Filter Pills (Language, Category, Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Languages Row
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val languages = listOf("All", "English", "Multi", "Anime / JA", "Spanish", "French", "Hindi", "German")
                    items(languages) { lang ->
                        val isSel = selectedLanguage == lang
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(if (isSel) Color(0xFF2E2E3E) else Color(0xFF101015))
                                .border(1.dp, if (isSel) Color(0x66FFFFFF) else Color(0x18FFFFFF), ShiopaStyle.PillShape)
                            .clickable { selectedLanguage = lang }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = lang,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else Color(0xFF888899)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Content Types Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categories = listOf("All", "Movies & TV", "Anime", "Cartoons")
                    items(categories) { cat ->
                        val isSel = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(if (isSel) Color(0xFF2E2E3E) else Color(0xFF101015))
                                .border(1.dp, if (isSel) Color(0x66FFFFFF) else Color(0x18FFFFFF), ShiopaStyle.PillShape)
                            .clickable { selectedCategory = cat }
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) Color.White else Color(0xFF888899)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Provider List
            if (isLoadingRepo) {
                LoadingView(message = "Fetching repository manifest...")
            } else if (remotePlugins.isEmpty()) {
                EmptyView(
                    title = if (repoLoadError == null) "No Plugins Available" else "Repository download failed",
                    description = repoLoadError ?: "Could not find plugins in the selected repository.",
                    actionLabel = "Retry",
                    onAction = { loadRepoPlugins(selectedRepo) }
                )
            } else {
                // Apply all filters
                val filtered = remotePlugins.filter { plugin ->
                    // 1. Search Query
                    val matchesSearch = searchQuery.isBlank() ||
                        plugin.name.contains(searchQuery, ignoreCase = true) ||
                        plugin.description?.contains(searchQuery, ignoreCase = true) == true ||
                        plugin.language?.contains(searchQuery, ignoreCase = true) == true ||
                        plugin.authors.any { a -> a.contains(searchQuery, ignoreCase = true) }

                    // 2. Language Filter
                    val matchesLang = when (selectedLanguage) {
                        "All" -> true
                        "English" -> plugin.language?.contains("en", ignoreCase = true) == true
                        "Multi" -> plugin.language?.contains("multi", ignoreCase = true) == true
                        "Anime / JA" -> plugin.language?.contains("ja", ignoreCase = true) == true ||
                                        plugin.name.contains("anime", ignoreCase = true) ||
                                        plugin.tvTypes?.any { it.contains("anime", ignoreCase = true) } == true
                        "Spanish" -> plugin.language?.contains("es", ignoreCase = true) == true
                        "French" -> plugin.language?.contains("fr", ignoreCase = true) == true
                        "Hindi" -> plugin.language?.contains("hi", ignoreCase = true) == true
                        "German" -> plugin.language?.contains("de", ignoreCase = true) == true
                        else -> true
                    }

                    // 3. Category Filter
                    val matchesCat = when (selectedCategory) {
                        "All" -> true
                        "Movies & TV" -> plugin.tvTypes.isNullOrEmpty() || plugin.tvTypes.any {
                            it.contains("movie", ignoreCase = true) || it.contains("tv", ignoreCase = true)
                        }
                        "Anime" -> plugin.tvTypes?.any { it.contains("anime", ignoreCase = true) } == true ||
                                   plugin.name.contains("anime", ignoreCase = true)
                        "Cartoons" -> plugin.tvTypes?.any { it.contains("cartoon", ignoreCase = true) } == true
                        else -> true
                    }

                    matchesSearch && matchesLang && matchesCat
                }

                // Header with results count
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available Extensions (${filtered.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFAAAAAA)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.internalName }) { plugin ->
                        val installed = installedPlugins.find { it.internalName == plugin.internalName }
                        val isInstalled = installed != null
                        val hasUpdate = installed != null && plugin.version > installed.version
                        val isCurrentlyInstalling = installingPluginId == plugin.internalName

                        ShiopaRemotePluginCard(
                            plugin = plugin,
                            isInstalled = isInstalled,
                            hasUpdate = hasUpdate,
                            installedVersion = installed?.version,
                            isInstalling = isCurrentlyInstalling,
                            onInstall = {
                                if (installingPluginId != null) return@ShiopaRemotePluginCard
                                installingPluginId = plugin.internalName
                                scope.launch {
                                    val success = DesktopPluginManager.installPluginFromRemote(plugin)
                                    withContext(Dispatchers.Main) {
                                        installingPluginId = null
                                        if (success) {
                                            refreshInstalled()
                                        }
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showAddRepoDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAddingRepo) showAddRepoDialog = false },
            title = {
                Text("Add Extension Repository", fontWeight = FontWeight.Bold, color = ShiopaStyle.TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Enter a repository URL, GitHub repository link, or preset shortcode ('phisher', 'official', 'megarepo').",
                        fontSize = 12.5.sp,
                        color = ShiopaStyle.TextSecondary
                    )

                    // Quick presets pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Presets:", fontSize = 11.5.sp, color = ShiopaStyle.TextMuted)

                        QuickPresetPill("Phisher (84+ Extensions)") {
                            newRepoUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"
                            newRepoName = "Phisher Repo"
                        }

                        QuickPresetPill("Official") {
                            newRepoUrl = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"
                            newRepoName = "CloudStream Official"
                        }

                        QuickPresetPill("MegaRepo") {
                            newRepoUrl = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json"
                            newRepoName = "Mega Repository"
                        }
                    }

                    OutlinedTextField(
                        value = newRepoUrl,
                        onValueChange = {
                            newRepoUrl = it
                            addRepoError = null
                        },
                        label = { Text("Repository URL or Shortcode") },
                        placeholder = { Text("e.g. phisher, cspr, or https://raw.../repo.json") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = ShiopaStyle.GlassBorder
                        )
                    )

                    OutlinedTextField(
                        value = newRepoName,
                        onValueChange = { newRepoName = it },
                        label = { Text("Repository Name (Optional)") },
                        placeholder = { Text("Leave blank to auto-detect") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = ShiopaStyle.GlassBorder
                        )
                    )

                    if (addRepoError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22EF4444))
                                .border(1.dp, Color(0x66EF4444), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = addRepoError!!,
                                color = Color(0xFFFF6B6B),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(if (isAddingRepo) Color(0xFF555555) else Color.White)
                        .clickable(enabled = !isAddingRepo) {
                            if (newRepoUrl.isBlank()) {
                                addRepoError = "Please enter a repository URL or shortcode."
                                return@clickable
                            }
                            isAddingRepo = true
                            addRepoError = null
                            scope.launch {
                                val result = DesktopPluginManager.resolveAndAddRepo(newRepoUrl, newRepoName)
                                withContext(Dispatchers.Main) {
                                    isAddingRepo = false
                                    if (result.isSuccess) {
                                        val repo = result.getOrThrow()
                                        repositories.clear()
                                        repositories.addAll(DesktopStorage.getRepositories())
                                        selectedRepo = repo
                                        loadRepoPlugins(repo)
                                        showAddRepoDialog = false
                                        newRepoName = ""
                                        newRepoUrl = ""
                                        NotificationService.success("Added ${repo.name} successfully")
                                    } else {
                                        addRepoError = "Could not reach repository: ${result.exceptionOrNull()?.message ?: "Check URL"}"
                                    }
                                }
                            }
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 20.dp, vertical = 9.dp)
                ) {
                    if (isAddingRepo) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verifying...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    } else {
                        Text("Add Repository", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddRepoDialog = false },
                    enabled = !isAddingRepo
                ) {
                    Text("Cancel", color = ShiopaStyle.TextSecondary)
                }
            },
            containerColor = Color(0xFF14141A),
            shape = ShiopaStyle.CardLargeShape
        )
    }
}

@Composable
private fun QuickPresetPill(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(ShiopaStyle.PillShape)
            .background(Color(0xFF1F1F28))
            .border(1.dp, ShiopaStyle.GlassBorder, ShiopaStyle.PillShape)
            .clickable(onClick = onClick)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, fontSize = 10.5.sp, color = ShiopaStyle.TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ShiopaInstalledPluginCard(
    plugin: StoredPlugin,
    onToggle: (Boolean) -> Unit,
    onUninstall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111116),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0xFF1D1D26))
                            .border(1.dp, Color(0x22FFFFFF), ShiopaStyle.PillShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${plugin.version}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA)
                        )
                    }
                }

                val desc = plugin.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        fontSize = 12.5.sp,
                        color = Color(0xFF9E9EAF),
                        lineHeight = 17.sp
                    )
                }

                if (plugin.authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "By ${plugin.authors.joinToString(", ")}",
                        fontSize = 11.sp,
                        color = Color(0xFF6E6E7E)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Switch(
                    checked = plugin.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ShiopaStyle.AccentGreen,
                        uncheckedThumbColor = ShiopaStyle.TextMuted,
                        uncheckedTrackColor = Color(0xFF22222C)
                    )
                )

                Box(
                    modifier = Modifier
                        .clip(ShiopaStyle.PillShape)
                        .background(Color(0xFF1A1A22))
                        .border(1.dp, Color(0x22FFFFFF), ShiopaStyle.PillShape)
                        .clickable(onClick = onUninstall)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Uninstall",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShiopaRemotePluginCard(
    plugin: RemotePlugin,
    isInstalled: Boolean,
    hasUpdate: Boolean = false,
    installedVersion: Int? = null,
    isInstalling: Boolean = false,
    onInstall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF111116),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x1AFFFFFF))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plugin.name,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0xFF1D1D26))
                            .border(1.dp, Color(0x22FFFFFF), ShiopaStyle.PillShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "v${plugin.version}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFAAAAAA)
                        )
                    }

                    if (plugin.language != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(Color(0x2206B6D4))
                                .border(1.dp, Color(0x4406B6D4), ShiopaStyle.PillShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = plugin.language.uppercase(),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ShiopaStyle.AccentCyan
                            )
                        }
                    }

                    if (!plugin.tvTypes.isNullOrEmpty()) {
                        for (type in plugin.tvTypes.take(2)) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(ShiopaStyle.PillShape)
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), ShiopaStyle.PillShape)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = type.uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB0B0C0)
                                )
                            }
                        }
                    }
                }

                val desc = plugin.description
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        fontSize = 12.5.sp,
                        color = Color(0xFF9E9EAF),
                        lineHeight = 17.sp
                    )
                }

                if (plugin.authors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "By ${plugin.authors.joinToString(", ")}",
                        fontSize = 11.sp,
                        color = Color(0xFF6E6E7E)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            when {
                isInstalling -> {
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color(0xFF22222C))
                            .border(1.dp, ShiopaStyle.GlassBorderActive, ShiopaStyle.PillShape)
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Installing...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                isInstalled -> {
                    if (hasUpdate) {
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(Color(0xFFF59E0B))
                                .clickable(onClick = onInstall)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .padding(horizontal = 18.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "Update v$installedVersion → v${plugin.version}",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(ShiopaStyle.PillShape)
                                .background(Color(0x2210B981))
                                .border(1.dp, Color(0x6610B981), ShiopaStyle.PillShape)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "✓ Installed",
                                color = ShiopaStyle.AccentGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .clip(ShiopaStyle.PillShape)
                            .background(Color.White)
                            .clickable(onClick = onInstall)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .padding(horizontal = 22.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Install",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }
            }
        }
    }
}
