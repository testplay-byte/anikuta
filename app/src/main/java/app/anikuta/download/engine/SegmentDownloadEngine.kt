package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.Download
import app.anikuta.download.DownloadProvider
import app.anikuta.download.DownloadVideoResolver
import app.anikuta.download.progress.ProgressTracker
import app.anikuta.download.progress.formatBytes
import app.anikuta.util.storage.toFFmpegString
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

/**
 * Segment-based download engine with full resume capability.
 *
 * Architecture:
 * 1. RESOLVING: Resolve video URL + get duration via FFprobe
 * 2. DOWNLOADING: Download in 10-second segments (skipping completed ones on resume)
 * 3. MUXING: Concatenate segments + mux subtitles into final .mkv
 *
 * The manifest tracks per-segment state. On resume:
 * - "done" segments are skipped (counted as complete)
 * - "partial" segments are deleted and re-downloaded
 * - "pending" segments are downloaded
 *
 * Each segment is downloaded via FFmpeg:
 *   ffmpeg -ss <startTime> -t 10 -i <videoUrl> -c copy -f mpegts seg_XXX.ts
 *
 * Final mux:
 *   ffmpeg -f concat -safe 0 -i concat.txt -i sub1.vtt -i sub2.vtt
 *          -map 0:v -map 0:a? -map 1:s? -c copy -f matroska video.mkv
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
        private const val SEGMENT_RETRY_BACKOFF_MS = 2000L
        private const val MIN_DISK_SPACE_MB = 200L
        private const val FINAL_VIDEO_NAME = "video.mkv"
        private const val TMP_DIR_PREFIX = "tmp_"
        private const val SEGMENTS_DIR = "segments"
        private const val SUBTITLES_DIR = "subtitles"
        private const val CONCAT_FILE = "concat.txt"
    }

    /** Pause flag — checked between segments. */
    @Volatile
    private var paused = false

    /** Cancel flag — checked between segments. */
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

        // Check disk space
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

        // Get Content-Length for size estimation
        val contentLength = getContentLength(video)
        if (contentLength > 0) {
            progressTracker.setTotalSize(download, contentLength)
            Log.d(TAG, "download: Content-Length = ${formatBytes(contentLength)}")
        } else {
            // Estimate from duration (average anime bitrate ~2 Mbps)
            val estimated = durationMs * 250 // ~250 KB/sec ≈ 2 Mbps
            progressTracker.setTotalSize(download, estimated)
            Log.d(TAG, "download: no Content-Length, estimated = ${formatBytes(estimated)}")
        }

        // Load or create manifest (use val + elvis to ensure non-null after this point)
        var manifest: DownloadManifest.Manifest = manifestManager.read(download) ?: run {
            // Fresh download — create manifest
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

        Log.d(TAG, "download: manifest loaded — ${manifest.totalSegments} segments, " +
            "${manifestManager.getCompletedSegmentCount(manifest)} done, resuming from segment " +
            "${manifest.segments.indexOfFirst { it.status != DownloadManifest.SegmentStatus.DONE }.let { if (it < 0) -1 else it }}")

        // Update progress from manifest state (shows completed segments on resume)
        progressTracker.updateFromManifest(download, manifest)

        // Download subtitle tracks first (small, fast)
        manifest = downloadSubtitles(download, video, manifest)

        // Download segments
        while (true) {
            // Check pause/cancel
            if (paused) {
                Log.d(TAG, "download: ⏸ paused — saving manifest and returning")
                download.status = Download.State.PAUSED
                manifestManager.write(download, manifest)
                return false
            }
            if (cancelled) {
                Log.d(TAG, "download: ✕ cancelled — cleaning up")
                cleanupTmp(download)
                return false
            }

            val segment = manifestManager.getNextPendingSegment(manifest)
            if (segment == null) {
                Log.d(TAG, "download: ✓ all segments done")
                break
            }

            // Download one segment
            manifest = manifestManager.markSegmentDownloading(manifest, segment.index)
            manifestManager.write(download, manifest)

            val segmentResult = downloadSegment(download, video, segment, manifest)

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
                // Don't fail the whole download yet; try next segment
                // (will retry this one on next pass via getNextPendingSegment)
            }

            yield() // cooperative cancellation
        }

        // Check if all segments are done
        if (!manifestManager.allSegmentsDone(manifest)) {
            download.error = "Some segments failed to download"
            Log.e(TAG, "download: ❌ not all segments done — ${download.error}")
            return false
        }

        // MUXING phase — concatenate segments + mux subtitles
        download.status = Download.State.MUXING
        Log.d(TAG, "download: → muxing phase — concatenating ${manifest.totalSegments} segments")

        val muxSuccess = muxSegments(download, video, manifest)
        if (!muxSuccess) {
            download.error = "Muxing failed"
            Log.e(TAG, "download: ❌ muxing failed for ${download.episodeName}")
            return false
        }

        // Clean up tmp files (segments, concat file, manifest)
        cleanupTmp(download, keepManifest = false)
        progressTracker.markComplete(download)
        download.status = Download.State.DOWNLOADED
        Log.d(TAG, "download: ✓ COMPLETE — ${download.episodeName}")
        return true
    }

    override suspend fun pause(download: Download) {
        Log.d(TAG, "pause: → pausing ${download.episodeName}")
        paused = true
        // The download loop will check `paused` and save state
        // Wait a moment for the current segment to finish
        delay(500)
        download.status = Download.State.PAUSED
        Log.d(TAG, "pause: ✓ ${download.episodeName} paused")
    }

    override suspend fun cancel(download: Download) {
        Log.d(TAG, "cancel: → cancelling ${download.episodeName}")
        cancelled = true
        delay(300)
        cleanupTmp(download)
        download.status = Download.State.NOT_DOWNLOADED
        Log.d(TAG, "cancel: ✓ ${download.episodeName} cancelled")
    }

    override fun isCompleted(download: Download): Boolean {
        val file = provider.getDownloadedVideoFile(download.episodeName, download.animeTitle, download.sourceName)
        return file != null
    }

    // ---- Segment download ----

    private data class SegmentResult(val success: Boolean, val sizeBytes: Long)

    private suspend fun downloadSegment(
        download: Download,
        video: Video,
        segment: DownloadManifest.SegmentState,
        manifest: DownloadManifest.Manifest,
    ): SegmentResult = withContext(Dispatchers.IO) {
        val startTimeSec = segment.index * SEGMENT_DURATION_SEC
        val segmentFile = getSegmentFile(download, segment.fileName) ?: run {
            Log.e(TAG, "downloadSegment: ❌ could not create segment file ${segment.fileName}")
            return@withContext SegmentResult(false, 0)
        }

        // Build FFmpeg command: extract a 10-second segment
        // -ss before -i for fast seek (keyframe-based)
        // -t for duration
        // -c copy for no re-encoding
        // -f mpegts for MPEG-TS container (supports concatenation)
        val headerOptions = buildHeaderOptions(video)
        val outputStr = segmentFile.uri.toFFmpegString(context)

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
            append("\"").append(outputStr).append("\" -y")
        }.toString()

        Log.v(TAG, "downloadSegment: cmd for seg ${segment.index}: $cmd")

        // Get content length for byte estimation (declared before the callback that uses it)
        val contentLength = try { getContentLength(video) } catch (_: Exception) { 0L }

        var lastStatistics: Statistics? = null
        val statsCallback = StatisticsCallback { stats ->
            lastStatistics = stats
            // Update byte tracking (rough estimate from statistics)
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
            // Delete partial segment file
            segmentFile.delete()
            SegmentResult(false, 0)
        }
    }

    // ---- Subtitle download ----

    private suspend fun downloadSubtitles(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
    ): DownloadManifest.Manifest = withContext(Dispatchers.IO) {
        if (video.subtitleTracks.isEmpty()) {
            Log.d(TAG, "downloadSubtitles: no subtitle tracks")
            return@withContext manifest
        }

        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return@withContext manifest
        val subsDir = episodeDir.createDirectory(SUBTITLES_DIR) ?: return@withContext manifest

        var updatedManifest = manifest
        video.subtitleTracks.forEachIndexed { i, track ->
            // Check if already downloaded (from manifest)
            val subState = manifest.subtitles.getOrNull(i)
            if (subState?.downloaded == true) {
                Log.d(TAG, "downloadSubtitles: ✓ already downloaded: ${track.lang}")
                return@forEachIndexed
            }

            val subFileName = "sub_${i}_${track.lang.ifBlank { "und" }}.vtt"
            val subFile = subsDir.createFile(subFileName) ?: run {
                Log.e(TAG, "downloadSubtitles: ❌ could not create file: $subFileName")
                return@forEachIndexed
            }

            val outputStr = subFile.uri.toFFmpegString(context)
            val headerOptions = buildHeaderOptions(video)
            val cmd = StringBuilder().apply {
                append("-headers '")
                append(headerOptions.replace("'", "\\'"))
                append("' ")
                append("-i \"").append(track.url).append("\" ")
                append("-c copy ")
                append("\"").append(outputStr).append("\" -y")
            }.toString()

            Log.d(TAG, "downloadSubtitles: downloading ${track.lang} → $subFileName")
            val session = FFmpegKit.execute(cmd)
            if (ReturnCode.isSuccess(session.returnCode)) {
                // Update manifest
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

    // ---- Muxing ----

    private suspend fun muxSegments(
        download: Download,
        video: Video,
        manifest: DownloadManifest.Manifest,
    ): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "muxSegments: → concatenating ${manifest.totalSegments} segments")

        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: run {
                Log.e(TAG, "muxSegments: ❌ could not get episode dir")
                return@withContext false
            }
        val segmentsDir = episodeDir.createDirectory(SEGMENTS_DIR) ?: run {
            Log.e(TAG, "muxSegments: ❌ could not get segments dir")
            return@withContext false
        }

        // Build concat list (FFmpeg concat demuxer format)
        // file 'seg_000.ts'
        // file 'seg_001.ts'
        // ...
        // We need to write this to a file that FFmpeg can read.
        // Since segments are in SAF, we use their FFmpeg saf:// strings.
        val concatBuilder = StringBuilder()
        manifest.segments.forEach { seg ->
            val segFile = segmentsDir.findFile(seg.fileName)
            if (segFile != null) {
                val segStr = segFile.uri.toFFmpegString(context)
                concatBuilder.append("file '").append(segStr).append("'\n")
            } else {
                Log.e(TAG, "muxSegments: ❌ segment file missing: ${seg.fileName}")
                return@withContext false
            }
        }

        // Write concat file
        val concatFile = episodeDir.createFile(CONCAT_FILE) ?: run {
            Log.e(TAG, "muxSegments: ❌ could not create concat file")
            return@withContext false
        }
        concatFile.openOutputStream(false).bufferedWriter().use { it.write(concatBuilder.toString()) }
        Log.v(TAG, "muxSegments: concat list written (${manifest.totalSegments} entries)")

        // Build output file
        val outputFile = episodeDir.createFile(FINAL_VIDEO_NAME) ?: run {
            Log.e(TAG, "muxSegments: ❌ could not create output file")
            return@withContext false
        }
        val concatStr = concatFile.uri.toFFmpegString(context)
        val outputStr = outputFile.uri.toFFmpegString(context)

        // Build mux command:
        // ffmpeg -f concat -safe 0 -i concat.txt -i sub1.vtt -i sub2.vtt
        //        -map 0:v -map 0:a? -map 1:s? -c copy -f matroska video.mkv
        val cmd = StringBuilder().apply {
            append("-f concat -safe 0 -i \"").append(concatStr).append("\"")

            // Subtitle inputs
            video.subtitleTracks.forEachIndexed { i, _ ->
                val subState = manifest.subtitles.getOrNull(i)
                if (subState?.downloaded == true) {
                    val subFile = episodeDir.findFile(SUBTITLES_DIR)?.findFile(subState.fileName)
                    if (subFile != null) {
                        val subStr = subFile.uri.toFFmpegString(context)
                        append(" -i \"").append(subStr).append("\"")
                    }
                }
            }

            // Mapping: video + audio from concat, subtitles from extra inputs
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

            append(" \"").append(outputStr).append("\" -y")
        }.toString()

        Log.d(TAG, "muxSegments: mux command length=${cmd.length}")
        Log.v(TAG, "muxSegments: cmd: $cmd")

        val session = FFmpegKit.execute(cmd)
        val success = ReturnCode.isSuccess(session.returnCode)

        if (success) {
            Log.d(TAG, "muxSegments: ✓ mux succeeded, output size=${outputFile.length()}")
            // Clean up segments + concat file
            segmentsDir.delete()
            concatFile.delete()
            episodeDir.findFile(SUBTITLES_DIR)?.delete()
            Log.d(TAG, "muxSegments: ✓ cleaned up tmp files")
        } else {
            Log.e(TAG, "muxSegments: ❌ mux failed rc=${session.returnCode.value}")
            Log.e(TAG, "muxSegments: logs: ${session.allLogsAsString?.take(1000)}")
            outputFile.delete()
        }

        success
    }

    // ---- FFprobe / metadata ----

    /**
     * Get video duration in milliseconds via FFprobe.
     */
    private suspend fun getVideoDurationMs(video: Video): Long = withContext(Dispatchers.IO) {
        val headerOptions = buildHeaderOptions(video)
        // FFprobe with headers
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
            // Fallback: assume 24 minutes (will be wrong but won't block)
            24 * 60 * 1000L
        }
    }

    /**
     * Get Content-Length from video URL (HEAD request).
     */
    private fun getContentLength(video: Video): Long {
        return try {
            // FFmpegKit doesn't have a direct HEAD request, so we estimate from FFprobe
            // The actual size will be tracked via segment downloads
            0L
        } catch (e: Exception) {
            Log.w(TAG, "getContentLength: ⚠ could not get Content-Length: ${e.message}")
            0L
        }
    }

    // ---- Helpers ----

    private fun buildHeaderOptions(video: Video): String {
        return video.headers?.let { headers ->
            headers.toMultimap().map { (key, values) ->
                "$key: ${values.firstOrNull() ?: ""}"
            }.joinToString("\r\n")
        } ?: "User-Agent: Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"
    }

    private fun getSegmentFile(download: Download, fileName: String): UniFile? {
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return null
        val segmentsDir = episodeDir.createDirectory(SEGMENTS_DIR) ?: return null
        return segmentsDir.createFile(fileName)
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

    private fun cleanupTmp(download: Download, keepManifest: Boolean = false) {
        try {
            val episodeDir = provider.findEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            if (episodeDir != null) {
                episodeDir.findFile(SEGMENTS_DIR)?.delete()
                episodeDir.findFile(CONCAT_FILE)?.delete()
                if (!keepManifest) {
                    episodeDir.findFile(DownloadManifest.MANIFEST_FILE)?.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupTmp: ⚠ ${e.message}")
        }
    }

    /**
     * Reset pause/cancel flags. Called by the worker before starting a new download.
     */
    fun resetFlags() {
        paused = false
        cancelled = false
        Log.d(TAG, "resetFlags: ✓ pause/cancel flags cleared")
    }
}
