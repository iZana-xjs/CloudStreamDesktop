package com.lagradost.cloudstream4.extensions

import com.googlecode.d2j.dex.Dex2jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.actions.VideoClickActionHolder
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.utils.extractorApis
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.DesktopTmdbProvider
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.core.StoredPlugin
import com.lagradost.cloudstream4.core.StoredRepository
import com.lagradost.cloudstream4.core.desktopJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URLClassLoader
import java.security.MessageDigest
import java.util.zip.ZipInputStream

@Serializable
data class RepoIndex(
    val name: String? = null,
    val description: String? = null,
    val manifestVersion: Int = 1,
    val pluginLists: List<String> = emptyList()
)

@Serializable
data class RemotePlugin(
    val url: String,
    val name: String,
    val internalName: String,
    val version: Int = 1,
    val authors: List<String> = emptyList(),
    val description: String? = null,
    val repositoryUrl: String? = null,
    val tvTypes: List<String>? = null,
    val language: String? = null,
    val iconUrl: String? = null,
    val fileSize: Long? = null,
    val fileHash: String? = null,
    val status: Int = 1
)

/** Reject insecure or credential-bearing URLs supplied by extension repositories. */
fun requireTrustedHttpsUrl(rawUrl: String): String {
    val uri = java.net.URI(rawUrl.trim())
    require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
        "Extension repositories and downloads must use HTTPS."
    }
    require(uri.userInfo.isNullOrBlank()) {
        "Repository URLs must not embed usernames or passwords."
    }
    return uri.toASCIIString()
}

/** Keep remote plugin names from escaping the plugin storage/cache directories. */
fun isSafePluginInternalName(value: String): Boolean =
    value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._ -]{0,127}")) &&
        ".." !in value && !value.endsWith('.') && !value.endsWith(' ')

/** Resolve repository entries that sometimes contain unescaped spaces in filenames. */
internal fun resolveRepositoryResourceUrl(baseUrl: String, rawUrl: String): String {
    val sanitized = rawUrl.trim().replace(" ", "%20")
    val resolved = java.net.URI(baseUrl).resolve(java.net.URI(sanitized)).toASCIIString()
    return requireTrustedHttpsUrl(resolved)
}

object DesktopPluginManager {
    private const val MAX_PLUGIN_ARCHIVE_BYTES = 100L * 1024L * 1024L
    val pluginsState = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Plugins needing a newer version or a same-version archive refresh. */
    val availableUpdates = kotlinx.coroutines.flow.MutableStateFlow<List<PluginUpdateInfo>>(emptyList())
    var lastUpdateCheckMs: Long = 0L
        private set

    data class PluginUpdateInfo(
        val installed: StoredPlugin,
        val remote: RemotePlugin
    )

    private val loadedPlugins = mutableMapOf<String, BasePlugin>()
    private val classLoaders = mutableMapOf<String, URLClassLoader>()

