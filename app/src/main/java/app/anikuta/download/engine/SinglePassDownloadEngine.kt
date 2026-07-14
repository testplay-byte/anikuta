package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadPreferences
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Headers
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-pass FFmpeg download engine — mirrors aniyomi's approach exactly.
 *
 * Uses ONE FFmpeg call with the proxy URL (no -ss, no -t, no segments):
 *   ffmpeg -headers '...' -i "http://localhost:PORT/m3u8?url=..." \
 *          -map 0:v -map 0:a? -map 0:s? -c copy -f matroska output.mkv
 *
 * FFmpeg's HLS demuxer:
 * 1. Fetches m3u8 through the proxy (native HTTP, not OkHttp)
 * 2. Downloads .ts segments through the proxy (CDN auth handled by proxy)
 * 3. Muxes into one .mkv with correct duration and size
 * 4. No -ss seeking = no duplicate content
 *
 * This is the proven approach used by aniyomi. The only downside is no resume
 * after failure — but aniyomi has the same limitation and it works fine.
 *
 * Progress is tracked via FFmpeg's statistics callback (statistics.time vs
 * FFprobe duration), same as aniyomi.
 */
class SinglePassDownloadEngine(
    private val context: Context,
    private val provider: DownloadProvider,
    private val resolver: DownloadVideoResolver,
    private val progressTracker: ProgressTracker,
    private val downloadPrefs: DownloadPreferences,
) : DownloadEngine {

    companion object {
        private const val TAG = "SinglePassEngine"
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val CACHE_BASE_DIR = "anikuta_dl"
    }

    private data class CancelState(@Volatile var paused: Boolean, @Volatile var cancelled: Boolean)
    private val cancelStates = ConcurrentHashMap<String, CancelState>()

    override suspend fun resolve(download: Download): Boolean {
        Log.d(TAG, "resolve: → ${download.episodeName}")
        download.status = Download.State.RESOLVING
        return try {
            val video = resolver.resolve(download)
            if (video == null) {
                download.error = "Could not resolve video URL"
                return false
            }
            download.video = video
            val parsed = app.anikuta.ui.detail.VideoTitleParser.parse(video)
            download.serverName = parsed.server
            download.audioVersion = parsed.audio.name
            download.qualityLabel = "${parsed.quality ?: 0}p"
            Log.d(TAG, "resolve: ✓ ${video.videoTitle} (${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
            true
        } catch (e: Exception) {
            download.error = "Resolve failed: ${e.message}"
            false
        }
    }

    override suspend fun download(download: Download): Boolean {
        Log.d(TAG, "download: → ${download.episodeName}")
        download.status = Download.State.DOWNLOADING
        progressTracker.resetSpeed()

        // NOTE: Do NOT call disableRedirection() here — it also disables statistics
        // callbacks, which we need for progress tracking. FFmpeg logs will appear in
        // logcat but that's acceptable for a single FFmpeg call (not 144 like the
        // segment engine).

        if (!hasMinDiskSpace()) {
            download.error = "Insufficient disk space"
            return false
        }

        val video = download.video ?: run {
            if (!resolve(download)) return false
            download.video ?: return false
        }

        val cacheDir = getCacheDir(download)
        val outputFile = File(cacheDir, FINAL_VIDEO_NAME)

        // Build FFmpeg command — mirrors aniyomi's getFFmpegOptions
        val cmd = buildFFmpegCommand(video, outputFile.absolutePath)
        Log.d(TAG, "download: FFmpeg command length=${cmd.length}")

        // Get duration for progress estimation
        val durationMs = getVideoDurationMs(video)
        Log.d(TAG, "download: estimated duration=${durationMs}ms (${durationMs / 1000 / 60}m${(durationMs / 1000) % 60}s)")

        // Progress tracking via file-size polling.
        //
        // FFmpegKit's statistics callback doesn't fire reliably in our setup (the aniyomi
        // fork may behave differently). Instead, we poll the output file size on disk
        // every 500ms. This is more reliable and mirrors what users see in file managers.
        //
        // We estimate total size from the FFprobe duration × typical bitrate (250 KB/s for 360p).
        // As the file grows, we refine the estimate based on actual download speed.
        val estimatedTotalBytes = (durationMs * 250 / 1000).toLong().coerceAtLeast(1_000_000) // at least 1MB
        progressTracker.setTotalSize(download, estimatedTotalBytes)
        Log.d(TAG, "download: estimated total size=${estimatedTotalBytes / 1024 / 1024}MB")

        // Start FFmpeg async
        download.status = Download.State.DOWNLOADING
        val args = FFmpegKitConfig.parseArguments(cmd)
        Log.d(TAG, "download: executing FFmpeg async with ${args.size} args")

        var ffmpegFinished = false
        var ffmpegSession: com.arthenica.ffmpegkit.FFmpegSession? = null

        val ffmpegJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            ffmpegSession = com.arthenica.ffmpegkit.FFmpegKit.executeWithArgumentsAsync(
                args,
                { session -> ffmpegFinished = true },
                null,
                null, // no statistics callback — we poll file size instead
            )
        }

        // Poll output file size for progress
        var lastFileSize = 0L
        var stableSizeCount = 0
        var calibratedTotal = estimatedTotalBytes
        val pollIntervalMs = 500L
        val startTime = System.currentTimeMillis()

        while (!ffmpegFinished) {
            // Check cancellation
            val cs = cancelStates[download.id]
            if (cs?.cancelled == true || download.status == Download.State.NOT_DOWNLOADED) {
                ffmpegSession?.cancel()
                break
            }
            if (cs?.paused == true || download.status == Download.State.PAUSED) {
                ffmpegSession?.cancel()
                break
            }

            // Read file size
            val currentSize = if (outputFile.exists()) outputFile.length() else 0L

            if (currentSize > 0) {
                download.downloadedBytes = currentSize

                // Detect if file size has stabilized (FFmpeg finished writing but
                // hasn't signaled completion yet). Re-calibrate total.
                if (currentSize == lastFileSize) {
                    stableSizeCount++
                    if (stableSizeCount >= 4 && currentSize < calibratedTotal) {
                        // File stopped growing — likely done
                        calibratedTotal = currentSize
                        progressTracker.setTotalSize(download, calibratedTotal)
                    }
                } else {
                    stableSizeCount = 0
                    lastFileSize = currentSize

                    // Refine total estimate based on elapsed time and current size.
                    // If we're downloading at X bytes/sec, and the video is Y seconds long,
                    // total ≈ X * Y. But we don't know the real duration — so we use
                    // the FFprobe duration as an upper bound and refine downward.
                    val elapsedMs = System.currentTimeMillis() - startTime
                    if (elapsedMs > 2000 && currentSize > 0) {
                        val bytesPerMs = currentSize.toDouble() / elapsedMs
                        val estimatedFromRate = (bytesPerMs * durationMs).toLong()
                        // Use the smaller of: rate-based estimate or current calibrated total
                        if (estimatedFromRate > 0 && estimatedFromRate < calibratedTotal) {
                            calibratedTotal = estimatedFromRate
                            progressTracker.setTotalSize(download, calibratedTotal)
                        }
                    }
                }

                // Calculate progress
                val progress = if (calibratedTotal > 0) {
                    ((currentSize.toDouble() / calibratedTotal) * 100).toInt().coerceIn(0, 99)
                } else 0

                if (progress != download.progress && progress > 0) {
                    download.progress = progress
                    Log.d(TAG, "download: progress=$progress% (${currentSize / 1024 / 1024}MB / ~${calibratedTotal / 1024 / 1024}MB)")
                }
            }

            delay(pollIntervalMs)
        }

        // Wait for the FFmpeg job to fully complete
        ffmpegJob.join()

        val session = ffmpegSession

        val cs = cancelStates[download.id]
        if (cs?.cancelled == true || download.status == Download.State.NOT_DOWNLOADED) {
            Log.d(TAG, "download: ✕ cancelled")
            cleanupCache(download)
            return false
        }

        if (!ReturnCode.isSuccess(session.returnCode)) {
            download.error = "FFmpeg failed (rc=${session.returnCode.value})"
            Log.e(TAG, "download: ❌ ${download.error}")
            cleanupCache(download)
            return false
        }

        if (!outputFile.exists() || outputFile.length() == 0L) {
            download.error = "Output file is empty"
            Log.e(TAG, "download: ❌ ${download.error}")
            cleanupCache(download)
            return false
        }

        val actualSize = outputFile.length()
        Log.d(TAG, "download: ✓ FFmpeg success, size=${formatBytes(actualSize)}")

        // FFprobe the final file for actual duration + resolution
        val mediaInfo = getMediaInfo(outputFile)
        download.actualDurationMs = mediaInfo.durationMs
        download.actualResolution = mediaInfo.resolution
        progressTracker.setTotalSize(download, actualSize)
        download.downloadedBytes = actualSize
        Log.d(TAG, "download: final mkv duration=${mediaInfo.durationMs}ms, resolution=${mediaInfo.resolution}")

        // Copy to SAF
        if (!copyMkvToSaf(outputFile, download)) {
            download.error = "Failed to copy video to storage"
            return false
        }

        // Write .episode_url file
        provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?.let { provider.writeEpisodeUrlFile(it, download.episodeUrl) }

        // Cleanup
        cleanupCache(download)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName} " +
            "(${formatBytes(actualSize)}, ${mediaInfo.resolution}, ${download.serverName}/${download.audioVersion}/${download.qualityLabel})")
        return true
    }

    /**
     * Build the FFmpeg command — mirrors aniyomi's getFFmpegOptions exactly.
     * No -ss, no -t — single pass with -c copy.
     */
    private fun buildFFmpegCommand(video: Video, outputPath: String): String {
        val headerOptions = buildHeaderOptions(video)

        val cmd = StringBuilder()

        // Video input with headers
        if (video.videoUrl.startsWith("http")) {
            cmd.append("-headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
        }
        cmd.append("-i \"").append(video.videoUrl).append("\"")

        // Subtitle inputs
        video.subtitleTracks.forEach { track ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"").append(track.url).append("\"")
        }

        // Audio inputs (external audio tracks if any)
        video.audioTracks.forEach { track ->
            cmd.append(" -headers '")
            cmd.append(headerOptions.replace("'", "\\'"))
            cmd.append("' ")
            cmd.append("-i \"").append(track.url).append("\"")
        }

        // Mapping: video from input 0, audio from input 0, subtitles from extra inputs
        cmd.append(" -map 0:v -map 0:a?")
        video.subtitleTracks.forEachIndexed { i, _ ->
            cmd.append(" -map ").append(i + 1).append(":s?")
        }

        // Codec: copy all streams (no re-encoding)
        cmd.append(" -c copy -f matroska")

        // Subtitle metadata
        video.subtitleTracks.forEachIndexed { i, track ->
            cmd.append(" -metadata:s:s:").append(i).append(" \"title=").append(track.lang).append("\"")
        }

        // Output file
        cmd.append(" \"").append(outputPath).append("\" -y")

        return cmd.toString()
    }

    private fun buildHeaderOptions(video: Video): String {
        return video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private suspend fun getVideoDurationMs(video: Video): Long = withContext(Dispatchers.IO) {
        try { FFmpegKitConfig.enableRedirection() } catch (_: Exception) {}
        val headerOptions = buildHeaderOptions(video)
        val cmd = if (video.videoUrl.startsWith("http")) {
            "-headers '$headerOptions' -i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        } else {
            "-i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        }
        val session = FFprobeKit.execute(cmd)
        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}
        val output = session.output?.trim()
        if (ReturnCode.isSuccess(session.returnCode) && !output.isNullOrEmpty()) {
            val durationSec = output.toDoubleOrNull() ?: 0.0
            (durationSec * 1000).toLong()
        } else {
            24 * 60 * 1000L // fallback
        }
    }

    private data class MediaInfo(val durationMs: Long, val resolution: String)

    private suspend fun getMediaInfo(file: File): MediaInfo = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext MediaInfo(0L, "")
        try { FFmpegKitConfig.enableRedirection() } catch (_: Exception) {}

        val durCmd = "-i \"${file.absolutePath}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        val durSession = FFprobeKit.execute(durCmd)
        val durStr = durSession.output?.trim()
        val durationMs = if (ReturnCode.isSuccess(durSession.returnCode) && !durStr.isNullOrEmpty()) {
            (durStr.toDoubleOrNull() ?: 0.0) * 1000
        } else 0L

        val resCmd = "-i \"${file.absolutePath}\" -show_entries stream=width,height -select_streams v:0 -v quiet -of csv=\"p=0\""
        val resSession = FFprobeKit.execute(resCmd)
        val resStr = resSession.output?.trim()
        val resolution = if (ReturnCode.isSuccess(resSession.returnCode) && !resStr.isNullOrEmpty()) {
            resStr.replace(",", "x")
        } else ""

        try { FFmpegKitConfig.disableRedirection() } catch (_: Exception) {}
        MediaInfo(durationMs.toLong(), resolution)
    }

    fun resetFlags(downloadId: String) {
        cancelStates[downloadId] = CancelState(false, false)
    }

    override suspend fun pause(download: Download) {
        cancelStates[download.id]?.paused = true
        // Cancel the running FFmpeg session — it will stop
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        delay(500)
        download.status = Download.State.PAUSED
    }

    override suspend fun cancel(download: Download) {
        cancelStates[download.id]?.cancelled = true
        try { FFmpegKit.cancel() } catch (_: Exception) {}
        delay(300)
        cleanupCache(download)
        download.status = Download.State.NOT_DOWNLOADED
    }

    override fun isCompleted(download: Download): Boolean {
        return provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName) != null
    }

    private suspend fun copyMkvToSaf(cacheMkv: File, download: Download): Boolean = withContext(Dispatchers.IO) {
        if (!cacheMkv.exists() || cacheMkv.length() == 0L) return@withContext false
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext false
        val safFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: return@withContext false
        try {
            cacheMkv.inputStream().use { input ->
                safFile.openOutputStream(false).use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "copyMkvToSaf: ✓ ${formatBytes(cacheMkv.length())} → SAF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyMkvToSaf: ❌ ${e.message}")
            false
        }
    }

    private fun getCacheDir(download: Download): File {
        return File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}").apply { mkdirs() }
    }

    private fun hasMinDiskSpace(): Boolean {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            freeBytes > MIN_DISK_SPACE_MB * 1024 * 1024
        } catch (e: Exception) { true }
    }

    private fun cleanupCache(download: Download) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}")
            if (cacheDir.exists()) cacheDir.deleteRecursively()
        } catch (_: Exception) {}
    }
}
