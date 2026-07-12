package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.anikuta.source.api.model.SEpisode
import com.hippo.unifile.UniFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Public facade for the download system. Manages the download queue lifecycle:
 * enqueue, pause, resume, cancel, retry, query.
 *
 * Delegates actual downloading to [DownloadWorker] (WorkManager) which calls
 * the FFmpeg-based download engine.
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
    val queue = _queue.asStateFlow()

    init {
        // Restore queue from persistent store on startup
        restoreQueue()
    }

    /**
     * Enqueue a single episode for download.
     * @param anilistId AniList anime ID
     * @param sourceId source ID (for re-resolution)
     * @param sourceName source display name (for directory naming)
     * @param animeTitle anime title (for directory naming)
     * @param episode the episode to download
     */
    fun enqueueDownload(
        anilistId: Int,
        sourceId: Long,
        sourceName: String,
        animeTitle: String,
        episode: SEpisode,
    ): String {
        val downloadId = "dl_${anilistId}_${episode.episode_number}_${System.currentTimeMillis()}"

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

        store.add(entry)
        addToLiveQueue(entry)
        startWork()

        Log.d(TAG, "Enqueued download: ${episode.name} (id=$downloadId)")
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
        Log.d(TAG, "Enqueued ${entries.size} downloads")
    }

    /** Cancel a download + remove from queue. */
    fun cancelDownload(downloadId: String) {
        _queue.value = _queue.value.filter { it.id != downloadId }
        store.remove(downloadId)
        Log.d(TAG, "Cancelled download: $downloadId")
    }

    /** Retry a failed download. */
    fun retryDownload(downloadId: String) {
        val download = _queue.value.find { it.id == downloadId } ?: return
        download.status = Download.State.QUEUE
        download.progress = 0
        download.error = null
        store.update(downloadId, status = Download.State.QUEUE.value, progress = 0, error = null)
        startWork()
        Log.d(TAG, "Retrying download: $downloadId")
    }

    /** Remove a completed download from the queue. */
    fun removeDownload(downloadId: String) {
        _queue.value = _queue.value.filter { it.id != downloadId }
        store.remove(downloadId)
    }

    /** Clear all completed downloads from the queue. */
    fun clearCompleted() {
        _queue.value = _queue.value.filter { it.status != Download.State.DOWNLOADED }
        store.clearCompleted()
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

        workManager.enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.REPLACE, request)
        Log.d(TAG, "Started download worker")
    }

    /** Restore the queue from persistent store on app start. */
    private fun restoreQueue() {
        val entries = store.getAll()
        if (entries.isNotEmpty()) {
            entries.forEach { addToLiveQueue(it) }
            Log.d(TAG, "Restored ${entries.size} downloads from store")
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
            status = Download.State.values().find { it.value == entry.status } ?: Download.State.QUEUE
            progress = entry.progress
            totalSize = entry.totalSize
            downloadedBytes = entry.downloadedBytes
            error = entry.error
        }
        _queue.value = _queue.value + download
    }
}
