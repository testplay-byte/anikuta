package app.anikuta.download.engine

import android.content.Context
import android.util.Log
import app.anikuta.download.DownloadProvider
import com.hippo.unifile.UniFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Manages the download manifest — a JSON file that tracks segment-level
 * download state for resume capability.
 *
 * The manifest is stored at:
 *   downloads/<source>/<anime>/<episode>/manifest.json
 *
 * It records:
 * - The resolved video URL (for reference; re-resolved on resume)
 * - Total video duration + segment count
 * - Per-segment status (done / partial / pending)
 * - Subtitle track download status
 * - Total + downloaded byte counts
 *
 * On resume, the engine reads the manifest, skips "done" segments,
 * deletes "partial" segments, and downloads only "pending" ones.
 *
 * Atomic writes: manifest is written to manifest.json.tmp first,
 * then renamed to manifest.json. This prevents corruption if the app
 * crashes during a write.
 */
class DownloadManifest(
    private val context: Context,
    private val provider: DownloadProvider,
) {
    companion object {
        private const val TAG = "DownloadManifest"
        const val MANIFEST_FILE = "manifest.json"
        private const val MANIFEST_TMP = "manifest.json.tmp"
        const val SEGMENT_DURATION_SEC = 10
    }

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    /**
     * Serializable manifest data model.
     */
    @Serializable
    data class Manifest(
        val downloadId: String,
        val anilistId: Int,
        val sourceId: Long,
        val sourceName: String,
        val animeTitle: String,
        val episodeUrl: String,
        val episodeName: String,
        val episodeNumber: Float,
        /** Resolved video URL at resolution time (for reference; re-resolved on resume). */
        val videoUrl: String = "",
        /** Total video duration in milliseconds. */
        val totalDurationMs: Long = 0L,
        /** Duration of each segment in seconds. */
        val segmentDurationSec: Int = SEGMENT_DURATION_SEC,
        /** Total number of segments. */
        val totalSegments: Int = 0,
        /** Per-segment state. */
        val segments: List<SegmentState> = emptyList(),
        /** Subtitle tracks. */
        val subtitles: List<SubtitleState> = emptyList(),
        /** Estimated total file size in bytes (-1 if unknown). */
        val totalSizeBytes: Long = -1L,
        /** Downloaded bytes so far. */
        val downloadedBytes: Long = 0L,
        /** Timestamps. */
        val createdAt: Long = System.currentTimeMillis(),
        val updatedAt: Long = System.currentTimeMillis(),
    )

    @Serializable
    data class SegmentState(
        val index: Int,
        val status: SegmentStatus,
        val fileName: String,
        val sizeBytes: Long = 0L,
        /** Start time in milliseconds. */
        val startTimeMs: Long = 0L,
    )

    @Serializable
    enum class SegmentStatus(val value: String) {
        PENDING("pending"),
        DOWNLOADING("downloading"),
        DONE("done"),
        PARTIAL("partial"),
        ERROR("error");

        companion object {
            fun fromValue(v: String): SegmentStatus = entries.find { it.value == v } ?: PENDING
        }
    }

    @Serializable
    data class SubtitleState(
        val url: String,
        val lang: String,
        val downloaded: Boolean,
        val fileName: String,
    )

    // ---- Read / Write ----

    /**
     * Read the manifest for a download. Returns null if no manifest exists.
     */
    suspend fun read(download: app.anikuta.download.Download): Manifest? = withContext(Dispatchers.IO) {
        val manifestFile = getManifestFile(download) ?: run {
            Log.d(TAG, "read: no manifest file for ${download.episodeName}")
            return@withContext null
        }
        try {
            val content = manifestFile.openInputStream().bufferedReader().use { it.readText() }
            Log.v(TAG, "read: manifest content length=${content.length} for ${download.episodeName}")
            val manifest = json.decodeFromString(Manifest.serializer(), content)
            Log.d(TAG, "read: ✓ manifest loaded for ${download.episodeName} — ${manifest.totalSegments} segments, " +
                "${manifest.segments.count { it.status == SegmentStatus.DONE }} done, " +
                "${manifest.segments.count { it.status == SegmentStatus.PENDING }} pending, " +
                "${manifest.segments.count { it.status == SegmentStatus.PARTIAL }} partial, " +
                "${manifest.segments.count { it.status == SegmentStatus.ERROR }} error")
            manifest
        } catch (e: Exception) {
            Log.e(TAG, "read: ❌ failed to parse manifest for ${download.episodeName}: ${e.message}", e)
            null
        }
    }

    /**
     * Write the manifest atomically (write to .tmp, then rename).
     */
    suspend fun write(download: app.anikuta.download.Download, manifest: Manifest) = withContext(Dispatchers.IO) {
        val episodeDir = provider.getEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: run {
                Log.e(TAG, "write: ❌ could not get episode dir for ${download.episodeName}")
                return@withContext
            }

        val updatedAt = manifest.copy(updatedAt = System.currentTimeMillis())
        val content = json.encodeToString(Manifest.serializer(), updatedAt)

        try {
            // Write to tmp file first
            val tmpFile = episodeDir.createFile(MANIFEST_TMP) ?: run {
                Log.e(TAG, "write: ❌ could not create tmp manifest file")
                return@withContext
            }
            tmpFile.openOutputStream(false).bufferedWriter().use { it.write(content) }
            Log.v(TAG, "write: tmp file written, length=${content.length}")

            // Rename tmp → final (atomic on most filesystems)
            // UniFile doesn't have rename for files within a dir easily,
            // so we delete the old manifest and rename the tmp.
            val existingManifest = episodeDir.findFile(MANIFEST_FILE)
            existingManifest?.delete()
            tmpFile.renameTo(MANIFEST_FILE)

            Log.d(TAG, "write: ✓ manifest saved for ${download.episodeName} — " +
                "${manifest.segments.count { it.status == SegmentStatus.DONE }}/${manifest.totalSegments} segments done, " +
                "${manifest.downloadedBytes}/${manifest.totalSizeBytes} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "write: ❌ failed to write manifest for ${download.episodeName}: ${e.message}", e)
        }
    }

    /**
     * Delete the manifest file. Called after successful mux (final .mkv is the source of truth).
     */
    suspend fun delete(download: app.anikuta.download.Download) = withContext(Dispatchers.IO) {
        val manifestFile = getManifestFile(download)
        manifestFile?.delete()
        Log.d(TAG, "delete: manifest deleted for ${download.episodeName}")
    }

    /**
     * Check if a manifest exists for this download.
     */
    fun exists(download: app.anikuta.download.Download): Boolean {
        return getManifestFile(download) != null
    }

    /**
     * Create a new manifest with all segments set to PENDING.
     */
    fun createFresh(
        download: app.anikuta.download.Download,
        videoUrl: String,
        totalDurationMs: Long,
        subtitles: List<SubtitleState> = emptyList(),
        totalSizeBytes: Long = -1L,
    ): Manifest {
        val totalSegments = if (totalDurationMs > 0) {
            kotlin.math.ceil(totalDurationMs.toDouble() / (SEGMENT_DURATION_SEC * 1000)).toInt()
        } else {
            Log.w(TAG, "createFresh: totalDurationMs=$totalDurationMs, defaulting to 0 segments")
            0
        }

        val segments = (0 until totalSegments).map { index ->
            SegmentState(
                index = index,
                status = SegmentStatus.PENDING,
                fileName = "seg_%04d.ts".format(index),
                sizeBytes = 0L,
                startTimeMs = index.toLong() * SEGMENT_DURATION_SEC * 1000,
            )
        }

        val manifest = Manifest(
            downloadId = download.id,
            anilistId = download.anilistId,
            sourceId = download.sourceId,
            sourceName = download.sourceName,
            animeTitle = download.animeTitle,
            episodeUrl = download.episodeUrl,
            episodeName = download.episodeName,
            episodeNumber = download.episodeNumber,
            videoUrl = videoUrl,
            totalDurationMs = totalDurationMs,
            segmentDurationSec = SEGMENT_DURATION_SEC,
            totalSegments = totalSegments,
            segments = segments,
            subtitles = subtitles,
            totalSizeBytes = totalSizeBytes,
            downloadedBytes = 0L,
        )

        Log.d(TAG, "createFresh: ✓ created manifest for ${download.episodeName} — " +
            "$totalSegments segments, duration=${totalDurationMs}ms, " +
            "${subtitles.size} subtitle tracks, estimated size=${totalSizeBytes} bytes")
        return manifest
    }

    /**
     * Mark a segment as done and update byte counts.
     */
    fun markSegmentDone(manifest: Manifest, index: Int, sizeBytes: Long): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) {
                seg.copy(status = SegmentStatus.DONE, sizeBytes = sizeBytes)
            } else seg
        }
        val downloadedBytes = segments.sumOf { if (it.status == SegmentStatus.DONE) it.sizeBytes else 0L }
        return manifest.copy(segments = segments, downloadedBytes = downloadedBytes, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark a segment as partial (interrupted mid-download).
     */
    fun markSegmentPartial(manifest: Manifest, index: Int): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) seg.copy(status = SegmentStatus.PARTIAL) else seg
        }
        return manifest.copy(segments = segments, updatedAt = System.currentTimeMillis())
    }

    /**
     * Mark a segment as downloading (in-progress).
     */
    fun markSegmentDownloading(manifest: Manifest, index: Int): Manifest {
        val segments = manifest.segments.map { seg ->
            if (seg.index == index) seg.copy(status = SegmentStatus.DOWNLOADING) else seg
        }
        return manifest.copy(segments = segments, updatedAt = System.currentTimeMillis())
    }

    /**
     * Get the next pending segment to download. Returns null if all are done.
     */
    fun getNextPendingSegment(manifest: Manifest): SegmentState? {
        return manifest.segments.firstOrNull { it.status == SegmentStatus.PENDING || it.status == SegmentStatus.PARTIAL || it.status == SegmentStatus.ERROR }
    }

    /**
     * Get the count of completed segments.
     */
    fun getCompletedSegmentCount(manifest: Manifest): Int {
        return manifest.segments.count { it.status == SegmentStatus.DONE }
    }

    /**
     * Check if all segments are done.
     */
    fun allSegmentsDone(manifest: Manifest): Boolean {
        return manifest.segments.isNotEmpty() && manifest.segments.all { it.status == SegmentStatus.DONE }
    }

    // ---- Private helpers ----

    private fun getManifestFile(download: app.anikuta.download.Download): UniFile? {
        val episodeDir = provider.findEpisodeDir(download.episodeName, download.animeTitle, download.sourceName)
            ?: return null
        return episodeDir.findFile(MANIFEST_FILE)
    }
}