    fun notifyPluginsChanged() {
        pluginsState.value += 1
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        // Register default TMDB metadata provider
        if (APIHolder.allProviders.none { it.name == "TMDB" }) {
            try {
                val tmdb = DesktopTmdbProvider()
                tmdb.name = "TMDB"
                APIHolder.allProviders.add(tmdb)
                APIHolder.addPluginMapping(tmdb)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }

        // Load all saved, enabled plugins
        val savedPlugins = DesktopStorage.getInstalledPlugins()
        // Older builds could persist absolute paths into the legacy D:\ data directory.
        // Prefer the migrated, app-owned archive when present and repair the record so
        // subsequent starts and enable/disable actions use the same copy.
        val installed = savedPlugins.map { plugin ->
            val managedFile = managedPluginFile(plugin.internalName)
            if (managedFile?.isFile == true && !samePath(plugin.filePath, managedFile)) {
                plugin.copy(filePath = managedFile.absolutePath)
            } else plugin
        }
        if (installed != savedPlugins) DesktopStorage.saveInstalledPlugins(installed)
        for (plugin in installed) {
            if (plugin.enabled) {
                try {
                    val file = resolveInstalledArchive(plugin)
                    if (file.exists()) {
                        loadPluginFromFile(file, plugin.internalName)
                    }
                } catch (e: Throwable) {
                    println("Failed to load plugin ${plugin.internalName}: ${e.message}")
                }
            }
        }
        notifyPluginsChanged()
    }

    fun normalizeRepoUrl(raw: String): String {
        var url = raw.trim()
        if (url.startsWith("cloudstreamrepo://")) {
            url = "https://" + url.removePrefix("cloudstreamrepo://")
        }
        if (url.startsWith("cs.repo://")) {
            url = "https://" + url.removePrefix("cs.repo://")
        }

        when (url.lowercase()) {
            "phisher", "phisher98" -> return "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json"
            "official", "cspr", "0094" -> return "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json"
            "mega", "megarepo", "3737" -> return "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json"
        }

        // Handle GitHub web URLs: https://github.com/owner/repo
        val ghRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/?$""")
        val ghMatch = ghRegex.matchEntire(url)
        if (ghMatch != null) {
            val (owner, repo) = ghMatch.destructured
            return "https://raw.githubusercontent.com/$owner/$repo/refs/heads/builds/repo.json"
        }

        val ghTreeRegex = Regex("""^https?://github\.com/([^/]+)/([^/]+)/tree/([^/]+)/?(.*)$""")
        val ghTreeMatch = ghTreeRegex.matchEntire(url)
        if (ghTreeMatch != null) {
            val (owner, repo, branch, path) = ghTreeMatch.destructured
            val cleanPath = if (path.isBlank() || path == "/") "repo.json" else path.removePrefix("/")
            return "https://raw.githubusercontent.com/$owner/$repo/$branch/$cleanPath"
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }

        if (!url.endsWith(".json")) {
            url = if (url.endsWith("/")) "${url}repo.json" else "$url/repo.json"
        }
        return url
    }

    private fun getCandidateUrls(primaryUrl: String): List<String> {
        val list = mutableListOf(primaryUrl)
        if (primaryUrl.contains("raw.githubusercontent.com")) {
            if (primaryUrl.contains("/refs/heads/builds/")) {
                list.add(primaryUrl.replace("/refs/heads/builds/", "/builds/"))
                list.add(primaryUrl.replace("/refs/heads/builds/", "/master/"))
                list.add(primaryUrl.replace("/refs/heads/builds/", "/main/"))
            } else if (primaryUrl.contains("/builds/")) {
                list.add(primaryUrl.replace("/builds/", "/refs/heads/builds/"))
                list.add(primaryUrl.replace("/builds/", "/master/"))
                list.add(primaryUrl.replace("/builds/", "/main/"))
            } else if (primaryUrl.contains("/master/")) {
                list.add(primaryUrl.replace("/master/", "/builds/"))
                list.add(primaryUrl.replace("/master/", "/refs/heads/builds/"))
                list.add(primaryUrl.replace("/master/", "/main/"))
            }
        }
        return list.distinct()
    }

    suspend fun resolveAndAddRepo(inputUrl: String, customName: String? = null): Result<StoredRepository> = withContext(Dispatchers.IO) {
        val normalized = runCatching { requireTrustedHttpsUrl(normalizeRepoUrl(inputUrl)) }
            .getOrElse { return@withContext Result.failure(it) }
        val candidates = getCandidateUrls(normalized)

        var lastError: Throwable? = null
        for (candidate in candidates) {
            try {
                requireTrustedHttpsUrl(candidate)
                val repoResponse = app.get(candidate)
                requireTrustedHttpsUrl(repoResponse.okhttpResponse.request.url.toString())
                val resp = repoResponse.text
                if (resp.isNotBlank() && (resp.contains("\"name\"") || resp.contains("\"pluginLists\"") || resp.trimStart().startsWith("["))) {
                    var parsedName: String? = null
                    var parsedDesc: String? = null
                    if (resp.contains("\"pluginLists\"")) {
                        try {
                            val repoIndex = desktopJson.decodeFromString<RepoIndex>(resp)
                            parsedName = repoIndex.name
                            parsedDesc = repoIndex.description
                        } catch (_: Throwable) {}
                    }

                    val finalName = when {
                        !customName.isNullOrBlank() -> customName.trim()
                        !parsedName.isNullOrBlank() -> parsedName.trim()
                        candidate.contains("phisher") -> "Phisher Repo"
                        candidate.contains("recloudstream/extensions") -> "CloudStream Official"
                        candidate.contains("MegaRepo") -> "Mega Repository"
                        else -> candidate.substringAfter("raw.githubusercontent.com/").substringBefore("/").ifBlank { "Community Repo" }
                    }

                    val stored = StoredRepository(
                        name = finalName,
                        url = candidate,
                        description = parsedDesc
                    )
                    DesktopStorage.saveRepository(stored)
                    return@withContext Result.success(stored)
                }
            } catch (e: Throwable) {
                lastError = e
            }
        }
        Result.failure(lastError ?: Exception("Could not fetch a valid repository manifest from $inputUrl"))
    }

    suspend fun fetchRepoPlugins(repo: StoredRepository): List<RemotePlugin> = withContext(Dispatchers.IO) {
        val result = mutableListOf<RemotePlugin>()
        val candidates = getCandidateUrls(requireTrustedHttpsUrl(repo.url))

        var fetchedResponse: String? = null
        var workingUrl = repo.url
        var manifestError: Throwable? = null

        for (candidate in candidates) {
            try {
                requireTrustedHttpsUrl(candidate)
                val repoResponse = app.get(candidate)
                requireTrustedHttpsUrl(repoResponse.okhttpResponse.request.url.toString())
                val resp = repoResponse.text
                if (resp.isNotBlank() && (resp.contains("\"pluginLists\"") || resp.trimStart().startsWith("["))) {
                    fetchedResponse = resp
                    workingUrl = candidate
                    break
                }
            } catch (e: Throwable) { manifestError = e }
        }

        if (fetchedResponse == null) {
            throw IllegalStateException(
                "Could not load the repository plugin list. Check the repository URL and connection${manifestError?.message?.let { ": $it" } ?: "."}",
                manifestError
            )
        }

        try {
            if (fetchedResponse.contains("\"pluginLists\"")) {
                val repoIndex = desktopJson.decodeFromString<RepoIndex>(fetchedResponse)
                if (repoIndex.pluginLists.isEmpty()) throw IllegalStateException("The repository manifest contains no plugin lists")
                for (rawListUrl in repoIndex.pluginLists) {
                    try {
                        val resolvedListUrl = resolveRepositoryResourceUrl(workingUrl, rawListUrl)
                        val listResponse = app.get(resolvedListUrl)
                        requireTrustedHttpsUrl(listResponse.okhttpResponse.request.url.toString())
                        val pluginsJson = listResponse.text
                        val plugins = desktopJson.decodeFromString<List<RemotePlugin>>(pluginsJson)
                        for (p in plugins) {
                            runCatching {
                                val absolutePluginUrl = resolveRepositoryResourceUrl(resolvedListUrl, p.url)
                                result.add(p.copy(url = absolutePluginUrl))
                            }.onFailure { error ->
                                manifestError = error
                                println("Skipping invalid plugin URL for ${p.internalName}: ${error.message}")
                            }
                        }
                    } catch (e: Throwable) {
                        manifestError = e
                    }
                }
                if (result.isEmpty() && manifestError != null) {
                    throw IllegalStateException("Could not load any plugin list from ${repo.name}: ${manifestError.message}", manifestError)
                }
            } else {
                val plugins = desktopJson.decodeFromString<List<RemotePlugin>>(fetchedResponse)
                for (p in plugins) {
                    runCatching {
                        val absolutePluginUrl = resolveRepositoryResourceUrl(workingUrl, p.url)
                        result.add(p.copy(url = absolutePluginUrl))
                    }.onFailure { error ->
                        manifestError = error
                        println("Skipping invalid plugin URL for ${p.internalName}: ${error.message}")
                    }
                }
            }
        } catch (e: Throwable) {
            throw IllegalStateException("Could not parse repository ${repo.name}: ${e.message}", e)
        }
        result
    }

    suspend fun installPluginFromRemote(remote: RemotePlugin): Boolean = installPlugin(remote, null)

    suspend fun installPlugin(
        remote: RemotePlugin,
        repo: StoredRepository? = null,
        enabledAfterInstall: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isSafePluginInternalName(remote.internalName)) {
            NotificationService.error("The repository returned an invalid extension identifier.")
            return@withContext false
        }
        val downloadUrl = runCatching { requireTrustedHttpsUrl(remote.url) }
            .getOrElse {
                NotificationService.error(it.message ?: "The extension download URL is not secure.")
                return@withContext false
            }
        val pluginFile = File(AppPaths.pluginsDir, "${remote.internalName}.cs3")
        val downloadFile = File(AppPaths.pluginsDir, "${remote.internalName}.download.tmp")
        val backupFile = File(AppPaths.pluginsDir, "${remote.internalName}.previous.tmp")
        var previouslyLoaded = false
        var downloadedVersionLoaded = false
        try {
            NotificationService.info("Downloading ${remote.name}...")

            // Stage the download so a failed update never overwrites a working plugin.
            val bytes = app.get(downloadUrl).okhttpResponse.use { response ->
                requireTrustedHttpsUrl(response.request.url.toString())
                if (!response.isSuccessful) {
                    throw IllegalStateException("The plugin server returned HTTP ${response.code}.")
                }
                val body = response.body ?: throw IllegalStateException("The plugin download had no response body.")
                if (body.contentLength() > MAX_PLUGIN_ARCHIVE_BYTES) {
                    throw IllegalStateException("The extension archive is larger than the 100 MB safety limit.")
                }
                body.byteStream().use { input ->
                    ByteArrayOutputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > MAX_PLUGIN_ARCHIVE_BYTES) {
                                throw IllegalStateException("The extension archive is larger than the 100 MB safety limit.")
                            }
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            }
            if (bytes.isEmpty()) throw IllegalStateException("The repository returned an empty plugin file")
            downloadFile.writeBytes(bytes)
            java.util.zip.ZipFile(downloadFile).use { archive ->
                if (archive.getEntry("manifest.json") == null || archive.getEntry("classes.dex") == null) {
                    throw IllegalStateException("The download is not a valid CloudStream plugin archive")
                }
            }

            // Verify hash if provided
            if (!remote.fileHash.isNullOrBlank()) {
                val digest = MessageDigest.getInstance("SHA-256")
                val hashHex = digest.digest(bytes).joinToString("") { "%02x".format(it) }
                val expected = remote.fileHash.replace("sha256-", "").trim()
                if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) {
                    throw IllegalStateException("The repository supplied an invalid SHA-256 checksum.")
                }
                if (!expected.equals(hashHex, ignoreCase = true)) {
                    throw IllegalStateException("The extension checksum does not match repository metadata; installation was stopped.")
                }
            }

            if (backupFile.exists()) backupFile.delete()
            previouslyLoaded = loadedPlugins.containsKey(remote.internalName)
            if (previouslyLoaded) unloadPlugin(remote.internalName)
            if (pluginFile.exists()) moveReplacing(pluginFile, backupFile)
            moveReplacing(downloadFile, pluginFile)

            // Convert and load
            var loadError: String? = null
            val success = if (enabledAfterInstall) {
                loadPluginFromFile(pluginFile, remote.internalName) { loadError = it }
            } else {
                // Keep a user's disabled plugin disabled; don't execute its code just
                // to replace its archive. ZIP/manifest/DEX presence was already checked.
                true
            }
            if (success) {
                downloadedVersionLoaded = enabledAfterInstall
                DesktopStorage.savePlugin(
                    StoredPlugin(
                        internalName = remote.internalName,
                        name = remote.name,
                        version = remote.version,
                        filePath = pluginFile.absolutePath,
                        url = remote.url,
                        enabled = enabledAfterInstall,
                        authors = remote.authors,
                        description = remote.description,
                        iconUrl = remote.iconUrl
                    )
                )
                backupFile.delete()
                NotificationService.success("Installed ${remote.name} successfully")
                notifyPluginsChanged()
                return@withContext true
            } else {
                if (backupFile.exists()) {
                    pluginFile.delete()
                    moveReplacing(backupFile, pluginFile)
                } else {
                    pluginFile.delete()
                }
                if (previouslyLoaded && pluginFile.exists()) {
                    loadPluginFromFile(pluginFile, remote.internalName)
                }
                NotificationService.error(
                    "Failed to initialize plugin ${remote.name}",
                    loadError ?: "The plugin loader did not return a diagnostic. Check the application log."
                )
                return@withContext false
            }
        } catch (e: Throwable) {
            downloadFile.delete()
            if (downloadedVersionLoaded) unloadPlugin(remote.internalName)
            if (backupFile.exists()) {
                pluginFile.delete()
                runCatching { moveReplacing(backupFile, pluginFile) }
            } else if (downloadedVersionLoaded) {
                pluginFile.delete()
            }
            if (previouslyLoaded && pluginFile.exists() && !loadedPlugins.containsKey(remote.internalName)) {
                runCatching { loadPluginFromFile(pluginFile, remote.internalName) }
            }
            e.printStackTrace()
            NotificationService.error(
                "Failed to install ${remote.name}",
                "${e::class.simpleName}: ${e.message ?: "No error details"}"
            )
            return@withContext false
        }
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(
                source.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    suspend fun uninstallPlugin(internalName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            unloadPlugin(internalName)
            val cs3File = File(AppPaths.pluginsDir, "$internalName.cs3")
            if (cs3File.exists()) cs3File.delete()
            val jarFile = File(AppPaths.pluginsCacheDir, "$internalName.jar")
            if (jarFile.exists()) jarFile.delete()

            DesktopStorage.removePlugin(internalName)
            NotificationService.info("Uninstalled $internalName")
            notifyPluginsChanged()
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            NotificationService.error("Error uninstalling $internalName", e.message)
            false
        }
    }

