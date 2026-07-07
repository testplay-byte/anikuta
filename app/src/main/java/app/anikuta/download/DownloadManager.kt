package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.anikuta.core.preference.PreferenceStore
import app.anikuta.data.anilist.model.AniListAnime
import app.anikuta.source.api.model.SEpisode
import app.anikuta.source.api.model.Video
import java.util.UUID

/**
 * Phase 6 task 6.12 — Download manager.
 *
 * Wraps WorkManager to enqueue/cancel downloads. Provides a clean interface
 * so the download implementation can be swapped/extended later (user request:
 * "implement in a better cleaner way so that we can change it easily later").
 *
 * Features:
 *  - WorkManager-based (survives app restart)
 *  - Auto-retry on network failure (exponential backoff)
 *  - Partial download resumption (HTTP Range)
 *  - Network constraint (WiFi-only option)
 *  - Progress reporting via WorkManager setProgress()
 */
class DownloadManager(
    private val context: Context,
    private val store: DownloadStore,
    private val prefs: DownloadPreferences,
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val WORK_TAG = "anikuta_download"
    }

    private val workManager: WorkManager = WorkManager.getInstance(context)

    /**
     * Enqueue a download for an episode.
     * @param anilistId AniList anime ID
     * @param episode The episode to download
     * @param video The resolved video (URL + quality)
     * @param animeTitle Title for the download entry
     */
    fun enqueueDownload(
        anilistId: Int,
        episode: SEpisode,
        video: Video,
        animeTitle: String,
    ): String {
        val downloadId = "dl_${anilistId}_${episode.episode_number}_${System.currentTimeMillis()}"
        val title = "$animeTitle - Episode ${episode.episode_number}"

        // Add to store
        store.add(DownloadEntry(
            id = downloadId,
            anilistId = anilistId,
            episodeUrl = episode.url,
            videoUrl = video.videoUrl,
            title = title,
            episodeNumber = episode.episode_number,
        ))

        // Build WorkManager request
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (prefs.downloadOverWifiOnly().get()) NetworkType.UNMETERED
                else NetworkType.CONNECTED
            )
            .build()

        val data = workDataOf(
            DownloadWorker.KEY_DOWNLOAD_ID to downloadId,
            DownloadWorker.KEY_VIDEO_URL to video.videoUrl,
            DownloadWorker.KEY_TITLE to title,
            DownloadWorker.KEY_EPISODE_URL to episode.url,
            DownloadWorker.KEY_ANILIST_ID to anilistId,
            DownloadWorker.KEY_EPISODE_NUMBER to episode.episode_number,
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .addTag(WORK_TAG)
            .addTag("download_$downloadId")
            .build()

        workManager.enqueue(request)
        Log.d(TAG, "Enqueued download: $title (id=$downloadId)")
        return downloadId
    }

    /** Cancel a download + remove from store. */
    fun cancelDownload(downloadId: String) {
        workManager.cancelAllWorkByTag("download_$downloadId")
        store.remove(downloadId)
        Log.d(TAG, "Cancelled download: $downloadId")
    }

    /** Remove a completed download (deletes the local file). */
    fun removeDownload(downloadId: String) {
        store.remove(downloadId)
    }

    /** Clear all completed downloads. */
    fun clearCompleted() {
        store.clearCompleted()
    }

    /** Check if an episode has been downloaded (for offline playback). */
    fun isDownloaded(episodeUrl: String): Boolean =
        store.getDownloadedFile(episodeUrl) != null

    /** Get the local file path for a downloaded episode (for offline playback). */
    fun getDownloadedFile(episodeUrl: String): String? =
        store.getDownloadedFile(episodeUrl)
}
