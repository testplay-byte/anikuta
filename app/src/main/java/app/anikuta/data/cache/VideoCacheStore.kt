package app.anikuta.data.cache

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persistent video resolution cache — saves resolved video lists to disk as JSON.
 *
 * Survives app restarts. When the user reopens an anime after killing the app,
 * resolved videos are loaded instantly from disk, then a background re-resolve
 * checks for updates.
 *
 * Cache files are stored in: app/files/video_cache/{anilistId}_{episodeUrlHash}.json
 *
 * Each file contains:
 *  - anilistId
 *  - episodeUrl (for identification)
 *  - timestamp (when the cache was written)
 *  - videos (serialized Video list)
 */
class VideoCacheStore(
    private val context: Context,
) {
    companion object {
        private const val TAG = "VideoCacheStore"
        private const val CACHE_DIR = "video_cache"
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        /** Cache TTL — 24 hours. After this, the cache is considered stale. */
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    private val cacheDir by lazy {
        File(context.filesDir, CACHE_DIR).apply { mkdirs() }
    }

    @Serializable
    data class CachedVideos(
        val anilistId: Int,
        val episodeUrl: String,
        val timestamp: Long,
        val videosJson: String,
    )

    /**
     * Save resolved videos to disk cache.
     */
    suspend fun save(anilistId: Int, episodeUrl: String, videos: List<Video>) {
        withContext(Dispatchers.IO) {
            try {
                val videosJson = videos.serialize()
                val cached = CachedVideos(
                    anilistId = anilistId,
                    episodeUrl = episodeUrl,
                    timestamp = System.currentTimeMillis(),
                    videosJson = videosJson,
                )
                val file = File(cacheDir, "${anilistId}_${episodeUrl.hashCode()}.json")
                file.writeText(json.encodeToString(CachedVideos.serializer(), cached))
                Log.d(TAG, "Saved ${videos.size} videos to disk cache for anilistId=$anilistId episode=${episodeUrl.take(40)}...")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save video cache", e)
            }
        }
    }

    /**
     * Load videos from disk cache.
     * Returns null if cache doesn't exist, is corrupted, or is stale.
     */
    suspend fun load(anilistId: Int, episodeUrl: String): List<Video>? {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(cacheDir, "${anilistId}_${episodeUrl.hashCode()}.json")
                if (!file.exists()) return@withContext null

                val cached = json.decodeFromString(CachedVideos.serializer(), file.readText())
                val age = System.currentTimeMillis() - cached.timestamp
                if (age > CACHE_TTL_MS) {
                    Log.d(TAG, "Video cache stale (age=${age / 1000}s) for anilistId=$anilistId")
                    return@withContext null
                }

                val videos = cached.videosJson.toVideoList()
                Log.d(TAG, "Loaded ${videos.size} videos from disk cache for anilistId=$anilistId (age=${age / 1000}s)")
                videos
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load video cache", e)
                null
            }
        }
    }

    /**
     * Check if a cache entry exists and is fresh.
     */
    fun hasFreshCache(anilistId: Int, episodeUrl: String): Boolean {
        val file = File(cacheDir, "${anilistId}_${episodeUrl.hashCode()}.json")
        if (!file.exists()) return false
        return try {
            val cached = json.decodeFromString(CachedVideos.serializer(), file.readText())
            val age = System.currentTimeMillis() - cached.timestamp
            age <= CACHE_TTL_MS
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear cache for a specific anime.
     */
    fun clear(anilistId: Int) {
        cacheDir.listFiles()?.filter { it.name.startsWith("${anilistId}_") }?.forEach { it.delete() }
    }

    /**
     * Clear all cached videos.
     */
    fun clearAll() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Serialize Video list to JSON string using SerializableVideo.
     */
    private fun List<Video>.serialize(): String {
        val serializableList = this.map { vid ->
            eu.kanade.tachiyomi.animesource.model.SerializableVideo(
                videoUrl = vid.videoUrl,
                videoTitle = vid.videoTitle,
                resolution = vid.resolution,
                bitrate = vid.bitrate,
                headers = vid.headers?.toList(),
                preferred = vid.preferred,
                subtitleTracks = vid.subtitleTracks,
                audioTracks = vid.audioTracks,
                timestamps = vid.timestamps,
                mpvArgs = vid.mpvArgs,
                ffmpegStreamArgs = vid.ffmpegStreamArgs,
                ffmpegVideoArgs = vid.ffmpegVideoArgs,
                internalData = vid.internalData,
                initialized = vid.initialized,
            )
        }
        return json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(
                eu.kanade.tachiyomi.animesource.model.SerializableVideo.serializer(),
            ),
            serializableList,
        )
    }

    /**
     * Deserialize JSON string to Video list using SerializableVideo.
     */
    private fun String.toVideoList(): List<Video> {
        val serializableList = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(
                eu.kanade.tachiyomi.animesource.model.SerializableVideo.serializer(),
            ),
            this,
        )
        return serializableList.map { sVid ->
            Video(
                videoUrl = sVid.videoUrl,
                videoTitle = sVid.videoTitle,
                resolution = sVid.resolution,
                bitrate = sVid.bitrate,
                headers = sVid.headers?.let { 
                    okhttp3.Headers.headersOf(*it.flatMap { listOf(it.first, it.second) }.toTypedArray())
                },
                preferred = sVid.preferred,
                subtitleTracks = sVid.subtitleTracks,
                audioTracks = sVid.audioTracks,
                timestamps = sVid.timestamps,
                mpvArgs = sVid.mpvArgs,
                ffmpegStreamArgs = sVid.ffmpegStreamArgs,
                ffmpegVideoArgs = sVid.ffmpegVideoArgs,
                internalData = sVid.internalData,
                initialized = sVid.initialized,
            )
        }
    }
}