    suspend fun togglePlugin(internalName: String, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val plugin = DesktopStorage.getInstalledPlugins().find { it.internalName == internalName } ?: return@withContext false
        if (enable) {
            val file = resolveInstalledArchive(plugin)
            if (file.exists() && loadPluginFromFile(file, internalName)) {
                DesktopStorage.savePlugin(plugin.copy(enabled = true))
                NotificationService.success("Enabled ${plugin.name}")
                notifyPluginsChanged()
                true
            } else {
                NotificationService.error("Failed to enable ${plugin.name}")
                false
            }
        } else {
            unloadPlugin(internalName)
            DesktopStorage.savePlugin(plugin.copy(enabled = false))
            NotificationService.info("Disabled ${plugin.name}")
            notifyPluginsChanged()
            true
        }
    }

    /**
     * Compare installed plugins against every configured repository and return
     * entries where a newer version is advertised or the installed archive's
     * checksum differs from the repository's checksum for the same version.
     * Failures for individual repos are skipped so one dead mirror never
     * blocks updates from the others.
     */
    suspend fun checkForPluginUpdates(): List<PluginUpdateInfo> = withContext(Dispatchers.IO) {
        val installed = DesktopStorage.getInstalledPlugins()
        if (installed.isEmpty()) {
            availableUpdates.value = emptyList()
            lastUpdateCheckMs = System.currentTimeMillis()
            return@withContext emptyList()
        }
        val repos = DesktopStorage.getRepositories()
        val latestById = mutableMapOf<String, RemotePlugin>()
        for (repo in repos) {
            try {
                val remotes = fetchRepoPlugins(repo)
                for (remote in remotes) {
                    val existing = latestById[remote.internalName]
                    if (existing == null || remote.version > existing.version) {
                        latestById[remote.internalName] = remote
                    }
                }
            } catch (e: Throwable) {
                println("Update check: skipping repo ${repo.name}: ${e.message}")
            }
        }
        val updates = installed.mapNotNull { local ->
            val remote = latestById[local.internalName]
            if (remote != null && pluginNeedsRefresh(local, remote)) {
                PluginUpdateInfo(installed = local, remote = remote)
            } else null
        }
        availableUpdates.value = updates
        lastUpdateCheckMs = System.currentTimeMillis()
        updates
    }

