package com.lagradost.cloudstream4.core

import java.io.File

object AppPaths {
    /** Clearly named data folder created inside the install directory. */
    const val DATA_DIR_NAME = "CloudStreamData"

    /** Explicit override: JVM property first, then environment variable. */
    const val DATA_OVERRIDE_PROPERTY = "cloudstream.data.dir"
    const val DATA_OVERRIDE_ENV = "CLOUDSTREAM_DATA_DIR"

    /** Previous fixed location. Read-only legacy source for migration; never written. */
    const val LEGACY_WINDOWS_DATA_PATH = "D:\\CloudStreamData"

    private val _locationNotices = mutableListOf<String>()

    /** User-facing notices produced while resolving/migrating (relocation, migration). */
    val locationNotices: List<String> get() = _locationNotices.toList()

    val appDataDir: File by lazy {
        val dir = resolveAppDataDir()
        maybeMigrateLegacyData(dir)
        dir
    }

    val pluginsDir: File by lazy {
        File(appDataDir, "plugins").apply { if (!exists()) mkdirs() }
    }

    val pluginsCacheDir: File by lazy {
        File(pluginsDir, "cache").apply { if (!exists()) mkdirs() }
    }

    val dataDir: File by lazy {
        File(appDataDir, "data").apply { if (!exists()) mkdirs() }
    }

    val cacheDir: File by lazy {
        File(appDataDir, "cache").apply { if (!exists()) mkdirs() }
    }

    val defaultDownloadsDir: File by lazy {
        File(appDataDir, "downloads").apply { if (!exists()) mkdirs() }
    }

    private fun resolveAppDataDir(): File {
        // 1. Explicit override wins everywhere (dev runs, tests, portable use).
        //    Misconfiguration fails fast instead of silently landing elsewhere.
        val override = System.getProperty(DATA_OVERRIDE_PROPERTY)?.takeIf { it.isNotBlank() }
            ?: System.getenv(DATA_OVERRIDE_ENV)?.takeIf { it.isNotBlank() }
        if (override != null) {
            val dir = File(override)
            require(ensureWritableDir(dir)) {
                "Configured data dir is not writable: ${dir.absolutePath} ($DATA_OVERRIDE_PROPERTY/$DATA_OVERRIDE_ENV)"
            }
            return dir
        }

        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("win")) {
            return legacyNonWindowsDir()
        }

        // 2. Packaged install: derive the install root from the running
        //    executable. Start Menu / desktop shortcuts launch the exe
        //    directly, so this works regardless of working directory.
        val command = runCatching { ProcessHandle.current().info().command().orElse(null) }.getOrNull()
        val installRoot = installRootFromCommand(command)
        if (installRoot != null) {
            val candidate = File(installRoot, DATA_DIR_NAME)
            if (ensureWritableDir(candidate)) return candidate
            // 3. Installed somewhere protected (e.g. Program Files): fall back
            //    to a per-user LocalAppData folder WITH a clear notice shown in
            //    Settings. Never admin rights, never a silent D: fallback.
            val fallback = localAppDataFallback()
            require(ensureWritableDir(fallback)) {
                "Neither the install folder nor the per-user fallback is writable: " +
                    "${candidate.absolutePath}, ${fallback.absolutePath}"
            }
            _locationNotices += "CloudStream is installed in ${installRoot.absolutePath}, " +
                "which Windows does not let apps write to without administrator rights. " +
                "Settings and extensions are stored instead at ${fallback.absolutePath}. " +
                "To keep everything with the app, reinstall using the default per-user " +
                "location (no admin needed) or move the app folder somewhere writable. " +
                "Nothing was deleted; your previous files were left in place."
            return fallback
        }

