package app.anikuta.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.anikuta.data.notification.Notifications
import app.anikuta.download.engine.DownloadEngine
import app.anikuta.download.engine.SegmentDownloadEngine
import app.anikuta.download.progress.ProgressTracker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Thin WorkManager worker that processes the download queue.
 *
 * Responsibilities:
 * 1. Set up as foreground service (Android 12+ requirement)
 * 2. Process downloads concurrently (up to maxConcurrentDownloads)
 * 3. Delegate actual downloading to [DownloadEngine]
 * 4. Update notifications via [DownloadNotifier]
 * 5. Persist state via [DownloadStore]
 *
 * Concurrency fix (B6): each download runs in its own child coroutine
 * with a shared Semaphore to limit concurrency.
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"
    }

    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadNotifier: DownloadNotifier = Injekt.get()
    private val downloadPrefs: DownloadPreferences = Injekt.get()
    private val downloadStore: DownloadStore = Injekt.get()

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork: → worker started")

        val queue = downloadManager.queue.value
        val pending = queue.filter {
            it.status == Download.State.QUEUE ||
            it.status == Download.State.ERROR
        }

        if (pending.isEmpty()) {
            Log.d(TAG, "doWork: no pending downloads — done")
            downloadNotifier.cancelProgress()
            return Result.success()
        }

        Log.d(TAG, "doWork: ${pending.size} pending downloads, " +
            "maxConcurrent=${downloadPrefs.maxConcurrentDownloads().get()}")

        // Set up foreground service
        try {
            val foregroundInfo = createForegroundInfo(pending)
            setForeground(foregroundInfo)
            Log.d(TAG, "doWork: ✓ foreground service started")
        } catch (e: Exception) {
            Log.w(TAG, "doWork: ⚠ could not start foreground service: ${e.message}")
            // Continue without foreground service (downloads may be killed on Android 12+)
        }

        val maxConcurrent = downloadPrefs.maxConcurrentDownloads().get().coerceIn(1, 4)
        val semaphore = Semaphore(maxConcurrent)

        // Process downloads concurrently — each in its own coroutine (fixes B6)
        coroutineScope {
            pending.map { download ->
                async {
                    semaphore.withPermit {
                        processDownload(download)
                    }
                }
            }.awaitAll()
        }

        // FIX (E2): After processing all downloads, check if any NEW downloads were
        // enqueued during processing. If so, re-run the worker to pick them up.
        // Without this, a download enqueued while the worker is running would sit
        // in QUEUE status forever until the user manually pauses+resumes.
        val remainingPending = downloadManager.queue.value.filter {
            it.status == Download.State.QUEUE || it.status == Download.State.ERROR
        }
        if (remainingPending.isNotEmpty()) {
            Log.d(TAG, "doWork: ${remainingPending.size} downloads still pending — returning retry")
            return Result.retry()
        }

        // Update notification after all downloads
        val finalQueue = downloadManager.queue.value
        val activeDownloads = finalQueue.filter {
            it.status == Download.State.DOWNLOADING || it.status == Download.State.QUEUE
        }
        if (activeDownloads.isEmpty()) {
            downloadNotifier.cancelProgress()
        }

        Log.d(TAG, "doWork: ✓ worker finished")
        return Result.success()
    }

    /**
     * Process a single download. Delegates to the engine.
     */
    private suspend fun processDownload(download: Download) {
        Log.d(TAG, "processDownload: → ${download.episodeName} (id=${download.id})")

        val engine: DownloadEngine = Injekt.get<SegmentDownloadEngine>()
        val progressTracker: ProgressTracker = Injekt.get()

        // Get the engine instance and reset its pause/cancel flags
        if (engine is SegmentDownloadEngine) {
            engine.resetFlags()
        }

        var lastError: String? = null
        val maxRetries = 3

        for (attempt in 1..maxRetries) {
            try {
                Log.d(TAG, "processDownload: ${download.episodeName} attempt $attempt/$maxRetries")

                // Phase 1: Resolve video URL
                downloadManager.updateDownloadState(download.id, status = Download.State.RESOLVING)
                downloadNotifier.updateProgress(listOf(download))

                val resolved = engine.resolve(download)
                if (!resolved) {
                    throw Exception("Video resolution failed: ${download.error ?: "unknown"}")
                }

                // Phase 2: Download
                downloadManager.updateDownloadState(download.id, status = Download.State.DOWNLOADING)
                downloadNotifier.updateProgress(listOf(download))

                // Start a coroutine to periodically update the notification
                val notifJob = kotlinx.coroutines.GlobalScope.launch {
                    while (true) {
                        delay(1000)
                        val active = downloadManager.queue.value.filter {
                            it.status == Download.State.DOWNLOADING ||
                            it.status == Download.State.RESOLVING ||
                            it.status == Download.State.MUXING
                        }
                        if (active.isNotEmpty()) {
                            downloadNotifier.updateProgress(active)
                        } else {
                            break
                        }
                    }
                }

                val success = try {
                    engine.download(download)
                } finally {
                    notifJob.cancel()
                }

                if (success) {
                    downloadManager.updateDownloadState(
                        download.id,
                        status = Download.State.DOWNLOADED,
                        progress = 100,
                    )
                    downloadNotifier.postComplete(download)
                    Log.d(TAG, "processDownload: ✓ ${download.episodeName} complete")
                    return
                } else {
                    // Check if paused
                    if (download.status == Download.State.PAUSED) {
                        Log.d(TAG, "processDownload: ⏸ ${download.episodeName} paused")
                        return
                    }
                    throw Exception("Download failed: ${download.error ?: "unknown"}")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                Log.e(TAG, "processDownload: ❌ ${download.episodeName} attempt $attempt failed: $lastError")

                // FIX (E4): Do NOT call engine.cancel() on failure.
                // engine.cancel() deletes the cache dir (segments) which destroys
                // resume state. The manifest is in SAF and survives, but on retry
                // verifySegmentFiles() finds no cache files → marks all "done"
                // segments as "pending" → re-downloads from scratch.
                // Instead: leave the cache intact. The engine's downloadSegment()
                // already deletes the failed segment's partial file. On retry,
                // the engine reads the manifest, finds completed segments in cache,
                // and resumes from the first pending/partial one.

                if (attempt < maxRetries) {
                    val backoff = when (attempt) {
                        1 -> 2000L
                        2 -> 4000L
                        else -> 8000L
                    }
                    Log.d(TAG, "processDownload: retrying in ${backoff}ms...")
                    delay(backoff)
                }
            }
        }

        // All retries failed
        downloadManager.updateDownloadState(
            download.id,
            status = Download.State.ERROR,
            error = lastError,
        )
        downloadNotifier.postError(download)
        Log.e(TAG, "processDownload: ❌ ${download.episodeName} failed after $maxRetries attempts: $lastError")
    }

    /**
     * Create the foreground service notification info.
     */
    private fun createForegroundInfo(downloads: List<Download>): ForegroundInfo {
        downloadNotifier.createChannels()

        val notification = downloadNotifier.buildProgressNotification(downloads).build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.ID_DOWNLOAD_PROGRESS,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(
                Notifications.ID_DOWNLOAD_PROGRESS,
                notification,
            )
        }
    }
}