    private fun managedPluginFile(internalName: String): File? {
        if (!isSafePluginInternalName(internalName)) return null
        return File(AppPaths.pluginsDir, "$internalName.cs3")
    }

    private fun samePath(storedPath: String, file: File): Boolean {
        val stored = runCatching { File(storedPath).canonicalFile }.getOrNull() ?: return false
        val managed = runCatching { file.canonicalFile }.getOrNull() ?: return false
        return stored == managed
    }

    private fun resolveInstalledArchive(plugin: StoredPlugin): File {
        val managed = managedPluginFile(plugin.internalName)
        return if (managed?.isFile == true) managed else File(plugin.filePath)
    }

    private fun pluginNeedsRefresh(local: StoredPlugin, remote: RemotePlugin): Boolean {
        if (remote.version > local.version) return true
        if (remote.version < local.version || remote.fileHash.isNullOrBlank()) return false

        val expected = remote.fileHash.removePrefix("sha256-").trim()
        if (!expected.matches(Regex("[0-9a-fA-F]{64}"))) return false
        val archive = resolveInstalledArchive(local)
        if (!archive.isFile) return true
        val actual = runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            archive.inputStream().buffered().use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull() ?: return true
        return !actual.equals(expected, ignoreCase = true)
    }

    data class AutoUpdateResult(
        val checked: Int,
        val updated: Int,
        val failed: List<String>
    )