        // 4. Development run (plain java/gradle, no packaged exe): reuse the
        //    existing D: data when present so dev/test workflows keep working,
        //    otherwise a run-data folder next to the working directory.
        val legacy = File(LEGACY_WINDOWS_DATA_PATH)
        if (legacy.isDirectory) {
            _locationNotices +=
                "Development run: using the existing data folder at ${legacy.absolutePath}."
            return legacy
        }
        val devDir = File(System.getProperty("user.dir"), "run-data/$DATA_DIR_NAME")
        require(ensureWritableDir(devDir)) {
            "Development data dir is not writable: ${devDir.absolutePath}"
        }
        return devDir
    }

    private fun legacyNonWindowsDir(): File {
        val os = System.getProperty("os.name").lowercase()
        val dir = if (os.contains("mac")) {
            File(System.getProperty("user.home"), "Library/Application Support/CloudStream")
        } else {
            val xdgConfig = System.getenv("XDG_CONFIG_HOME")
            if (!xdgConfig.isNullOrBlank()) File(xdgConfig, "cloudstream")
            else File(System.getProperty("user.home"), ".config/cloudstream")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun localAppDataFallback(): File {
        val base = System.getenv("LOCALAPPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), "AppData/Local")
        return File(base, "CloudStream/$DATA_DIR_NAME")
    }

    private fun maybeMigrateLegacyData(target: File) {
        if (!System.getProperty("os.name").lowercase().contains("win")) return
        val copied = runCatching { migrateLegacyData(File(LEGACY_WINDOWS_DATA_PATH), target) }.getOrDefault(0)
        if (copied > 0) {
            _locationNotices += "Copied $copied settings/extension file(s) from the old " +
                "$LEGACY_WINDOWS_DATA_PATH folder into ${target.absolutePath}. The originals " +
                "were left untouched, and already-downloaded videos stay where they were."
        }
    }
}

/**
 * Derives the install root from a process command path. Returns the exe's
 * parent directory only when it looks like a packaged Compose install
 * (an `app/` subfolder with the jars); otherwise null (dev `java` runs,
 * bare copies). Pure and unit-tested.
 */
internal fun installRootFromCommand(command: String?): File? {
    if (command.isNullOrBlank()) return null
    var cleaned = command.trim()
    if (cleaned.length >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
        cleaned = cleaned.substring(1, cleaned.length - 1)
    }
    if (cleaned.isBlank()) return null
    val exe = File(cleaned)
    val name = exe.name.lowercase()
    if (name != "cloudstream.exe" && name != "cloudstream") return null
    val root = exe.parentFile ?: return null
    if (!File(root, "app").isDirectory) return null
    return root
}

/** Creates the dir if needed, then proves writability with a probe file. Pure IO, unit-tested. */
internal fun ensureWritableDir(dir: File): Boolean = try {
    if (!dir.isDirectory && !dir.mkdirs()) return false
    if (!dir.isDirectory) return false
    val probe = File.createTempFile(".writetest", null, dir)
    probe.delete()
    true
} catch (_: Throwable) {
    false
}

private val MIGRATED_JSONS = listOf(
    "settings.json", "bookmarks.json", "history.json",
    "repositories.json", "plugins.json", "downloads.json", "skipSegments.json"
)
private const val MIGRATION_MARKER = ".migrated-from-legacy"

/**
 * One-time, non-destructive copy from the legacy fixed folder into a fresh
 * install-relative home. Copies small JSON state + .cs3 plugin archives only;
 * skips temp files, regenerable caches, and downloaded media binaries
 * (downloads.json keeps their absolute paths, so resume still works).
 * Never deletes or overwrites: skips entirely unless the target is fresh
 * (no settings.json and no .cs3 yet) and records a marker afterwards.
 * Returns the number of files copied, 0 when skipped. Unit-tested with temp dirs.
 */
internal fun migrateLegacyData(legacyRoot: File, targetRoot: File): Int {
    if (!legacyRoot.isDirectory) return 0
    val legacyCanon = runCatching { legacyRoot.canonicalPath }.getOrDefault(legacyRoot.absolutePath)
    val targetCanon = runCatching { targetRoot.canonicalPath }.getOrDefault(targetRoot.absolutePath)
    if (targetCanon.equals(legacyCanon, ignoreCase = true) ||
        targetCanon.startsWith(legacyCanon + File.separator, ignoreCase = true)
    ) {
        return 0
    }
    if (File(targetRoot, MIGRATION_MARKER).exists()) return 0
    val targetData = File(targetRoot, "data").apply { mkdirs() }
    val targetPlugins = File(targetRoot, "plugins").apply { mkdirs() }
    val targetHasSettings = File(targetData, "settings.json").exists()
    val targetHasPlugins = targetPlugins.listFiles { f -> f.isFile && f.name.endsWith(".cs3", ignoreCase = true) }
        ?.isNotEmpty() == true
    if (targetHasSettings || targetHasPlugins) return 0

    var copied = 0
    val legacyData = File(legacyRoot, "data")
    for (name in MIGRATED_JSONS) {
        val src = File(legacyData, name)
        val dst = File(targetData, name)
        if (src.isFile && !dst.exists() && runCatching { src.copyTo(dst); true }.getOrDefault(false)) copied++
    }
    File(legacyRoot, "plugins")
        .listFiles { f -> f.isFile && f.name.endsWith(".cs3", ignoreCase = true) }
        ?.forEach { src ->
            val dst = File(targetPlugins, src.name)
            if (!dst.exists() && runCatching { src.copyTo(dst); true }.getOrDefault(false)) copied++
        }
    runCatching {
        File(targetRoot, MIGRATION_MARKER)
            .writeText("migrated=$copied legacy=$legacyCanon at=${System.currentTimeMillis()}")
    }
    return copied
}

/**
 * Keeps user-chosen download folders, except ones rooted at the legacy fixed
 * path, which are rebased to the new default. Pure and unit-tested.
 */
internal fun rebaseDownloadsDirectory(savedDir: String, legacyRootPath: String, newDefaultDir: String): String {
    val trimmed = savedDir.trim()
    if (trimmed.isBlank()) return newDefaultDir
    val normLegacy = legacyRootPath.trimEnd('\\', '/')
    val underLegacy = trimmed.equals(normLegacy, ignoreCase = true) ||
        trimmed.startsWith("$normLegacy\\", ignoreCase = true) ||
        trimmed.startsWith("$normLegacy/", ignoreCase = true)
    return if (underLegacy) newDefaultDir else savedDir
}
