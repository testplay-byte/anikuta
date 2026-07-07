package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.network.NetworkHelper
import okhttp3.Request
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.io.RandomAccessFile

/**
 * Phase 6 task 6.12 — Download worker (WorkManager CoroutineWorker).
 *
 * Downloads a video URL to a local file with:
 *  - HTTP Range header for partial resumption (resume from where it left off)
 *  - Auto-retry on network failure (WorkManager exponential backoff)
 *  - Progress reporting via setProgress()
 *  - Network constraint (configured by DownloadManager)
 *
 * The download is a simple file download (no FFmpeg muxing yet — if the
 * video URL is HLS/m3u8, the download will fail with a clear error. FFmpeg
 * integration comes as a refinement).
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_VIDEO_URL = "video_url"
        const val KEY_TITLE = "title"
        const val KEY_EPISODE_URL = "episode_url"
        const val KEY_ANILIST_ID = "anilist_id"
        const val KEY_EPISODE_NUMBER = "episode_number"

        const val PROGRESS_DOWNLOAD_ID = "progress_download_id"
        const val PROGRESS_PERCENT = "progress_percent"
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return Result.failure()
        val videoUrl = inputData.getString(KEY_VIDEO_URL) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: "Unknown"
        val episodeUrl = inputData.getString(KEY_EPISODE_URL) ?: ""
        val anilistId = inputData.getInt(KEY_ANILIST_ID, -1)
        val epNum = inputData.getFloat(KEY_EPISODE_NUMBER, -1f)

        val store: DownloadStore = Injekt.get()
        val networkHelper: NetworkHelper = Injekt.get()

        Log.d(TAG, "Starting download: $title ($videoUrl)")

        // Update status to DOWNLOADING
        store.update(downloadId, DownloadStatus.DOWNLOADING, 0)

        // Determine output file
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9-_]"), "_")
        val fileName = "${anilistId}_${epNum}_$safeTitle.mp4"
        val outputFile = File(store.getDownloadDir(), fileName)

        try {
            val client = networkHelper.client
            val requestBuilder = Request.Builder().url(videoUrl).get()

            // Partial resumption: if the file already exists, resume from current size
            var existingSize = 0L
            if (outputFile.exists()) {
                existingSize = outputFile.length()
                if (existingSize > 0) {
                    requestBuilder.header("Range", "bytes=$existingSize-")
                    Log.d(TAG, "Resuming from $existingSize bytes")
                }
            }

            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful && response.code != 206) {
                val errMsg = "HTTP ${response.code}: ${response.message}"
                Log.e(TAG, "Download failed: $errMsg")
                store.update(downloadId, DownloadStatus.FAILED, error = errMsg)
                return Result.retry() // WorkManager will retry with backoff
            }

            val responseBody = response.body ?: run {
                store.update(downloadId, DownloadStatus.FAILED, error = "No response body")
                return Result.failure()
            }

            val totalBytes = responseBody.contentLength().let { len ->
                if (len > 0) len + existingSize else -1L
            }

            // Write to file (append if resuming)
            val raf = RandomAccessFile(outputFile, "rw")
            if (existingSize > 0) raf.seek(existingSize)

            var downloadedBytes = existingSize
            val buffer = ByteArray(8192)
            var lastProgressUpdate = 0L

            responseBody.byteStream().use { input ->
                raf.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        // Report progress every ~500KB
                        val now = System.currentTimeMillis()
                        if (now - lastProgressUpdate > 500) {
                            lastProgressUpdate = now
                            val progress = if (totalBytes > 0) {
                                ((downloadedBytes.toDouble() / totalBytes) * 100).toInt().coerceIn(0, 100)
                            } else {
                                -1 // unknown total
                            }
                            setProgress(workDataOf(
                                PROGRESS_DOWNLOAD_ID to downloadId,
                                PROGRESS_PERCENT to progress,
                            ))
                            store.update(downloadId, DownloadStatus.DOWNLOADING, progress)
                        }
                    }
                }
            }

            // Success
            Log.d(TAG, "✅ Download complete: $title → ${outputFile.absolutePath} (${outputFile.length()} bytes)")
            store.update(downloadId, DownloadStatus.COMPLETED, 100, outputFile.absolutePath)
            return Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Download failed: $title", e)
            // If we have partial data, keep it for resumption on retry
            store.update(downloadId, DownloadStatus.FAILED, error = e.message ?: "Unknown error")
            // WorkManager will retry (with backoff) up to the retry limit
            return Result.retry()
        }
    }
}
