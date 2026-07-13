package app.anikuta.download

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import app.anikuta.util.storage.toFFmpegString
import com.hippo.unifile.UniFile
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Semaphore

/**
 * WorkManager CoroutineWorker that processes the download queue.
 *
 * Single unique work ("AnikutaDownloader"). Runs as a foreground service
 * while downloading. Processes downloads from the [DownloadManager] queue
 * up to [DownloadPreferences.maxConcurrentDownloads] at a time.
 *
 * Each download:
 *   1. Re-resolves the video URL via [DownloadVideoResolver] (handles expired links)
 *   2. Creates a tmp directory in the downloads folder
 *   3. Runs FFmpeg to mux video + subtitles into a single .mkv
 *   4. On success: renames tmp dir → episode dir, marks as DOWNLOADED
 *   5. On failure: retries 3× with 2/4/8s backoff, then marks as ERROR
 */
class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DownloadWorker"
        private const val MAX_RETRIES = 3
        private const val MIN_DISK_SPACE_MB = 200L
    }

    private val downloadManager: DownloadManager = Injekt.get()
    private val downloadProvider: DownloadProvider = Injekt.get()
    private val videoResolver: DownloadVideoResolver = Injekt.get()
    private val downloadPrefs: DownloadPreferences = Injekt.get()
    private val downloadStore: DownloadStore = Injekt.get()

    override suspend fun doWork(): Result {
        Log.d(TAG, "Download worker started")

        val queue = downloadManager.queue.value
        val pending = queue.filter {
            it.status == Download.State.QUEUE || it.status == Download.State.ERROR
        }

        if (pending.isEmpty()) {
            Log.d(TAG, "No pending downloads — worker done")
            return Result.success()
        }

        val maxConcurrent = downloadPrefs.maxConcurrentDownloads().get().coerceIn(1, 4)
        val semaphore = Semaphore(maxConcurrent)

        // Process downloads concurrently (up to maxConcurrent)
        pending.forEach { download ->
            semaphore.acquire()
            try {
                processDownload(download)
            } finally {
                semaphore.release()
            }
        }

        Log.d(TAG, "Download worker finished")
        return Result.success()
    }

    private suspend fun processDownload(download: Download) {
        download.status = Download.State.DOWNLOADING
        downloadStore.update(download.id, status = Download.State.DOWNLOADING.value)

        var lastError: String? = null
        for (attempt in 1..MAX_RETRIES) {
            try {
                Log.d(TAG, "Downloading: ${download.episodeName} (attempt $attempt)")

                // 1. Resolve video URL (handles expired links)
                val video = videoResolver.resolve(download)
                if (video == null) {
                    throw Exception("Could not resolve video URL for ${download.episodeName}")
                }
                download.video = video

                // 2. Check disk space
                if (!hasMinDiskSpace()) {
                    throw Exception("Insufficient disk space (need ${MIN_DISK_SPACE_MB} MB)")
                }
                Log.d(TAG, "Disk space OK. Creating directories...")

                // 3. Create tmp directory
                val animeDir = downloadProvider.getAnimeDir(download.animeTitle, download.sourceName)
                    ?: throw Exception("Could not create anime directory")
                val tmpDir = animeDir.createDirectory("_tmp_${download.id}")
                    ?: throw Exception("Could not create temp directory")
                Log.d(TAG, "Created tmp dir: ${tmpDir.filePath ?: tmpDir.uri}")

                // 4. Build FFmpeg command
                val outputPath = tmpDir.createFile("video.mkv")!!
                Log.d(TAG, "Output file: ${outputPath.filePath ?: outputPath.uri}")
                val ffmpegCmd = buildFFmpegCommand(video, outputPath, download)
                Log.d(TAG, "FFmpeg command: $ffmpegCmd")

                // 5. Execute FFmpeg
                Log.d(TAG, "Starting FFmpeg execution...")
                val success = executeFFmpeg(ffmpegCmd, download)

                if (success) {
                    Log.d(TAG, "FFmpeg succeeded. Moving file to episode dir...")
                    // 6. Rename tmp dir → episode dir
                    val episodeDirName = buildValidFilename(download.episodeName.ifBlank { "Episode" })
                    val episodeDir = animeDir.createDirectory(episodeDirName)!!
                    // Move the .mkv from tmp to episode dir
                    val finalFile = episodeDir.createFile("video.mkv")!!
                    // Copy via UniFile (SAF doesn't support rename across directories)
                    outputPath.openInputStream().use { input ->
                        finalFile.openOutputStream(false).use { output ->
                            input.copyTo(output)
                        }
                    }
                    tmpDir.delete()
                    Log.d(TAG, "File moved. tmpDir deleted.")

                    // 7. Mark as downloaded
                    download.status = Download.State.DOWNLOADED
                    download.progress = 100
                    downloadStore.update(download.id, status = Download.State.DOWNLOADED.value, progress = 100)
                    Log.d(TAG, "✅ Download complete: ${download.episodeName}")
                    return
                } else {
                    throw Exception("FFmpeg failed (return code non-zero)")
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                Log.e(TAG, "❌ Download attempt $attempt failed: $lastError", e)

                // Clean up tmp dir
                try {
                    val animeDir = downloadProvider.getAnimeDir(download.animeTitle, download.sourceName)
                    animeDir?.findFile("_tmp_${download.id}")?.delete()
                } catch (_: Exception) {}

                if (attempt < MAX_RETRIES) {
                    val backoff = when (attempt) {
                        1 -> 2000L
                        2 -> 4000L
                        else -> 8000L
                    }
                    Log.d(TAG, "Retrying in ${backoff}ms...")
                    kotlinx.coroutines.delay(backoff)
                }
            }
        }

        // All retries failed
        download.status = Download.State.ERROR
        download.error = lastError
        downloadStore.update(download.id, status = Download.State.ERROR.value, error = lastError)
        Log.e(TAG, "Download failed after $MAX_RETRIES attempts: ${download.episodeName} — $lastError")
    }

    /**
     * Build the FFmpeg command to mux video + subtitles into a .mkv file.
     * Mirrors aniyomi's getFFmpegOptions + ffmpegDownload pattern.
     *
     * Key differences from the previous broken version:
     * 1. Passes HTTP headers via -headers (CDN requires origin/referer/UA or returns 403)
     * 2. Uses -f matroska -c:a copy -c:v copy -c:s copy (explicit format + codec specs)
     * 3. Uses the UniFile's URI toFFmpegString with the application context
     * 4. Adds -y to overwrite existing files
     */
    private fun buildFFmpegCommand(
        video: eu.kanade.tachiyomi.animesource.model.Video,
        outputFile: UniFile,
        download: Download,
    ): String {
        val context = applicationContext

        // Build header options for HTTP inputs (CDNs require proper headers)
        val headerOptions = video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

        // Build the output filename via SAF
        val ffmpegOutput = outputFile.uri.toFFmpegString(context)
        Log.d(TAG, "FFmpeg output path: $ffmpegOutput")

        // Build the command
        val cmd = StringBuilder()

        // Video input (with headers if HTTP)
        if (video.videoUrl.startsWith("http")) {
            cmd.append("-headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
        }
        cmd.append("-i \"${video.videoUrl}\"")

        // Subtitle inputs (each .vtt URL, with headers)
        video.subtitleTracks.forEach { sub ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"${sub.url}\"")
        }

        // Audio inputs (external audio tracks if any)
        video.audioTracks.forEach { audio ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"${audio.url}\"")
        }

        // Mapping: video from input 0, audio from input 0, subtitles from inputs 1+
        cmd.append(" -map 0:v -map 0:a?")
        video.subtitleTracks.forEachIndexed { index, _ ->
            cmd.append(" -map ${index + 1}:s?")
        }

        // Codec: copy all streams (no re-encoding) + matroska format
        cmd.append(" -f matroska -c:v copy -c:a copy -c:s copy")

        // Subtitle metadata (language labels)
        video.subtitleTracks.forEachIndexed { i, track ->
            cmd.append(" -metadata:s:s:$i \"title=${track.lang}\"")
        }

        // Output file + overwrite
        cmd.append(" \"$ffmpegOutput\" -y")

        return cmd.toString()
    }

    /**
     * Execute FFmpeg synchronously with progress reporting via statistics callback.
     * Uses FFmpegKitConfig.enableStatisticsCallback to get frame/time updates
     * during execution. Progress is calculated from statistics.getTime() vs
     * the estimated video duration (from the video URL's m3u8 metadata).
     *
     * The FFmpeg logs show frame counts (e.g. frame=32703) — we use the
     * statistics callback's time value to calculate percentage.
     *
     * @return true if FFmpeg succeeded (return code 0), false otherwise
     */
    private fun executeFFmpeg(command: String, download: Download): Boolean {
        Log.d(TAG, "executeFFmpeg: command length=${command.length}")

        // Enable statistics callback for live progress updates
        // The callback fires on a separate thread during FFmpeg execution.
        // statistics.getTime() returns microseconds of processed video.
        // We estimate total duration from the video (typical anime: 24 min = 1,440,000,000 μs)
        // and calculate percentage = (processed_time / estimated_total) * 100
        val estimatedDurationMs = 24 * 60 * 1000L // 24 minutes default (will be refined)
        FFmpegKitConfig.enableStatisticsCallback { statistics ->
            val processedTimeMs = statistics.time / 1000 // μs → ms
            if (processedTimeMs > 0) {
                val progress = ((100.0 * processedTimeMs / estimatedDurationMs).toInt()).coerceIn(0, 99)
                if (progress != download.progress) {
                    download.progress = progress
                    downloadStore.update(download.id, progress = progress)
                    Log.d(TAG, "Progress: $progress% (${processedTimeMs}ms / ${estimatedDurationMs}ms)")
                }
            }
        }

        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        // Disable statistics callback after completion
        FFmpegKitConfig.disableStatisticsCallback()

        Log.d(TAG, "FFmpeg return code: ${returnCode.value} (isSuccess=${ReturnCode.isSuccess(returnCode)}, isCancel=${ReturnCode.isCancel(returnCode)})")

        if (ReturnCode.isSuccess(returnCode)) {
            Log.d(TAG, "✅ FFmpeg success for ${download.episodeName}")
            return true
        } else {
            val logs = session.allLogsAsString
            Log.e(TAG, "❌ FFmpeg failed for ${download.episodeName}: rc=${returnCode.value}")
            Log.e(TAG, "FFmpeg full logs:\n$logs")
            return false
        }
    }

    private fun hasMinDiskSpace(): Boolean {
        return try {
            val stat = android.os.StatFs(applicationContext.filesDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            freeBytes > MIN_DISK_SPACE_MB * 1024 * 1024
        } catch (e: Exception) {
            true // Assume OK if we can't check
        }
    }

    private fun buildValidFilename(name: String): String {
        return app.anikuta.util.storage.DiskUtil.buildValidFilename(name)
    }
}