    /**
     * Install every pending update. Uses the same atomic staged install as a
     * manual install, so a failed update keeps the previous working .cs3.
     */
    suspend fun autoUpdatePlugins(
        updates: List<PluginUpdateInfo>? = null,
        onProgress: (done: Int, total: Int, name: String) -> Unit = { _, _, _ -> }
    ): AutoUpdateResult = withContext(Dispatchers.IO) {
        val pending = updates ?: checkForPluginUpdates()
        if (pending.isEmpty()) return@withContext AutoUpdateResult(checked = 0, updated = 0, failed = emptyList())
        var updated = 0
        val failed = mutableListOf<String>()
        pending.forEachIndexed { index, info ->
            onProgress(index, pending.size, info.installed.name)
            try {
                val updateLabel = if (info.remote.version > info.installed.version) {
                    "v${info.installed.version} → v${info.remote.version}"
                } else "refreshing v${info.installed.version}"
                NotificationService.info("Updating ${info.installed.name} ($updateLabel)...")
                val ok = installPlugin(info.remote, null, enabledAfterInstall = info.installed.enabled)
                if (ok) updated++ else failed.add(info.installed.name)
            } catch (e: Throwable) {
                e.printStackTrace()
                failed.add(info.installed.name)
            }
        }
        // Refresh the pending list after applying updates.
        runCatching { checkForPluginUpdates() }
        if (updated > 0 && failed.isEmpty()) {
            NotificationService.success("Updated $updated extension${if (updated == 1) "" else "s"}")
        } else if (updated > 0) {
            NotificationService.info("Updated $updated, failed ${failed.size}: ${failed.joinToString()}")
        }
        AutoUpdateResult(checked = pending.size, updated = updated, failed = failed)
    }

