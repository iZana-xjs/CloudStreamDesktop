package com.lagradost.cloudstream4.downloads

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream4.core.AppPaths
import com.lagradost.cloudstream4.core.DesktopStorage
import com.lagradost.cloudstream4.core.NotificationService
import com.lagradost.cloudstream4.core.StoredDownload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val episodeTitle: String? = null,
    val sourceUrl: String,
    val destinationPath: String,
    val posterUrl: String? = null,
    val headers: Map<String, String> = emptyMap(),
    initialTotalBytes: Long = 0L,
    initialDownloadedBytes: Long = 0L,
    initialState: DownloadState = DownloadState.QUEUED
) {
    var totalBytes by mutableLongStateOf(initialTotalBytes)
    var downloadedBytes by mutableLongStateOf(initialDownloadedBytes)
    var state by mutableStateOf(initialState)
    var speedBytesPerSec by mutableLongStateOf(0L)
    var etaSeconds by mutableLongStateOf(0L)
    var errorMessage by mutableStateOf<String?>(null)

    var job: Job? = null

    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

object DesktopDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val tasks = mutableStateListOf<DownloadTask>()

    init {
        // Load existing completed downloads from persistence
        val saved = DesktopStorage.getDownloads()
        for (item in saved) {
            tasks.add(
                DownloadTask(
                    id = item.id,
                    title = item.mediaTitle,
                    episodeTitle = item.episodeTitle,
                    sourceUrl = item.sourceUrl,
                    destinationPath = item.destinationPath,
                    posterUrl = item.posterUrl,
                    headers = item.headers,
                    initialTotalBytes = item.totalBytes,
                    initialDownloadedBytes = item.downloadedBytes,
                    initialState = when (item.status) {
                        "COMPLETED" -> DownloadState.COMPLETED
                        "PAUSED" -> DownloadState.PAUSED
                        "FAILED" -> DownloadState.FAILED
                        else -> DownloadState.COMPLETED
                    }
                )
            )
        }
    }

    fun startDownload(
        title: String,
        episodeTitle: String?,
        sourceUrl: String,
        posterUrl: String? = null,
        headers: Map<String, String> = emptyMap()
    ) {
        val safeName = (title + (if (episodeTitle != null) " - $episodeTitle" else ""))
            .replace("[^a-zA-Z0-9.-]".toRegex(), "_")
        val destinationFolder = File(DesktopStorage.settings.downloadsDirectory)
        if (!destinationFolder.exists()) destinationFolder.mkdirs()

        val destFile = File(destinationFolder, "$safeName.mp4")
        val task = DownloadTask(
            title = title,
            episodeTitle = episodeTitle,
            sourceUrl = sourceUrl,
            destinationPath = destFile.absolutePath,
            posterUrl = posterUrl,
            headers = headers
        )
        tasks.add(task)
        NotificationService.info("Download started: $title")
        enqueue(task)
    }

    private fun enqueue(task: DownloadTask) {
        task.state = DownloadState.DOWNLOADING
        task.job = scope.launch {
            val partFile = File("${task.destinationPath}.part")
            val finalFile = File(task.destinationPath)

            try {
                var startByte = 0L
                if (partFile.exists()) {
                    startByte = partFile.length()
                    task.downloadedBytes = startByte
                }

                val requestHeaders = task.headers.toMutableMap()
                if (startByte > 0) requestHeaders["Range"] = "bytes=$startByte-"
                val response = app.get(task.sourceUrl, headers = requestHeaders).okhttpResponse
                val body = response.body
                val contentLength = body.contentLength()

                if (contentLength > 0) {
                    task.totalBytes = startByte + contentLength
                }

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(partFile, startByte > 0)

                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var lastSpeedCheckTime = System.currentTimeMillis()
                var bytesSinceLastCheck = 0L

                outputStream.use { out ->
                    inputStream.use { `in` ->
                        while (`in`.read(buffer).also { bytesRead = it } != -1 && isActive) {
                            if (task.state == DownloadState.PAUSED || task.state == DownloadState.CANCELLED) {
                                break
                            }

                            out.write(buffer, 0, bytesRead)
                            task.downloadedBytes += bytesRead
                            bytesSinceLastCheck += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDiff = now - lastSpeedCheckTime
                            if (timeDiff >= 1000) {
                                val speed = (bytesSinceLastCheck * 1000) / timeDiff
                                task.speedBytesPerSec = speed
                                if (speed > 0 && task.totalBytes > task.downloadedBytes) {
                                    task.etaSeconds = (task.totalBytes - task.downloadedBytes) / speed
                                }
                                lastSpeedCheckTime = now
                                bytesSinceLastCheck = 0L
                            }
                        }
                    }
                }

                if (task.state == DownloadState.CANCELLED) {
                    if (partFile.exists()) partFile.delete()
                    return@launch
                }

                if (task.state == DownloadState.PAUSED) {
                    return@launch
                }

                if (partFile.exists()) {
                    partFile.renameTo(finalFile)
                }

                task.state = DownloadState.COMPLETED
                task.speedBytesPerSec = 0L
                task.etaSeconds = 0L

                DesktopStorage.saveDownload(
                    StoredDownload(
                        id = task.id,
                        mediaTitle = task.title,
                        episodeTitle = task.episodeTitle,
                        sourceUrl = task.sourceUrl,
                        destinationPath = finalFile.absolutePath,
                        posterUrl = task.posterUrl,
                        headers = task.headers,
                        totalBytes = task.totalBytes,
                        downloadedBytes = task.downloadedBytes,
                        status = "COMPLETED"
                    )
                )
                NotificationService.success("Download finished: ${task.title}")

            } catch (e: Throwable) {
                if (task.state != DownloadState.CANCELLED && task.state != DownloadState.PAUSED) {
                    task.state = DownloadState.FAILED
                    task.errorMessage = e.message
                    NotificationService.error("Download failed: ${task.title}", e.message)
                }
            }
        }
    }

    fun pauseDownload(task: DownloadTask) {
        task.state = DownloadState.PAUSED
        task.job?.cancel()
        task.speedBytesPerSec = 0L
    }

    fun resumeDownload(task: DownloadTask) {
        enqueue(task)
    }

    fun cancelDownload(task: DownloadTask) {
        task.state = DownloadState.CANCELLED
        task.job?.cancel()
        val partFile = File("${task.destinationPath}.part")
        if (partFile.exists()) partFile.delete()
        val finalFile = File(task.destinationPath)
        if (finalFile.exists()) finalFile.delete()
        tasks.remove(task)
        DesktopStorage.removeDownload(task.id)
    }

    fun removeCompleted(task: DownloadTask, deleteFile: Boolean = false) {
        if (deleteFile) {
            val file = File(task.destinationPath)
            if (file.exists()) file.delete()
        }
        tasks.remove(task)
        DesktopStorage.removeDownload(task.id)
    }
}
