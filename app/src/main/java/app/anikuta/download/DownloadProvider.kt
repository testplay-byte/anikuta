package app.anikuta.download

import android.content.Context
import android.util.Log
import app.anikuta.storage.StorageManager
import app.anikuta.util.storage.DiskUtil
import com.hippo.unifile.UniFile

/**
 * Provides the directory paths for downloaded episodes.
 *
 * Path scheme: `<downloads>/<sourceName>/<animeTitle>/<episodeName>/`
 *
 * Uses [DiskUtil.buildValidFilename] to sanitize directory names for
 * filesystem compatibility. All directories are created on demand via
 * [UniFile.createDirectory] (idempotent — returns existing if present).
 *
 * @param context the application context
 * @param storageManager provides the base downloads directory (SAF)
 */
class DownloadProvider(
    private val context: Context,
    private val storageManager: StorageManager,
) {
    private val downloadsDir: UniFile?
        get() = storageManager.getDownloadsDirectory()

    /**
     * Get (or create) the source directory.
     * @param sourceName the display name of the source
     */
    fun getSourceDir(sourceName: String): UniFile? {
        return downloadsDir?.createDirectory(DiskUtil.buildValidFilename(sourceName))
    }

    /**
     * Get (or create) the anime directory inside a source directory.
     * @param animeTitle the anime title
     * @param sourceName the source display name
     */
    fun getAnimeDir(animeTitle: String, sourceName: String): UniFile? {
        return getSourceDir(sourceName)
            ?.createDirectory(DiskUtil.buildValidFilename(animeTitle))
    }

    /**
     * Get (or create) the episode directory inside an anime directory.
     * @param episodeName the episode name (e.g. "Episode 1")
     * @param animeTitle the anime title
     * @param sourceName the source display name
     */
    fun getEpisodeDir(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        return getAnimeDir(animeTitle, sourceName)
            ?.createDirectory(DiskUtil.buildValidFilename(episodeName.ifBlank { "Episode" }))
    }

    /**
     * Find an existing episode directory (without creating it).
     * @param episodeName the episode name
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return the UniFile if it exists, null otherwise
     */
    fun findEpisodeDir(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        val animeDir = getAnimeDir(animeTitle, sourceName) ?: return null
        val dirName = DiskUtil.buildValidFilename(episodeName.ifBlank { "Episode" })
        return animeDir.findFile(dirName)
    }

    /**
     * Check if an episode is downloaded.
     * @param episodeName the episode name
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return true if the episode directory exists and contains a .mkv or .mp4 file
     */
    fun isEpisodeDownloaded(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return false
        return episodeDir.listFiles()?.any { file ->
            val name = file.name?.lowercase() ?: ""
            name.endsWith(".mkv") || name.endsWith(".mp4")
        } ?: false
    }

    /**
     * List all downloaded episode names for a given anime.
     *
     * Scans the anime directory and returns the names of all episode subdirectories
     * that contain a .mkv or .mp4 file. Used by the detail page to show green
     * checkmarks for episodes that are on disk but not in the download queue
     * (e.g. after removing from the downloads page, or after app reinstall).
     *
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return set of episode names that are downloaded on disk
     */
    fun listDownloadedEpisodes(animeTitle: String, sourceName: String): Set<String> {
        val animeDir = getAnimeDir(animeTitle, sourceName) ?: return emptySet()
        val result = mutableSetOf<String>()
        animeDir.listFiles()?.forEach { episodeDir ->
            if (episodeDir.isDirectory) {
                val hasVideo = episodeDir.listFiles()?.any { file ->
                    val name = file.name?.lowercase() ?: ""
                    name.endsWith(".mkv") || name.endsWith(".mp4")
                } ?: false
                if (hasVideo) {
                    episodeDir.name?.let { result.add(it) }
                }
            }
        }
        return result
    }

    /**
     * Get the downloaded video file for an episode.
     * @param episodeName the episode name
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return the UniFile of the video, or null if not found
     */
    fun getDownloadedVideoFile(episodeName: String, animeTitle: String, sourceName: String): UniFile? {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return null
        return episodeDir.listFiles()?.firstOrNull { file ->
            val name = file.name?.lowercase() ?: ""
            name.endsWith(".mkv") || name.endsWith(".mp4")
        }
    }

    /**
     * Delete a downloaded episode.
     * @param episodeName the episode name
     * @param animeTitle the anime title
     * @param sourceName the source display name
     * @return true if deleted (or didn't exist), false on error
     */
    fun deleteEpisode(episodeName: String, animeTitle: String, sourceName: String): Boolean {
        val episodeDir = findEpisodeDir(episodeName, animeTitle, sourceName) ?: return true
        return try {
            episodeDir.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete episode dir", e)
            false
        }
    }

    companion object {
        private const val TAG = "DownloadProvider"
    }
}
