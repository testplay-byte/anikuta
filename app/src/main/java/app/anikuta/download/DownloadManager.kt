package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.anikuta.download.engine.DownloadEngine
import app.anikuta.source.api.model.SEpisode
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Public facade for the download system. Manages the download queue lifecycle:
 * enqueue, pause, resume, cancel, retry, query.
 *
 * Delegates actual downloading to [DownloadWorker] (WorkManager) which calls
 * the [DownloadEngine] (segment-based with resume).
 *
 * The queue is reactive: status/progress changes on individual [Download] objects
 * propagate to all observers via the downloads' StateFlows. The [queue] flow
 * re-emits when the list structure changes (add/remove). For per-download
 * status/progress, observers should collect [Download.statusFlow] and
 * [Download.progressFlow] directly.
 *
 * To fix bug B3 (status changes not reaching the UI), the [queueStateFlow]
 * combines the queue list with each download's statusFlow, so any status
 * change triggers a re-emit.
 *
 * @param context application context
 * @param store persistent queue state
 * @param prefs download preferences (wifi-only, concurrent, priority lists)
 */
class DownloadManager(
    private val context: Context,
    private val store: DownloadStore,
    private val prefs: DownloadPreferences,
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val UNIQUE_WORK = "AnikutaDownloader"
    }

    private val workManager: WorkManager = WorkManager.getInstance(context)

    /** Live in-memory queue (Download objects with StateFlows). */
    private val _queue = MutableStateFlow<List<Download>>(emptyList())
    val queue: StateFlow<List<Download>> = _queue.asStateFlow()

    /**
     * Reactive map of episodeName → download status.
     * Re-emits whenever any download's status changes (fixes bug B3).
     * Used by DetailViewModel for the download button state.
     */
    private val _downloadStatusMap = MutableStateFlow<Map<String, Download.State>>(emptyMap())
    val downloadStatusMap: StateFlow<Map<String, Download.State>> = _downloadStatusMap.asStateFlow()

    /**
     * Reactive map of episodeName → download progress (0-100).
     * Re-emits whenever any download's progress changes.
     * Used by DetailViewModel to show determinate progress on the download button (fixes C5).
     */
    private val _downloadProgressMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgressMap: StateFlow<Map<String, Int>> = _downloadProgressMap.asStateFlow()

    /**
     * Refresh both status and progress maps from the current queue state.
     */
    private fun refreshStatusMap() {
        _downloadStatusMap.value = _queue.value.associate { it.episodeName to it.status }
        _downloadProgressMap.value = _queue.value.associate { it.episodeName to it.progress }
    }

    init {
        // Restore queue from persistent store on startup
        restoreQueue()
        Log.d(TAG, "init: ✓ DownloadManager initialized, queue size=${_queue.value.size}")
    }

    /**
     * Enqueue a single episode for download.
     */
    fun enqueueDownload(
        anilistId: Int,
        sourceId: Long,
        sourceName: String,
        animeTitle: String,
        episode: SEpisode,
    ): String {
        val downloadId = "dl_${anilistId}_${episode.episode_number}_${System.currentTimeMillis()}"

        Log.d(TAG, "enqueueDownload: → ${episode.name} (id=$downloadId, source=$sourceName, anime=$animeTitle)")

        val entry = DownloadStore.DownloadStoreEntry(
            id = downloadId,
            anilistId = anilistId,
            sourceId = sourceId,
            sourceName = sourceName,
            animeTitle = animeTitle,
            episodeUrl = episode.url,
            episodeName = episode.name.ifBlank { "Episode ${episode.episode_number}" },
            episodeNumber = episode.episode_number,
        )

        // Dedup check: only add to live queue if the store actually added it
        // (fixes C2 — duplicate downloads when tapping the spinning circle)
        val wasAdded = store.add(entry)
        if (wasAdded) {
            addToLiveQueue(entry)
            startWork()
            Log.d(TAG, "enqueueDownload: ✓ enqueued ${episode.name} (id=$downloadId)")
        } else {
            Log.d(TAG, "enqueueDownload: ⏭ skipped duplicate — ${episode.name} already in queue")
        }
        return downloadId
    }

    /**
     * Enqueue multiple episodes for download.
     */
    fun enqueueDownloads(
        anilistId: Int,
        sourceId: Long,
        sourceName: String,
        animeTitle: String,
        episodes: List<SEpisode>,
    ) {
        Log.d(TAG, "enqueueDownloads: → ${episodes.size} episodes for $animeTitle")

        val entries = episodes.mapIndexed { index, ep ->
            DownloadStore.DownloadStoreEntry(
                id = "dl_${anilistId}_${ep.episode_number}_${System.currentTimeMillis()}_${index}",
                anilistId = anilistId,
                sourceId = sourceId,
                sourceName = sourceName,
                animeTitle = animeTitle,
                episodeUrl = ep.url,
                episodeName = ep.name.ifBlank { "Episode ${ep.episode_number}" },
                episodeNumber = ep.episode_number,
                order = index,
            )
        }
        store.addAll(entries)
        entries.forEach { addToLiveQueue(it) }
        startWork()

        Log.d(TAG, "enqueueDownloads: ✓ enqueued ${entries.size} downloads")
    }

    /** Cancel a download + remove from queue. */
    fun cancelDownload(downloadId: String) {
        Log.d(TAG, "cancelDownload: → $downloadId")
        // Set status to NOT_DOWNLOADED so the statusFlow emits (fixes C3 — stuck spinner)
        _queue.value.find { it.id == downloadId }?.let { download ->
            download.status = Download.State.NOT_DOWNLOADED
        }
        _queue.value = _queue.value.filter { it.id != downloadId }
        store.remove(downloadId)
        refreshStatusMap()
        Log.d(TAG, "cancelDownload: ✓ $downloadId")
    }

    /** Pause a download. */
    fun pauseDownload(downloadId: String) {
        Log.d(TAG, "pauseDownload: → $downloadId")
        _queue.value.find { it.id == downloadId }?.let { download ->
            download.status = Download.State.PAUSED
            store.update(downloadId, status = Download.State.PAUSED.value)
        }
        Log.d(TAG, "pauseDownload: ✓ $downloadId")
    }

    /** Resume a paused download. */
    fun resumeDownload(downloadId: String) {
        Log.d(TAG, "resumeDownload: → $downloadId")
        _queue.value.find { it.id == downloadId }?.let { download ->
            download.status = Download.State.QUEUE
            store.update(downloadId, status = Download.State.QUEUE.value)
        }
        startWork()
        Log.d(TAG, "resumeDownload: ✓ $downloadId")
    }

    /** Pause all downloads. */
    fun pauseAll() {
        Log.d(TAG, "pauseAll: → pausing all downloads")
        _queue.value.forEach { download ->
            if (download.status == Download.State.DOWNLOADING ||
                download.status == Download.State.QUEUE ||
                download.status == Download.State.RESOLVING) {
                download.status = Download.State.PAUSED
                store.update(download.id, status = Download.State.PAUSED.value)
            }
        }
        Log.d(TAG, "pauseAll: ✓ all paused")
    }

    /** Resume all paused downloads. */
    fun resumeAll() {
        Log.d(TAG, "resumeAll: → resuming all paused downloads")
        _queue.value.forEach { download ->
            if (download.status == Download.State.PAUSED) {
                download.status = Download.State.QUEUE
                store.update(download.id, status = Download.State.QUEUE.value)
            }
        }
        startWork()
        Log.d(TAG, "resumeAll: ✓ all resumed")
    }

    /** Retry a failed download. */
    fun retryDownload(downloadId: String) {
        Log.d(TAG, "retryDownload: → $downloadId")
        val download = _queue.value.find { it.id == downloadId } ?: return
        download.status = Download.State.QUEUE
        download.progress = 0
        download.error = null
        store.update(downloadId, status = Download.State.QUEUE.value, progress = 0, error = null)
        startWork()
        Log.d(TAG, "retryDownload: ✓ $downloadId")
    }

    /** Remove a completed download from the queue. */
    fun removeDownload(downloadId: String) {
        Log.d(TAG, "removeDownload: → $downloadId")
        _queue.value = _queue.value.filter { it.id != downloadId }
        store.remove(downloadId)
        refreshStatusMap()
    }

    /** Clear all completed downloads from the queue. */
    fun clearCompleted() {
        Log.d(TAG, "clearCompleted: → clearing completed downloads")
        _queue.value = _queue.value.filter { it.status != Download.State.DOWNLOADED }
        store.clearCompleted()
        refreshStatusMap()
    }

    /** Check if an episode is downloaded (for offline playback). */
    fun isEpisodeDownloaded(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val provider = Injekt.get<DownloadProvider>()
        return provider.isEpisodeDownloaded(episodeName, animeTitle, sourceName)
    }

    /** Get the downloaded video file URI for offline playback. */
    fun getDownloadedVideoUri(episodeName: String, animeTitle: String, sourceName: String): String? {
        val provider = Injekt.get<DownloadProvider>()
        val file = provider.getDownloadedVideoFile(episodeName, animeTitle, sourceName) ?: return null
        return file.uri.toString()
    }

    /** Delete a downloaded episode. */
    fun deleteDownloadedEpisode(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val provider = Injekt.get<DownloadProvider>()
        return provider.deleteEpisode(episodeName, animeTitle, sourceName)
    }

    /** Start the WorkManager job (unique work — only one instance runs at a time). */
    private fun startWork() {
        Log.d(TAG, "startWork: → starting download worker")
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.downloadOverWifiOnly().get()) NetworkType.UNMETERED
                else NetworkType.CONNECTED
            )
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .addTag(UNIQUE_WORK)
            .build()

        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
        Log.d(TAG, "startWork: ✓ worker enqueued")
    }

    /** Restore the queue from persistent store on app start. */
    private fun restoreQueue() {
        val entries = store.getAll()
        if (entries.isNotEmpty()) {
            entries.forEach { addToLiveQueue(it) }
            Log.d(TAG, "restoreQueue: ✓ restored ${entries.size} downloads from store")
        }
    }

    /** Add a store entry to the live in-memory queue. */
    private fun addToLiveQueue(entry: DownloadStore.DownloadStoreEntry) {
        val download = Download(
            id = entry.id,
            anilistId = entry.anilistId,
            sourceId = entry.sourceId,
            sourceName = entry.sourceName,
            animeTitle = entry.animeTitle,
            episodeUrl = entry.episodeUrl,
            episodeName = entry.episodeName,
            episodeNumber = entry.episodeNumber,
            order = entry.order,
        ).apply {
            status = Download.State.fromValue(entry.status)
            progress = entry.progress
            totalSize = entry.totalSize
            downloadedBytes = entry.downloadedBytes
            error = entry.error
        }
        _queue.value = _queue.value + download
        refreshStatusMap()

        // Observe this download's statusFlow AND progressFlow and update the maps when they change.
        // This is the fix for bug B3 — status changes now propagate to the UI reactively.
        // Also fixes C5 — progress changes propagate for determinate progress display.
        GlobalScope.launch {
            download.statusFlow.collect { _ -> refreshStatusMap() }
        }
        GlobalScope.launch {
            download.progressFlow.collect { _ -> refreshStatusMap() }
        }
    }

    /**
     * Update a download's state in both the live queue and persistent store.
     * Called by the worker when status/progress changes.
     */
    fun updateDownloadState(
        downloadId: String,
        status: Download.State? = null,
        progress: Int? = null,
        totalSize: Long? = null,
        downloadedBytes: Long? = null,
        error: String? = null,
    ) {
        _queue.value.find { it.id == downloadId }?.let { download ->
            if (status != null) download.status = status
            if (progress != null) download.progress = progress
            if (totalSize != null) download.totalSize = totalSize
            if (downloadedBytes != null) download.downloadedBytes = downloadedBytes
            if (error != null) download.error = error
        }
        store.update(
            downloadId,
            status = status?.value,
            progress = progress,
            error = error,
        )
    }
}