    private fun unloadPlugin(internalName: String) {
        val instance = loadedPlugins.remove(internalName)
        if (instance != null) {
            try {
                instance.beforeUnload()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            removePluginRegistrations(instance.filename)
        }
        classLoaders.remove(internalName)?.close()
    }

    private fun removePluginRegistrations(filename: String?) {
        if (filename == null) return
        val providers = APIHolder.allProviders.filter { it.sourcePlugin == filename }
        providers.forEach(APIHolder::removePluginMapping)
        APIHolder.allProviders.removeAll { it.sourcePlugin == filename }
        extractorApis.removeAll { it.sourcePlugin == filename }
        VideoClickActionHolder.allVideoClickActions.removeAll { it.sourcePlugin == filename }
    }

    fun testLoadPlugin(file: File, internalName: String): Boolean = loadPluginFromFile(file, internalName)

    internal fun loadPluginFromFile(
        file: File,
        internalName: String,
        onError: (String) -> Unit = {}
    ): Boolean {
        var activeClassLoader: URLClassLoader? = null
        val providersBeforeLoad = APIHolder.allProviders.toSet()
        val extractorsBeforeLoad = extractorApis.toSet()
        val actionsBeforeLoad = VideoClickActionHolder.allVideoClickActions.toSet()
        fun fail(message: String, error: Throwable? = null): Boolean {
            val detail = error?.let { "$message: ${it::class.simpleName}: ${it.message ?: "no details"}" } ?: message
            System.err.println("Failed to load plugin ${file.name}: $detail")
            error?.printStackTrace()
            runCatching { activeClassLoader?.close() }
            val partialProviders = APIHolder.allProviders.filter {
                it.sourcePlugin == file.absolutePath && it !in providersBeforeLoad
            }
            partialProviders.forEach(APIHolder::removePluginMapping)
            APIHolder.allProviders.removeAll { it.sourcePlugin == file.absolutePath && it !in providersBeforeLoad }
            extractorApis.removeAll { it.sourcePlugin == file.absolutePath && it !in extractorsBeforeLoad }
            VideoClickActionHolder.allVideoClickActions.removeAll {
                it.sourcePlugin == file.absolutePath && it !in actionsBeforeLoad
            }
            onError(detail)
            return false
        }

        try {
            val jarFile = File(AppPaths.pluginsCacheDir, "$internalName.jar")
            var manifest: BasePlugin.Manifest? = null
            var dexBytes: ByteArray? = null

            // Read the .cs3 ZIP archive
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when (entry.name) {
                        "manifest.json" -> {
                            val content = zis.bufferedReader().readText()
                            manifest = desktopJson.decodeFromString<BasePlugin.Manifest>(content)
                        }
                        "classes.dex" -> {
                            dexBytes = zis.readBytes()
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val validManifest = manifest
                ?: return fail("The archive does not contain a valid manifest.json")

            val pluginClassName = validManifest.pluginClassName
                ?: return fail("The plugin manifest does not declare pluginClassName")
            val dexHashFile = File(AppPaths.pluginsCacheDir, "$internalName.dex.sha256")
            val dexHash = dexBytes?.let { bytes ->
                MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            }
            val cacheFingerprint = dexHash?.let { "desktop-dex2jar-v2:$it" }
            val cachedClassEntry = pluginClassName.replace('.', '/') + ".class"
            val cachedJarIsUsable = if (jarFile.isFile) {
                try {
                    java.util.jar.JarFile(jarFile).use { it.getJarEntry(cachedClassEntry) != null } &&
                        cacheFingerprint != null && dexHashFile.isFile && dexHashFile.readText().trim() == cacheFingerprint
                } catch (_: Throwable) {
                    false
                }
            } else false

            // Validate cached bytecode against the exact DEX payload. Earlier builds could
            // leave a .cs3 archive in this cache after a conversion error.
            if (dexBytes != null && !cachedJarIsUsable) {
                val temporaryJar = File(AppPaths.pluginsCacheDir, "$internalName.jar.tmp")
                try {
                    temporaryJar.delete()
                    val d2j = Dex2jar.from(dexBytes)
                    d2j.to(temporaryJar.toPath())
                    remapKotlinResultAbi(temporaryJar)
                    val convertedJarIsUsable = java.util.jar.JarFile(temporaryJar).use {
                        it.getJarEntry(cachedClassEntry) != null
                    }
                    if (!convertedJarIsUsable) {
                        throw IllegalStateException("Dex2jar output does not contain $pluginClassName")
                    }
                    try {
                        java.nio.file.Files.move(
                            temporaryJar.toPath(),
                            jarFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE
                        )
                    } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                        java.nio.file.Files.move(
                            temporaryJar.toPath(),
                            jarFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                    dexHashFile.writeText(cacheFingerprint ?: return fail("Could not calculate the DEX cache fingerprint"))
                } catch (e: Throwable) {
                    temporaryJar.delete()
                    return fail("DEX translation failed", e)
                }
            } else if (dexBytes == null && !cachedJarIsUsable) {
                return fail("The archive does not contain classes.dex or a valid cached JVM plugin")
            }

            // Load class using URLClassLoader
            val classLoader = URLClassLoader(
                arrayOf(jarFile.toURI().toURL()),
                DesktopPluginManager::class.java.classLoader
            )
            activeClassLoader = classLoader

            val pluginClass = classLoader.loadClass(pluginClassName)
            val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
            instance.filename = file.absolutePath

            if (instance is com.lagradost.cloudstream3.plugins.Plugin) {
                try {
                    instance.load(android.content.Context())
                } catch (e: Throwable) {
                    println("instance.load(Context) encountered: ${e.message}, falling back to instance.load()")
                    instance.load()
                }
            } else {
                instance.load()
            }
            classLoaders.remove(internalName)?.close()
            classLoaders[internalName] = classLoader
            loadedPlugins[internalName] = instance
            println("Successfully loaded desktop plugin: ${validManifest.name ?: internalName}")
            return true
        } catch (e: Throwable) {
            return fail("Plugin initialization failed", e)
        }
    }

    /**
     * dex2jar sanitizes '-' in DEX method names to '_'. Kotlin's inline Result ABI uses
     * methods such as Result.constructor-impl, so the translated plugin otherwise calls
     * a method that does not exist on the desktop Kotlin runtime.
     */
    private fun remapKotlinResultAbi(jarFile: File) {
        val remappedFile = File(jarFile.parentFile, "${jarFile.name}.remapped")
        remappedFile.delete()
        java.util.jar.JarFile(jarFile).use { source ->
            java.util.jar.JarOutputStream(remappedFile.outputStream()).use { output ->
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val outputEntry = java.util.jar.JarEntry(entry.name)
                    outputEntry.time = entry.time
                    output.putNextEntry(outputEntry)
                    val bytes = source.getInputStream(entry).use { it.readBytes() }
                    val rewritten = if (entry.name.endsWith(".class")) {
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(0)
                        val remapper = object : Remapper(org.objectweb.asm.Opcodes.ASM9) {
                            override fun mapMethodName(owner: String, name: String, descriptor: String): String {
                                return if (owner == "kotlin/Result" && name.endsWith("_impl")) {
                                    name.removeSuffix("_impl") + "-impl"
                                } else {
                                    super.mapMethodName(owner, name, descriptor)
                                }
                            }
                        }
                        reader.accept(ClassRemapper(writer, remapper), 0)
                        writer.toByteArray()
                    } else {
                        bytes
                    }
                    output.write(rewritten)
                    output.closeEntry()
                }
            }
        }
        java.nio.file.Files.move(
            remappedFile.toPath(),
            jarFile.toPath(),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING
        )
    }
}
