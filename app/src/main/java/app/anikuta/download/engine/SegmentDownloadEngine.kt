package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/**
 * Segment-based download engine with full resume capability.
 *
 * Architecture:
 * 1. RESOLVING: Resolve video URL + get duration via FFprobe
 * 2. DOWNLOADING: Download in 10-second segments to CACHE DIR (real file paths)
 * 3. MUXING: Concatenate segments + mux subtitles into final .mkv in cache, then copy to SAF
 *
 * CRITICAL: Segments, subtitles, concat list, and the muxed .mkv are all stored in
 * the app-private CACHE DIR (real file paths) — NOT in SAF. This is because FFmpeg's
 * concat demuxer crashes with a SIGSEGV (null deref in saf_close) when the concat
 * list contains saf: URIs. Using real file paths avoids the SAF protocol bridge
 * entirely for the muxing phase.
 *
 * The manifest is stored in SAF (the episode dir) so it survives app kills.
 * On resume:
 * - "done" segments are checked — if the cache file is missing (cache cleared),
 *   they're marked "pending" and re-downloaded
 * - "partial" segments are deleted and re-downloaded
 * - "pending" segments are downloaded
 *
 * After successful mux, the cache dir is deleted and the manifest is removed.
 */
class SegmentDownloadEngine(
    private val context: Context,
    private val provider: DownloadProvider,
    private val resolver: DownloadVideoResolver,
    private val manifestManager: DownloadManifest,
    private val progressTracker: ProgressTracker,
) : DownloadEngine {

    companion object {
        private const val TAG = "SegmentDownloadEngine"
        private const val SEGMENT_DURATION_SEC = 10
        private const val MAX_SEGMENT_RETRIES = 3
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val CACHE_BASE_DIR = "anikuta_dl"
        private const val SEGMENTS_DIR = "segments"
        private const val SUBTITLES_DIR = "subtitles"
        private const val CONCAT_FILE = "concat.txt"
    }

    @Volatile
    private var paused = false

    @Volatile
    private var cancelled = false

    override suspend fun resolve(download: Download): Boolean {
        Log.d(TAG, "resolve: → resolving video for ${download.episodeName}")
        download.status = Download.State.RESOLVING

        return try {
            val video = resolver.resolve(download)
            if (video == null) {
                Log.e(TAG, "resolve: ❌ could not resolve video for ${download.episodeName}")
                download.error = "Could not resolve video URL"
                return false
            }
            download.video = video
            Log.d(TAG, "resolve: ✓ resolved — ${video.videoTitle ?: "untitled"} → ${video.videoUrl.take(80)}...")
            true
        } catch (e: Exception) {
            Log.e(TAG, "resolve: ❌ exception for ${download.episodeName}: ${e.message}", e)
            download.error = "Resolve failed: ${e.message}"
            false
        }
    }

    override suspend fun download(download: Download): Boolean {
        Log.d(TAG, "download: → starting for ${download.episodeName}")
        download.status = Download.State.DOWNLOADING
        progressTracker.resetSpeed()

        if (!hasMinDiskSpace()) {
            download.error = "Insufficient disk space (need ${MIN_DISK_SPACE_MB} MB)"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        val video = download.video ?: run {
            Log.e(TAG, "download: ❌ no video resolved, calling resolve first")
            if (!resolve(download)) return false
            download.video ?: return false
        }

        // Get video duration via FFprobe
        val durationMs = getVideoDurationMs(video)
        if (durationMs <= 0) {
            download.error = "Could not determine video duration"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }
        Log.d(TAG, "download: video duration = ${durationMs}ms (${durationMs / 1000 / 60} min ${(durationMs / 1000) % 60} sec)")

        // Size estimation
        val contentLength = getContentLength(video)
        if (contentLength > 0) {
            progressTracker.setTotalSize(download, contentLength)
            Log.d(TAG, "download: Content-Length = ${formatBytes(contentLength)}")
        } else {
            val estimated = durationMs * 250
            progressTracker.setTotalSize(download, estimated)
            Log.d(TAG, "download: no Content-Length, estimated = ${formatBytes(estimated)}")
        }

        // Set up cache directory for this download
        val cacheDir = getCacheDir(download)
        val segmentsDir = File(cacheDir, SEGMENTS_DIR).apply { mkdirs() }
        val subtitlesDir = File(cacheDir, SUBTITLES_DIR).apply { mkdirs() }
        Log.d(TAG, "download: cache dir = ${cacheDir.absolutePath}")

        // Load or create manifest (stored in SAF — survives app kills)
        var manifest: DownloadManifest.Manifest = manifestManager.read(download) ?: run {
            val subtitleStates = video.subtitleTracks.mapIndexed { i, track ->
                DownloadManifest.SubtitleState(
                    url = track.url,
                    lang = track.lang,
                    downloaded = false,
                    fileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt",
                )
            }
            val fresh = manifestManager.createFresh(
                download = download,
                videoUrl = video.videoUrl,
                totalDurationMs = durationMs,
                subtitles = subtitleStates,
                totalSizeBytes = if (contentLength > 0) contentLength else -1L,
            )
            manifestManager.write(download, fresh)
            Log.d(TAG, "download: ✓ fresh manifest created")
            fresh
        }

        // Verify cache files exist for "done" segments (cache may have been cleared)
        manifest = verifySegmentFiles(download, manifest, segmentsDir)

        Log.d(TAG, "download: manifest loaded — ${manifest.totalSegments} segments, " +
            "${manifestManager.getCompletedSegmentCount(manifest)} done, resuming from segment " +
            "${manifest.segments.indexOfFirst { it.status != DownloadManifest.SegmentStatus.DONE }.let { if (it < 0) -1 else it }}")

        progressTracker.updateFromManifest(download, manifest)

        // Download subtitle tracks (to cache dir — real file paths)
        manifest = downloadSubtitles(download, video, manifest, subtitlesDir)

        // Download segments (to cache dir — real file paths)
        while (true) {
            if (paused) {
                Log.d(TAG, "download: ⏸ paused — saving manifest and returning")
                download.status = Download.State.PAUSED
                manifestManager.write(download, manifest)
                return false
            }
            if (cancelled) {
                Log.d(TAG, "download: ✕ cancelled — cleaning up")
                cleanupCache(download)
                return false
            }

            val segment = manifestManager.getNextPendingSegment(manifest)
            if (segment == null) {
                Log.d(TAG, "download: ✓ all segments done")
                break
            }

            manifest = manifestManager.markSegmentDownloading(manifest, segment.index)
            manifestManager.write(download, manifest)

            val segmentFile = File(segmentsDir, segment.fileName)
            val segmentResult = downloadSegment(download, video, segment, manifest, segmentFile)

            if (cancelled || paused) continue

            if (segmentResult.success) {
                manifest = manifestManager.markSegmentDone(manifest, segment.index, segmentResult.sizeBytes)
                manifestManager.write(download, manifest)
                progressTracker.updateFromManifest(download, manifest)
                Log.d(TAG, "download: ✓ segment ${segment.index}/${manifest.totalSegments} done " +
                    "(${formatBytes(segmentResult.sizeBytes)})")
            } else {
                manifest = manifestManager.markSegmentPartial(manifest, segment.index)
                manifestManager.write(download, manifest)
                Log.w(TAG, "download: ⚠ segment ${segment.index} failed — marked partial")
            }

            yield()
        }

        if (!manifestManager.allSegmentsDone(manifest)) {
            download.error = "Some segments failed to download"
            Log.e(TAG, "download: ❌ not all segments done — ${download.error}")
            return false
        }

        // MUXING phase — concatenate segments + mux subtitles (all in cache dir — real file paths)
        download.status = Download.State.MUXING
        Log.d(TAG, "download: → muxing phase — concatenating ${manifest.totalSegments} segments")

        val muxSuccess = muxSegments(download, video, manifest, cacheDir, segmentsDir, subtitlesDir)
        if (!muxSuccess) {
            download.error = "Muxing failed"
            Log.e(TAG, "download: ❌ muxing failed for ${download.episodeName}")
            return false
        }

        // Copy the final .mkv from cache to SAF (episode dir)
        val cacheMkv = File(cacheDir, FINAL_VIDEO_NAME)
        val copySuccess = copyMkvToSaf(cacheMkv, download)
        if (!copySuccess) {
            download.error = "Failed to copy video to storage"
            Log.e(TAG, "download: ❌ ${download.error}")
            return false
        }

        // Clean up cache + manifest
        cleanupCache(download)
        manifestManager.delete(download)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName}")
        return true
    }

    override suspend fun pause(download: Download) {
        Log.d(TAG, "pause: → pausing ${download.episodeName}")
        paused = true
        delay(500)
        download.status = Download.State.PAUSED
        Log.d(TAG, "pause: ✓ ${download.episodeName} paused")
    }

    override suspend fun cancel(download: Download) {
        Log.d(TAG, "cancel: → cancelling ${download.episodeName}")
        cancelled = true
        delay(300)
        cleanupCache(download)
        download.status = Download.State.NOT_DOWNLOADED
        Log.d(TAG, "cancel: ✓ ${download.episodeName} cancelled")
    }

    override fun isCompleted(download: Download): Boolean {
        return provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName) != null
    }

    // ---- Segment download (to cache dir — real file paths) ----

    private data class SegmentResult(val success: Boolean, val sizeBytes: Long)

    private suspend fun downloadSegment(
        download: Download,
        video: Video,
        segment: DownloadManifest.SegmentState,
        manifest: DownloadManifest.Manifest,
        segmentFile: File,
    ): SegmentResult = withContext(Dispatchers.IO) {
        val startTimeSec = segment.index * SEGMENT_DURATION_SEC

        // Build FFmpeg command — output to real file path (NOT saf:)
        val headerOptions = buildHeaderOptions(video)
        val outputPath = segmentFile.absolutePath

        val cmd = StringBuilder().apply {
            if (video.videoUrl.startsWith("http")) {
                append("-headers '")
                append(headerOptions.replace("'", "\\'"))
                append("' ")
            }
            append("-ss ").append(startTimeSec).append(" ")
            append("-t ").append(SEGMENT_DURATION_SEC).append(" ")
            append("-i \"").append(video.videoUrl).append("\" ")
            append("-c copy ")
            append("-avoid_negative_ts make_zero ")
            append("-f mpegts ")
            append("\"").append(outputPath).append("\" -y")
        }.toString()

        Log.v(TAG, "downloadSegment: cmd for seg ${segment.index}: $cmd")

        val contentLength = try { getContentLength(video) } catch (_: Exception) { 0L }

        val statsCallback = StatisticsCallback { stats ->
            val estimatedSegSize = if (contentLength > 0) {
                contentLength / manifest.totalSegments
            } else 0
            if (estimatedSegSize > 0) {
                val segProgress = stats.time / (SEGMENT_DURATION_SEC * 1000.0)
                val segBytes = (estimatedSegSize * segProgress).toLong()
                val totalDownloaded = manifest.downloadedBytes + segBytes
                progressTracker.updateBytes(download, totalDownloaded)
            }
        }

        FFmpegKitConfig.enableStatisticsCallback(statsCallback)

        val session = FFmpegKit.execute(cmd)
        FFmpegKitConfig.enableStatisticsCallback(null)

        val returnCode = session.returnCode
        val segSize = segmentFile.length()

        if (ReturnCode.isSuccess(returnCode) && segSize > 0) {
            Log.v(TAG, "downloadSegment: ✓ seg ${segment.index} rc=0 size=${segSize}")
            SegmentResult(true, segSize)
        } else {
            val logs = session.allLogsAsString?.take(500) ?: "no logs"
            Log.w(TAG, "downloadSegment: ⚠ seg ${segment.index} rc=${returnCode.value} size=${segSize}")
            Log.w(TAG, "downloadSegment: logs: $logs")
            segmentFile.delete()
            SegmentResult(false, 0)
        }
    }

    // ---- Subtitle download (to cache dir — real file paths) ----

    private suspend fun downloadSubtitles(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
        subtitlesDir: File,
    ): DownloadManifest.Manifest = withContext(Dispatchers.IO) {
        if (video.subtitleTracks.isEmpty()) {
            Log.d(TAG, "downloadSubtitles: no subtitle tracks")
            return@withContext manifest
        }

        var updatedManifest = manifest
        video.subtitleTracks.forEachIndexed { i, track ->
            val subState = manifest.subtitles.getOrNull(i)
            if (subState?.downloaded == true) {
                // Check if file exists in cache
                val subFile = File(subtitlesDir, subState.fileName)
                if (subFile.exists() && subFile.length() > 0) {
                    Log.d(TAG, "downloadSubtitles: ✓ already downloaded: ${track.lang}")
                    return@forEachIndexed
                }
            }

            val subFileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt"
            val subFile = File(subtitlesDir, subFileName)
            val headerOptions = buildHeaderOptions(video)
            val cmd = StringBuilder().apply {
                append("-headers '")
                append(headerOptions.replace("'", "\\'"))
                append("' ")
                append("-i \"").append(track.url).append("\" ")
                append("-c copy ")
                append("\"").append(subFile.absolutePath).append("\" -y")
            }.toString()

            Log.d(TAG, "downloadSubtitles: downloading ${track.lang} → $subFileName")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode) && subFile.length() > 0) {
                val updatedSubs = updatedManifest.subtitles.toMutableList()
                if (i < updatedSubs.size) {
                    updatedSubs[i] = updatedSubs[i].copy(downloaded = true)
                }
                updatedManifest = updatedManifest.copy(subtitles = updatedSubs)
                Log.d(TAG, "downloadSubtitles: ✓ downloaded: ${track.lang}")
            } else {
                Log.w(TAG, "downloadSubtitles: ⚠ failed: ${track.lang} rc=${session.returnCode.value}")
            }
        }

        manifestManager.write(download, updatedManifest)
        updatedManifest
    }

    // ---- Muxing (all in cache dir — real file paths, NO saf: protocol) ----

    private suspend fun muxSegments(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
        cacheDir: File,
        segmentsDir: File,
        subtitlesDir: File,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "muxSegments: → concatenating ${manifest.totalSegments} segments")

        // Build concat list with REAL FILE PATHS (not saf: URIs)
        // This is the critical fix for C1 — the concat demuxer crashes with saf: URIs
        val concatFile = File(cacheDir, CONCAT_FILE)
        val concatBuilder = StringBuilder()
        manifest.segments.forEach { seg ->
            val segFile = File(segmentsDir, seg.fileName)
            if (!segFile.exists() || segFile.length() == 0L) {
                Log.e(TAG, "muxSegments: ❌ segment file missing or empty: ${seg.fileName}")
                return@withContext false
            }
            concatBuilder.append("file '").append(segFile.absolutePath).append("'\n")
        }
        concatFile.writeText(concatBuilder.toString())
        Log.v(TAG, "muxSegments: concat list written (${manifest.totalSegments} entries)")

        // Output file in cache dir (real file path)
        val outputFile = File(cacheDir, FINAL_VIDEO_NAME)

        // Build mux command — all real file paths, NO saf: protocol
        val cmd = StringBuilder().apply {
            append("-f concat -safe 0 -i \"").append(concatFile.absolutePath).append("\"")

            // Subtitle inputs (from cache dir — real file paths)
            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    val subFile = File(subtitlesDir, subState.fileName)
                    if (subFile.exists()) {
                        append(" -i \"").append(subFile.absolutePath).append("\"")
                    }
                }
            }

            // Mapping
            append(" -map 0:v -map 0:a?")
            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    append(" -map ").append(i + 1).append(":s?")
                }
            }

            // Codec: copy all streams
            append(" -c copy -f matroska")

            // Subtitle metadata
            video.subtitleTracks.forEachIndexed { i, track ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    append(" -metadata:s:s:").append(i).append(" \"title=").append(track.lang).append("\"")
                }
            }

            append(" \"").append(outputFile.absolutePath).append("\" -y")
        }.toString()

        Log.d(TAG, "muxSegments: mux command length=${cmd.length}")
        Log.v(TAG, "muxSegments: cmd: $cmd")

        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode)

        if (success && outputFile.length() > 0) {
            Log.d(TAG, "muxSegments: ✓ mux succeeded, output size=${formatBytes(outputFile.length())}")
        } else {
            Log.e(TAG, "muxSegments: ❌ mux failed rc=${session.returnCode.value}")
            Log.e(TAG, "muxSegments: logs: ${session.allLogsAsString?.take(1000)}")
            outputFile.delete()
        }

        success
    }

    /**
     * Copy the muxed .mkv from cache dir to SAF (the user-selected downloads folder).
     */
    private suspend fun copyMkvToSaf(cacheMkv: File, download: Download): Boolean = withContext(Dispatchers.IO) {
        if (!cacheMkv.exists() || cacheMkv.length() == 0L) {
            Log.e(TAG, "copyMkvToSaf: ❌ cache mkv doesn't exist or is empty")
            return@withContext false
        }

        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: run {
                Log.e(TAG, "copyMkvToSaf: ❌ could not get episode dir")
                return@withContext false
            }

        val safFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: run {
            Log.e(TAG, "copyMkvToSaf: ❌ could not create file in SAF")
            return@withContext false
        }

        try {
            cacheMkv.inputStream().use { input ->
                safFile.openOutputStream(false).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "copyMkvToSaf: ✓ copied ${formatBytes(cacheMkv.length())} to SAF")
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyMkvToSaf: ❌ copy failed: ${e.message}", e)
            false
        }
    }

    /**
     * Verify that "done" segments still have their cache files.
     * If a file is missing (cache cleared), mark the segment as "pending".
     */
    private suspend fun verifySegmentFiles(
        download: Download,
        manifest: DownloadManifest.Manifest,
        segmentsDir: File,
    ): DownloadManifest.Manifest {
        if (!segmentsDir.exists()) {
            // Cache dir doesn't exist — all segments need re-downloading
            Log.d(TAG, "verifySegmentFiles: cache dir doesn't exist, marking all segments as pending")
            val resetSegments = manifest.segments.map { seg ->
                if (seg.status == DownloadManifest.SegmentStatus.DONE) {
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING)
                } else seg
            }
            val resetManifest = manifest.copy(segments = resetSegments)
            manifestManager.write(download, resetManifest)
            return resetManifest
        }

        var changed = false
        val segments = manifest.segments.map { seg ->
            if (seg.status == DownloadManifest.SegmentStatus.DONE) {
                val file = File(segmentsDir, seg.fileName)
                if (!file.exists() || file.length() == 0L) {
                    Log.w(TAG, "verifySegmentFiles: ⚠ segment ${seg.index} marked done but file missing — resetting to pending")
                    changed = true
                    seg.copy(status = DownloadManifest.SegmentStatus.PENDING, sizeBytes = 0L)
                } else seg
            } else seg
        }

        return if (changed) {
            val updated = manifest.copy(segments = segments)
            manifestManager.write(download, updated)
            updated
        } else manifest
    }

    // ---- FFprobe / metadata ----

    private suspend fun getVideoDurationMs(video: Video): Long = withContext(Dispatchers.IO) {
        val headerOptions = buildHeaderOptions(video)
        val cmd = if (video.videoUrl.startsWith("http")) {
            "-headers '$headerOptions' -i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        } else {
            "-i \"${video.videoUrl}\" -show_entries format=duration -v quiet -of csv=\"p=0\""
        }

        Log.d(TAG, "getVideoDurationMs: probing with FFprobe")
        val session = FFprobeKit.execute(cmd)
        val output = session.output?.trim()

        if (ReturnCode.isSuccess(session.returnCode) && !output.isNullOrEmpty()) {
            val durationSec = output.toDoubleOrNull() ?: 0.0
            val durationMs = (durationSec * 1000).toLong()
            Log.d(TAG, "getVideoDurationMs: ✓ duration=${durationMs}ms")
            durationMs
        } else {
            Log.w(TAG, "getVideoDurationMs: ⚠ FFprobe returned no duration, output='$output'")
            24 * 60 * 1000L
        }
    }

    private fun getContentLength(video: Video): Long {
        return try { 0L } catch (e: Exception) { 0L }
    }

    // ---- Helpers ----

    private fun buildHeaderOptions(video: Video): String {
        return video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private fun getCacheDir(download: Download): File {
        return File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}").apply { mkdirs() }
    }

    private fun hasMinDiskSpace(): Boolean {
        return try {
            val stat = android.os.StatFs(context.filesDir.path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val hasSpace = freeBytes > MIN_DISK_SPACE_MB * 1024 * 1024
            Log.v(TAG, "hasMinDiskSpace: free=${formatBytes(freeBytes)}, min=${MIN_DISK_SPACE_MB}MB → $hasSpace")
            hasSpace
        } catch (e: Exception) {
            Log.w(TAG, "hasMinDiskSpace: ⚠ could not check: ${e.message}")
            true
        }
    }

    private fun cleanupCache(download: Download) {
        try {
            val cacheDir = File(context.cacheDir, "$CACHE_BASE_DIR/${download.id}")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                Log.d(TAG, "cleanupCache: ✓ deleted ${cacheDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupCache: ⚠ ${e.message}")
        }
    }

    fun resetFlags() {
        paused = false
        cancelled = false
        Log.d(TAG, "resetFlags: ✓ pause/cancel flags cleared")
    }
}
